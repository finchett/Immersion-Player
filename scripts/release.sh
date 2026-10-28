#!/bin/bash
# release.sh [--try] <release notes.md>
#
# Builds both apps at the version in gradle.properties (or $APP_VERSION), writes the update feed
# (update.json) and signs it, and drafts the GitHub release with all four files. Publishing the
# draft is what sends the update: every installed copy finds it within a few hours.
#
# --try builds the same files into dist/try-<version> with the feed pointing at
# http://localhost:8765 instead, and creates nothing on GitHub, to try the update on this Mac
# from the version installed in /Applications first.
#
# Needs the release keys in ~/.keystores: immersion-player.properties (Android, see
# app/build.gradle.kts) and immersion-update.pem (the feed; its public half is in core's Updates).
set -euo pipefail
cd "$(dirname "$0")/.."

try=0
if [ "${1:-}" = "--try" ]; then try=1; shift; fi
notes="${1:?usage: release.sh [--try] <release notes.md>}"
version="${APP_VERSION:-$(sed -n 's/^appVersion=//p' gradle.properties)}"
tag="v$version"
repo="finchett/Immersion-Player"
feed_key="$HOME/.keystores/immersion-update.pem"
android_key="$HOME/.keystores/immersion-player.properties"
# the certificate every release APK has been signed with (0.1 onwards); Android won't update across another
android_signer="6af52571097c89fc700401a99e2d8f7a0a9ec39393802b0d928f7ff9cc0db762"
apksigner="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner | tail -1)"

die() { echo "release: $*" >&2; exit 1; }
[ -f "$notes" ] || die "no release notes at $notes"
[ -f "$feed_key" ] || die "no feed signing key at $feed_key"
[ -f "$android_key" ] || die "no Android signing key at $android_key"
if [ "$try" = 0 ]; then
    git diff --quiet HEAD || die "uncommitted changes; a release is built from a commit"
    [ -n "$(git branch -r --contains HEAD)" ] || die "$(git rev-parse --short HEAD) isn't on GitHub yet"
    ! gh release view "$tag" --repo "$repo" >/dev/null 2>&1 || die "$tag already exists"
fi

./gradlew -PappVersion="$version" -PimmersionKeystore="$android_key" :app:assembleRelease :desktop:packageMacRelease

if [ "$try" = 1 ]; then out="dist/try-$version"; base="http://localhost:8765"; else out="dist/$tag"; base="https://github.com/$repo/releases/download/$tag"; fi
rm -rf "$out"
mkdir -p "$out"
apk="$out/immersion-player-$version-arm64.apk"
dmg="$out/Immersion-Player-$version-macos-arm64.dmg"
cp app/build/outputs/apk/release/app-release.apk "$apk"
cp "desktop/build/release/Immersion-Player-$version-macos-arm64.dmg" "$dmg"
"$apksigner" verify --print-certs "$apk" | grep -q "SHA-256 digest: $android_signer" || die "$apk isn't signed with the release key"

# the feed: the version, each platform's file with its size and SHA-256, and the notes the apps show as What's new
python3 - "$version" "$repo" "$tag" "$base" "$notes" "$dmg" "$apk" > "$out/update.json" <<'PY'
import hashlib, json, os, sys
version, repo, tag, base, notes, dmg, apk = sys.argv[1:]
def asset(path):
    name = os.path.basename(path)
    with open(path, "rb") as f:
        digest = hashlib.file_digest(f, "sha256").hexdigest()
    return {"name": name, "url": f"{base}/{name}", "size": os.path.getsize(path), "sha256": digest}
print(json.dumps({
    "version": version,
    "page": f"https://github.com/{repo}/releases/tag/{tag}",
    "notes": open(notes, encoding="utf-8").read(),
    "assets": {"macos-arm64": asset(dmg), "android-arm64": asset(apk)},
}, indent=2, ensure_ascii=False))
PY
openssl dgst -sha256 -sign "$feed_key" -out "$out/update.json.sig" "$out/update.json"

if [ "$try" = 1 ]; then
    cat <<EOF

Built $version into $out. To update the installed app to it:
  python3 -m http.server 8765 --directory $out
  JAVA_TOOL_OPTIONS=-Dimmersion.updateFeed=$base/update.json "/Applications/Immersion Player.app/Contents/MacOS/Immersion Player"
It downloads in the background; "What's new in $version" appears in the library header once it's ready.
EOF
    exit 0
fi

gh release create "$tag" --repo "$repo" --draft --target "$(git rev-parse HEAD)" \
    --title "Immersion Player $version" --notes-file "$notes" \
    "$apk" "$dmg" "$out/update.json" "$out/update.json.sig"
cat <<EOF

Drafted $tag. Check it on GitHub, then publish it to send the update:
  gh release edit $tag --repo $repo --draft=false
EOF
