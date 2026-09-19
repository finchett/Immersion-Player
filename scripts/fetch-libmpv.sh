#!/bin/sh
# Downloads the pinned mpv-android release and extracts its arm64 native libraries
# (libmpv, ffmpeg, and the JNI glue that app/src/main/java/is/xyz/mpv/MPVLib.kt binds to).
set -eu

TAG="2026-09-17"
APK="app-default-arm64-v8a-release.apk"
URL="https://github.com/mpv-android/mpv-android/releases/download/$TAG/$APK"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

echo "Downloading mpv-android $TAG…"
curl -fL -o "$tmp/$APK" "$URL"
unzip -q -o "$tmp/$APK" 'lib/arm64-v8a/*' -d "$tmp"
mkdir -p "$DEST"
rm -rf "$DEST/arm64-v8a"
mv "$tmp/lib/arm64-v8a" "$DEST/"
echo "Extracted to $DEST/arm64-v8a:"
ls "$DEST/arm64-v8a"
