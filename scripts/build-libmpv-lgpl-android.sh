#!/bin/bash
# build-libmpv-lgpl-android.sh <out>
#
# Builds mpv-android's native libraries (libmpv, FFmpeg, and the JNI glue MPVLib.kt binds to)
# without GPL code, for arm64, and puts them in <out>. It runs mpv-android's own build at the tag
# scripts/fetch-libmpv.sh has always taken, so the glue matches the vendored MPVLib.kt, with two
# changes: FFmpeg without --enable-gpl, --enable-version3 and mbedtls (TLS for network streams,
# which the app doesn't play, and which needs version3), so it's LGPL-2.1+; and mpv with
# -Dgpl=false. Linux x64; fetches the Android SDK and NDK itself; most of an hour.
set -euo pipefail

TAG="2026-09-17"

out="$(mkdir -p "$1" && cd "$1" && pwd)"
work="$(mktemp -d)"

git clone -q --depth 1 --branch "$TAG" https://github.com/mpv-android/mpv-android.git "$work/mpv-android"
cd "$work/mpv-android/buildscripts"

# change only what's needed, and stop if mpv-android's scripts no longer read as expected
patch_line() {
    local file="$1" from="$2" to="$3"
    grep -qF -- "$from" "$file" || { echo "error: $file no longer has: $from" >&2; exit 1; }
    sed -i "s|$(printf '%s' "$from" | sed 's/[][\.*^$|]/\\&/g')|$to|" "$file"
}
patch_line scripts/ffmpeg.sh '--enable-{jni,mediacodec,mbedtls,libdav1d,libxml2}' '--enable-{jni,mediacodec,libdav1d,libxml2}'
patch_line scripts/ffmpeg.sh '--disable-static --enable-shared --enable-{gpl,version3}' '--disable-static --enable-shared'
patch_line scripts/mpv.sh '-Dlibmpv=true -Dcplayer=false' '-Dlibmpv=true -Dcplayer=false -Dgpl=false'
git diff --stat

./download.sh
./buildall.sh --arch arm64

# FFmpeg's configure records whether GPL code went in; it mustn't have
config="$(find deps/ffmpeg -path '*arm64*' -name config.h | head -1)"
[ -n "$config" ] || { echo "error: can't find FFmpeg's config.h" >&2; exit 1; }
grep -q "#define CONFIG_GPL 0" "$config" || { echo "error: FFmpeg was configured with GPL code" >&2; exit 1; }
grep -q "#define CONFIG_VERSION3 0" "$config" || { echo "error: FFmpeg was configured as (L)GPLv3" >&2; exit 1; }

# the libraries the app ships, from the APK mpv-android's build made
apk="$(find ../app/build/outputs/apk -name '*.apk' | head -1)"
[ -n "$apk" ] || { echo "error: mpv-android's build made no APK" >&2; exit 1; }
unzip -q -o "$apk" 'lib/arm64-v8a/*' -d "$work/apk"
cp "$work/apk/lib/arm64-v8a/"*.so "$out/"
ls -la "$out"
echo "built mpv-android $TAG's libraries without GPL code into $out"
