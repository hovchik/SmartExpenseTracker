#!/usr/bin/env python3
"""Generate every Android + store icon asset from the icon-source SVGs.

Run from the repo root:  python3 .claude/skills/logo-icon-drawing/scripts/render_icons.py

Source of truth (viewBox 0 0 108 108):
  icon-source/ic_launcher_background.svg
  icon-source/ic_launcher_foreground.svg
  icon-source/ic_launcher_full.svg
"""
import io, os, sys
import cairosvg
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
SRC = os.path.join(ROOT, "icon-source")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Legacy mipmap density -> pixel size (this project uses one size per density
# for all four PNGs, matching its existing convention).
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render(svg_path, px):
    png = cairosvg.svg2png(url=svg_path, output_width=px, output_height=px)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def squircle_mask(px, radius_ratio=0.235):
    m = Image.new("L", (px, px), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, px - 1, px - 1], radius=int(px * radius_ratio), fill=255)
    return m


def circle_mask(px):
    m = Image.new("L", (px, px), 0)
    ImageDraw.Draw(m).ellipse([0, 0, px - 1, px - 1], fill=255)
    return m


def masked(img, mask):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    full = os.path.join(SRC, "ic_launcher_full.svg")
    fg = os.path.join(SRC, "ic_launcher_foreground.svg")
    bg = os.path.join(SRC, "ic_launcher_background.svg")
    for p in (full, fg, bg):
        if not os.path.exists(p):
            sys.exit(f"missing source SVG: {p}")

    for dens, px in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        comp = render(full, px)
        masked(comp, squircle_mask(px)).save(os.path.join(d, "ic_launcher.png"))
        masked(comp, circle_mask(px)).save(os.path.join(d, "ic_launcher_round.png"))
        render(fg, px).save(os.path.join(d, "ic_launcher_foreground.png"))
        render(bg, px).save(os.path.join(d, "ic_launcher_background.png"))
        print(f"mipmap-{dens}: {px}px")

    # Play Store icon: 512 full-bleed square (RGB, no alpha).
    play = os.path.join(ROOT, "app", "src", "main", "ic_launcher-playstore.png")
    render(full, 512).convert("RGB").save(play)
    print("ic_launcher-playstore.png: 512px")

    # Store-listing preview: 512 squircle.
    prev_dir = os.path.join(ROOT, "store-listing")
    if os.path.isdir(prev_dir):
        masked(render(full, 512), squircle_mask(512)).save(
            os.path.join(prev_dir, "icon_preview_512.png"))
        print("store-listing/icon_preview_512.png: 512px")


if __name__ == "__main__":
    main()
