#!/usr/bin/env python3
"""Render a contact sheet for an icon SVG so the design can be judged visually.

Usage:  python3 scripts/preview.py icon-source/ic_launcher_full.svg [out.png]

Shows full-bleed, squircle-masked and circle-masked at 512, plus tiny 72px and
48px samples (real launcher scale) to check legibility.
"""
import io, sys
import cairosvg
from PIL import Image, ImageDraw

src = sys.argv[1] if len(sys.argv) > 1 else "icon-source/ic_launcher_full.svg"
out = sys.argv[2] if len(sys.argv) > 2 else "preview.png"
S = 512

full = Image.open(io.BytesIO(
    cairosvg.svg2png(url=src, output_width=S, output_height=S))).convert("RGBA")


def masked(shape, px):
    img = full.resize((px, px), Image.LANCZOS)
    m = Image.new("L", (px, px), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse([0, 0, px - 1, px - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.235), fill=255)
    o = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    o.paste(img, (0, 0), m)
    return o


pad = 24
sheet = Image.new("RGBA", (S * 3 + pad * 4, S + pad * 2 + 130), (245, 245, 245, 255))
sheet.paste(full, (pad, pad))
sheet.paste(masked("square", S), (pad * 2 + S, pad))
sheet.paste(masked("circle", S), (pad * 3 + S * 2, pad))
sheet.paste(masked("square", 72), (pad, S + pad + 36))
sheet.paste(masked("circle", 48), (pad + 110, S + pad + 48))
sheet.convert("RGB").save(out)
print(f"wrote {out}")
