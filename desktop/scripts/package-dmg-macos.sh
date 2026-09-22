#!/bin/bash
# package-dmg-macos.sh <app> <version> <out.dmg>
# Sets the app's version, re-signs it ad hoc and builds a drag-to-Applications disk image.
set -euo pipefail
app="$1"; version="$2"; out="$3"
staging="$(dirname "$out")/staging"
rm -rf "$staging" "$out"
mkdir -p "$staging"
cp -R "$app" "$staging/"
plist="$staging/$(basename "$app")/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $version" -c "Set :CFBundleVersion $version" "$plist"
codesign --force --deep --sign - "$staging/$(basename "$app")"
ln -s /Applications "$staging/Applications"
hdiutil create -volname "Immersion Player" -srcfolder "$staging" -ov -format UDZO "$out" >/dev/null
rm -rf "$staging"
echo "$out ($(du -h "$out" | cut -f1))"
