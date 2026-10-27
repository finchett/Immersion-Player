#!/bin/bash
# Builds the app icons from desktop/icons/icon.svg, the app icon on the macOS icon grid. Needs
# librsvg (ImageMagick's own SVG renderer drops the gradients) and ImageMagick for the .ico;
# iconutil is part of macOS. Run it after editing the SVG:
#   desktop/scripts/make-icons.sh
set -euo pipefail
cd "$(dirname "$0")/.."
svg=icons/icon.svg
render() { rsvg-convert --width "$1" --height "$1" "$svg" --output "$2"; }

set=$(mktemp -d)/icon.iconset
mkdir -p "$set"
for size in 16 32 128 256 512; do
    render "$size" "$set/icon_${size}x${size}.png"
    render "$((size * 2))" "$set/icon_${size}x${size}@2x.png"
done
iconutil --convert icns --output icons/icon.icns "$set"
rm -rf "$set"

# Windows, and one PNG for Linux packages and the window/dock icon at run time
mkdir -p src/main/resources
render 512 src/main/resources/icon.png
render 256 "$set.png"
magick "$set.png" -define icon:auto-resize=256,128,64,48,32,16 icons/icon.ico
rm "$set.png"
