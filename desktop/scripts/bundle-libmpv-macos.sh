#!/bin/bash
# Copies Homebrew's libmpv and every Homebrew library it loads into $1, rewrites their load paths
# to @loader_path so they find each other there, and ad-hoc signs them (Apple Silicon refuses to
# load unsigned code). The result doesn't need Homebrew at run time.
set -euo pipefail

out="$1"
entry="${LIBMPV:-/opt/homebrew/lib/libmpv.2.dylib}"
rm -rf "$out"
mkdir -p "$out"

homebrew_deps() { otool -L "$1" | tail -n +2 | awk '{print $1}' | grep -E '^/opt/homebrew|^/usr/local' || true; }

# walk the dependency tree; each library is copied under the name the others reference it by
queue=("$entry")
done_list=" "
while [ ${#queue[@]} -gt 0 ]; do
    lib="${queue[0]}"
    queue=("${queue[@]:1}")
    name=$(basename "$lib")
    case "$done_list" in *" $name "*) continue ;; esac
    done_list+="$name "
    cp "$(realpath "$lib")" "$out/$name"
    chmod u+w "$out/$name"
    while read -r dep; do
        [ -n "$dep" ] && queue+=("$dep")
    done < <(homebrew_deps "$lib")
done

for f in "$out"/*.dylib; do
    install_name_tool -id "@loader_path/$(basename "$f")" "$f" 2>/dev/null
    while read -r dep; do
        [ -n "$dep" ] && install_name_tool -change "$dep" "@loader_path/$(basename "$dep")" "$f" 2>/dev/null
    done < <(homebrew_deps "$f")
    codesign --force --sign - "$f" 2>/dev/null
done

# nothing may still point into Homebrew
leftover=$(for f in "$out"/*.dylib; do otool -L "$f" | grep -E '/opt/homebrew|/usr/local' | sed "s|^|$(basename "$f"): |" || true; done)
if [ -n "$leftover" ]; then
    echo "error: libraries still reference Homebrew:" >&2
    echo "$leftover" >&2
    exit 1
fi
echo "bundled $(ls "$out" | wc -l | tr -d ' ') libraries ($(du -sh "$out" | cut -f1)) into $out"
