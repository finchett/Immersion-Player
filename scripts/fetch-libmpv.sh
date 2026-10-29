#!/bin/sh
# Downloads the Android app's native libraries (libmpv, FFmpeg, and the JNI glue that
# app/src/main/java/is/xyz/mpv/MPVLib.kt binds to), arm64, into app/src/main/jniLibs. They're
# mpv-android's, at the tag below, built without GPL code by scripts/build-libmpv-lgpl-android.sh
# in the Android libmpv workflow, which publishes them as their own release.
set -eu
TAG="2026-09-17"
URL="https://github.com/finchett/Mizune/releases/download/android-libmpv-lgpl-$TAG/android-arm64-libmpv.zip"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
echo "Downloading mpv-android $TAG's libraries, built without GPL code…"
curl -fL -o "$tmp/libs.zip" "$URL"
mkdir -p "$tmp/arm64-v8a"
unzip -q -o "$tmp/libs.zip" -d "$tmp/arm64-v8a"
mkdir -p "$DEST"
rm -rf "$DEST/arm64-v8a"
mv "$tmp/arm64-v8a" "$DEST/"
echo "Extracted to $DEST/arm64-v8a:"
ls "$DEST/arm64-v8a"
