#!/usr/bin/env python3
"""Generate every Android + store icon asset from the icon-source SVGs.

Run from the repo root:  python3 .claude/skills/logo-icon-drawing/scripts/render_icons.py

Source of truth (viewBox 0 0 108 108):
  icon-source/ic_launcher_background.svg
  icon-source/ic_launcher_foreground.svg
  icon-source/ic_launcher_monochrome.svg
  icon-source/ic_launcher_full.svg
  icon-source/feature_graphic.svg   (1024x500)

The app's minSdk is 31, so the launcher always uses the adaptive icon in
res/mipmap-anydpi/ (vector drawables). Legacy density PNGs are not generated;
set GENERATE_LEGACY_MIPMAPS = True if minSdk ever drops below 26.
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
GENERATE_LEGACY_MIPMAPS = False
STORE = os.path.join(ROOT, "store-listing")


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

    for dens, px in (DENSITIES.items() if GENERATE_LEGACY_MIPMAPS else []):
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

    os.makedirs(STORE, exist_ok=True)

    # Google Play listing icon: 512x512 full-bleed square. Play applies its own
    # rounded mask and shadow, so the PNG must not be pre-rounded.
    render(full, 512).convert("RGB").save(os.path.join(STORE, "play_icon_512.png"))
    print("store-listing/play_icon_512.png: 512px")

    # Store-listing preview: 512 squircle (what users roughly see in Play).
    masked(render(full, 512), squircle_mask(512)).save(
        os.path.join(STORE, "icon_preview_512.png"))
    print("store-listing/icon_preview_512.png: 512px")

    # Adaptive icon layers at 432px (108dp @ 4x) for Play Console / design tools.
    render(fg, 432).save(os.path.join(STORE, "adaptive_foreground_432.png"))
    render(bg, 432).convert("RGB").save(os.path.join(STORE, "adaptive_background_432.png"))
    mono = os.path.join(SRC, "ic_launcher_monochrome.svg")
    if os.path.exists(mono):
        render(mono, 432).save(os.path.join(STORE, "adaptive_monochrome_432.png"))
    print("store-listing/adaptive_*_432.png")

    # Feature graphic: 1024x500, 24-bit PNG (Play rejects alpha here).
    feat = os.path.join(SRC, "feature_graphic.svg")
    if os.path.exists(feat):
        png = cairosvg.svg2png(url=feat, output_width=1024, output_height=500)
        Image.open(io.BytesIO(png)).convert("RGB").save(
            os.path.join(STORE, "feature_graphic_1024x500.png"))
        print("store-listing/feature_graphic_1024x500.png")


if __name__ == "__main__":
    main()
