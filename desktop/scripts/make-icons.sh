#!/bin/bash
# Builds the app icons from desktop/icons/icon.svg, which is the Android launcher icon on the
# macOS icon grid. Needs ImageMagick; iconutil is part of macOS. Run it after editing the SVG:
#   desktop/scripts/make-icons.sh
set -euo pipefail
cd "$(dirname "$0")/.."
svg=icons/icon.svg
render() { magick -background none "$svg" -resize "${1}x${1}" "$2"; }

set=$(mktemp -d)/icon.iconset
mkdir -p "$set"
for size in 16 32 128 256 512; do
    render "$size" "$set/icon_${size}x${size}.png"
    render "$((size * 2))" "$set/icon_${size}x${size}@2x.png"
done
iconutil --convert icns --output icons/icon.icns "$set"
rm -rf "$set"

# Windows, and one PNG for Linux packages and the window/dock icon at run time
magick -background none "$svg" -define icon:auto-resize=256,128,64,48,32,16 icons/icon.ico
mkdir -p src/main/resources
render 512 src/main/resources/icon.png
