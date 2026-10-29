#!/bin/bash
# release.sh [--try] <release notes.md>
#
# Builds the Mac and Android apps at the version in gradle.properties (or $APP_VERSION), takes the
# Windows and Linux installers from the Desktop builds workflow's run for this commit (waiting for
# it if it's still going), writes the update feed (update.json) and signs it, and drafts the GitHub
# release with them all. Publishing the draft is what sends the update: every installed copy finds
# it within a few hours.
#
# --try builds the Mac and Android files into dist/try-<version> with the feed pointing at
# http://localhost:8765 instead, and creates nothing on GitHub, to try the update on this Mac
# from the version installed in /Applications first.
#
# Needs the release keys in ~/.keystores: mizune.properties (Android, see
# app/build.gradle.kts) and mizune-update.pem (the feed; its public half is in core's Updates).
set -euo pipefail
cd "$(dirname "$0")/.."

try=0
if [ "${1:-}" = "--try" ]; then try=1; shift; fi
notes="${1:?usage: release.sh [--try] <release notes.md>}"
version="${APP_VERSION:-$(sed -n 's/^appVersion=//p' gradle.properties)}"
tag="v$version"
repo="finchett/Mizune"
feed_key="$HOME/.keystores/mizune-update.pem"
android_key="$HOME/.keystores/mizune.properties"
# the certificate every release APK has been signed with (1.0 onwards); Android won't update across another
android_signer="6130587b879dc47a247cdf629e62b5408d761965cf71f9c01a0a56b4428c5345"
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

if [ "$try" = 1 ]; then out="dist/try-$version"; base="http://localhost:8765"; else out="dist/$tag"; base="https://github.com/$repo/releases/download/$tag"; fi
rm -rf "$out"
mkdir -p "$out"

# from CI, built from this same commit: Windows and Linux, and the Mac's libmpv without GPL code
msi="$out/Mizune-$version-windows-x64.msi"
deb="$out/Mizune-$version-linux-x64.deb"
appimage="$out/Mizune-$version-linux-x64.AppImage"
if [ "$try" = 0 ]; then
    commit="$(git rev-parse HEAD)"
    run="$(gh run list --repo "$repo" --workflow desktop.yml --commit "$commit" --event push --limit 1 --json databaseId -q '.[0].databaseId')"
    [ -n "$run" ] || die "no Desktop builds run for $commit; it starts when the commit reaches main"
    echo "Waiting for the CI builds (run $run)…"
    gh run watch "$run" --repo "$repo" --exit-status > /dev/null || die "the CI builds failed: gh run view $run --repo $repo"
    gh run download "$run" --repo "$repo" -n windows-x64 -D "$out"
    gh run download "$run" --repo "$repo" -n linux-x64 -D "$out"
    gh run download "$run" --repo "$repo" -n linux-x64-appimage -D "$out"
    [ -f "$msi" ] && [ -f "$deb" ] && [ -f "$appimage" ] || die "the builds didn't produce $msi, $deb and $appimage (is appVersion $version on main?)"
    gh run download "$run" --repo "$repo" -n macos-arm64-libmpv -D "$out/libmpv"
    mkdir -p "$out/libmpv/bundle"
    tar -xzf "$out/libmpv/macos-arm64-libmpv.tar.gz" -C "$out/libmpv/bundle"
    # the Mac app bundles this in place of Homebrew's GPL libmpv (bundle-libmpv-macos.sh)
    export LIBMPV_BUNDLE="$PWD/$out/libmpv/bundle"
else
    echo "--try: the Mac app bundles Homebrew's libmpv, a GPL build; releases take CI's LGPL one"
fi

./gradlew -PappVersion="$version" -PmizuneKeystore="$android_key" :app:assembleRelease :desktop:packageMacRelease
rm -rf "$out/libmpv"

apk="$out/mizune-$version-arm64.apk"
dmg="$out/Mizune-$version-macos-arm64.dmg"
cp app/build/outputs/apk/release/app-release.apk "$apk"
cp "desktop/build/release/Mizune-$version-macos-arm64.dmg" "$dmg"
"$apksigner" verify --print-certs "$apk" | grep -q "SHA-256 digest: $android_signer" || die "$apk isn't signed with the release key"
# a release's Mac app carries CI's libmpv without GPL code, never one left from an earlier build
if [ "$try" = 0 ]; then
    mount="$(mktemp -d)"
    hdiutil attach -noverify -nobrowse -readonly -mountpoint "$mount" "$dmg" > /dev/null 2>&1 || die "can't open $dmg"
    lgpl=0
    strings "$mount/Mizune.app/Contents/app/resources/libmpv.2.dylib" | grep -q -- "-Dgpl=false" && lgpl=1
    hdiutil detach -quiet "$mount"
    [ "$lgpl" = 1 ] || die "$dmg carries a libmpv built with GPL code"
fi

# the feed: the version, each platform's file with its size and SHA-256, and the notes the apps show as What's new
python3 - "$version" "$repo" "$tag" "$base" "$notes" "$dmg" "$apk" "$msi" "$deb" "$appimage" > "$out/update.json" <<'PY'
import hashlib, json, os, sys
version, repo, tag, base, notes, dmg, apk, msi, deb, appimage = sys.argv[1:]
def asset(path):
    name = os.path.basename(path)
    with open(path, "rb") as f:
        digest = hashlib.file_digest(f, "sha256").hexdigest()
    return {"name": name, "url": f"{base}/{name}", "size": os.path.getsize(path), "sha256": digest}
print(json.dumps({
    "version": version,
    "page": f"https://github.com/{repo}/releases/tag/{tag}",
    "notes": open(notes, encoding="utf-8").read(),
    "assets": {
        platform: asset(path)
        for platform, path in (
            ("macos-arm64", dmg), ("android-arm64", apk), ("windows-x64", msi), ("linux-x64", deb), ("linux-x64-appimage", appimage),
        )
        if os.path.exists(path)
    },
}, indent=2, ensure_ascii=False))
PY
openssl dgst -sha256 -sign "$feed_key" -out "$out/update.json.sig" "$out/update.json"

if [ "$try" = 1 ]; then
    cat <<EOF

Built $version into $out. To update the installed app to it:
  python3 -m http.server 8765 --directory $out
  JAVA_TOOL_OPTIONS=-Dmizune.updateFeed=$base/update.json "/Applications/Mizune.app/Contents/MacOS/Mizune"
It downloads in the background; "What's new in $version" appears in the library header once it's ready.
EOF
    exit 0
fi

gh release create "$tag" --repo "$repo" --draft --target "$(git rev-parse HEAD)" \
    --title "Mizune $version" --notes-file "$notes" \
    "$apk" "$dmg" "$msi" "$deb" "$appimage" "$out/update.json" "$out/update.json.sig"
cat <<EOF

Drafted $tag. Check it on GitHub, then publish it to send the update:
  gh release edit $tag --repo $repo --draft=false
EOF
