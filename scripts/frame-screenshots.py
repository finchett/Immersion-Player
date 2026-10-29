#!/usr/bin/env python3
"""frame-screenshots.py <shots> <docs/screenshots> [name ...]

Puts the README's screenshots (ReadmeScreenshots, in <shots>) in a window: rounded corners, the
traffic lights where the app has a header, and a soft shadow, 1800 px wide. The video ones become
WebP, the rest PNG. With names, only those; otherwise all five. Needs ImageMagick and cwebp
(brew install imagemagick webp).
"""
import os
import subprocess
import sys
import tempfile

# name: (traffic lights, format)
SHOTS = {
    "player": (True, "webp"),
    "over-video": (False, "webp"),
    "library": (True, "png"),
    "welcome": (True, "png"),
    "themes": (True, "png"),
}


def run(*args):
    subprocess.run(list(args), check=True)


def size(path):
    out = subprocess.run(["magick", "identify", "-format", "%w %h", path], capture_output=True, text=True, check=True).stdout
    w, h = out.split()
    return int(w), int(h)


def frame(src_dir, out_dir, tmp_dir, name):
    lights, fmt = SHOTS[name]
    src = os.path.join(src_dir, name + ".png")
    tmp = os.path.join(tmp_dir, name + ".png")
    w, h = size(src)
    draw = []
    if lights:
        for x, colour in ((40, "#FF5F57"), (80, "#FEBC2E"), (120, "#28C840")):
            draw += ["-fill", colour, "-draw", f"circle {x},40 {x + 14},40"]
    run(
        "magick", src, *draw,
        "(", "-size", f"{w}x{h}", "xc:black", "-fill", "white", "-draw", f"roundrectangle 0,0,{w - 1},{h - 1},32,32", ")",
        "-alpha", "off", "-compose", "CopyOpacity", "-composite", "-compose", "Over",
        "-resize", "1800x",
        "(", "+clone", "-background", "#00000060", "-shadow", "55x22+0+14", ")", "+swap",
        "-background", "none", "-layers", "merge", "+repage", tmp,
    )
    if fmt == "webp":
        run("cwebp", "-quiet", "-q", "86", "-alpha_q", "100", tmp, "-o", os.path.join(out_dir, name + ".webp"))
    else:
        run("magick", tmp, "-define", "png:compression-level=9", os.path.join(out_dir, name + ".png"))


def main():
    src_dir, out_dir, *names = sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp_dir:
        for name in names or SHOTS:
            frame(src_dir, out_dir, tmp_dir, name)


if __name__ == "__main__":
    main()
