#!/bin/bash
# build-libmpv-lgpl-macos.sh <prefix>
#
# Builds FFmpeg and libmpv without their GPL parts into <prefix>, for the Mac app to bundle in place
# of Homebrew's GPL build (bundle-libmpv-macos.sh, with LIBMPV and LIBMPV_PREFIX). The libraries
# they use come from Homebrew, all LGPL or more permissive: dav1d, libass and what it uses,
# libplacebo, Little CMS, uchardet, zimg. FFmpeg's configure refuses GPL code unless asked for it,
# and mpv's -Dgpl=false leaves out its own GPL parts. Apple Silicon; about ten minutes.
set -euo pipefail

FFMPEG=7.1.1
MPV=0.41.0

prefix="$(mkdir -p "$1" && cd "$1" && pwd)"
work="$(mktemp -d)"
jobs="$(sysctl -n hw.ncpu)"

brew install --quiet meson ninja nasm pkgconf dav1d libass libplacebo little-cms2 uchardet zimg

cd "$work"
curl -sSfL "https://ffmpeg.org/releases/ffmpeg-$FFMPEG.tar.xz" | tar xJ
cd "ffmpeg-$FFMPEG"
./configure --prefix="$prefix" --enable-shared --disable-static \
    --disable-programs --disable-doc --disable-debug \
    --enable-videotoolbox --enable-audiotoolbox --enable-libdav1d
# configure records whether GPL code is in; it mustn't be
grep -q "#define CONFIG_GPL 0" config.h || { echo "error: FFmpeg was configured with GPL code" >&2; exit 1; }
make -j"$jobs"
make install

cd "$work"
curl -sSfL "https://github.com/mpv-player/mpv/archive/refs/tags/v$MPV.tar.gz" | tar xz
cd "mpv-$MPV"
# libmpv only: no player, manual or scripting. Swift stays (as Homebrew builds it): mpv's Mac
# clipboard code needs it
PKG_CONFIG_PATH="$prefix/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}" \
    meson setup build --prefix="$prefix" --libdir=lib --buildtype=release \
    -Dgpl=false -Dlibmpv=true -Dcplayer=false \
    -Dmanpage-build=disabled -Dhtml-build=disabled -Dpdf-build=disabled \
    -Dlua=disabled -Djavascript=disabled
meson install -C build

echo "built $(ls "$prefix/lib/libmpv"*.dylib | head -1) without GPL code"
