#!/bin/bash
# build-libmpv-lgpl-linux.sh <prefix>
#
# Builds libplacebo, FFmpeg and libmpv without their GPL parts into <prefix>, for the Linux
# AppImage to bundle in place of Ubuntu's GPL libmpv. For Ubuntu 22.04, whose glibc sets how old a
# Linux the AppImage runs on; its libplacebo is too old for mpv 0.41, so that's built here too.
# Other libraries come from Ubuntu's packages, all LGPL or more permissive. FFmpeg's configure
# refuses GPL code unless asked for it, and mpv's -Dgpl=false leaves out its own GPL parts.
# X11, EGL and Wayland are built in so mpv can draw with OpenGL on a real GPU.
set -euo pipefail

LIBPLACEBO=v7.351.0
FFMPEG=7.1.1
MPV=0.41.0

prefix="$(mkdir -p "$1" && cd "$1" && pwd)"
work="$(mktemp -d)"
jobs="$(nproc)"
export PKG_CONFIG_PATH="$prefix/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"

sudo apt-get update -q
sudo apt-get install -yq --no-install-recommends \
    build-essential git ninja-build nasm pkg-config python3 python3-pip \
    libdav1d-dev libva-dev libvdpau-dev libdrm-dev \
    libass-dev libfreetype-dev libfribidi-dev libharfbuzz-dev libfontconfig-dev \
    liblcms2-dev libuchardet-dev zlib1g-dev libjpeg-dev \
    libasound2-dev libpulse-dev \
    libx11-dev libxext-dev libxpresent-dev libxrandr-dev libxss-dev libxinerama-dev libxv-dev \
    libegl-dev libgl-dev libgbm-dev libwayland-dev wayland-protocols libxkbcommon-dev
# 22.04's Meson (0.61) is older than libplacebo and mpv need
python3 -m pip install --quiet --user meson
export PATH="$HOME/.local/bin:$PATH"
meson --version

cd "$work"
git clone -q --depth 1 --branch "$LIBPLACEBO" --recurse-submodules https://github.com/haasn/libplacebo.git
cd libplacebo
meson setup build --prefix="$prefix" --libdir=lib --buildtype=release \
    -Dvulkan=disabled -Dd3d11=disabled -Dshaderc=disabled -Dglslang=disabled \
    -Ddemos=false -Dtests=false
meson install -C build

cd "$work"
# ffmpeg.org, retried, and FFmpeg's own GitHub mirror if it stays out of reach
mkdir "ffmpeg-$FFMPEG"
curl -sSfL --retry 5 --retry-delay 10 --retry-all-errors "https://ffmpeg.org/releases/ffmpeg-$FFMPEG.tar.xz" -o ffmpeg.tar.xz \
    && tar xJf ffmpeg.tar.xz --strip-components=1 -C "ffmpeg-$FFMPEG" \
    || curl -sSfL --retry 5 --retry-delay 10 --retry-all-errors "https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n$FFMPEG.tar.gz" \
        | tar xz --strip-components=1 -C "ffmpeg-$FFMPEG"
cd "ffmpeg-$FFMPEG"
./configure --prefix="$prefix" --enable-shared --disable-static \
    --disable-programs --disable-doc --disable-debug \
    --enable-libdav1d --enable-vaapi --enable-vdpau
# configure records whether GPL code is in; it mustn't be
grep -q "#define CONFIG_GPL 0" config.h || { echo "error: FFmpeg was configured with GPL code" >&2; exit 1; }
make -j"$jobs"
make install

cd "$work"
curl -sSfL --retry 5 --retry-delay 10 --retry-all-errors "https://github.com/mpv-player/mpv/archive/refs/tags/v$MPV.tar.gz" | tar xz
cd "mpv-$MPV"
# libmpv only: no player, manual or scripting
meson setup build --prefix="$prefix" --libdir=lib --buildtype=release \
    -Dgpl=false -Dlibmpv=true -Dcplayer=false \
    -Dmanpage-build=disabled -Dhtml-build=disabled -Dpdf-build=disabled \
    -Dlua=disabled -Djavascript=disabled \
    -Dgl=enabled -Degl=enabled
meson install -C build

echo "built $prefix/lib/libmpv.so.2 without GPL code"
