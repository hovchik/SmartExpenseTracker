#!/usr/bin/env python3
"""
Generate 1024x500 Google Play Feature Graphic for FlowSense – AI Expense Tracker.

Layout (left → right):
  LEFT PANEL  (~480 px wide)
    • "FlowSense" wordmark (large, bold)
    • Tagline: "AI-Powered Expense Tracking"
    • Three mini benefit pills: Receipt Scan · SMS Capture · Smart Insights
    • Small sparkle stars cluster

  RIGHT PANEL  (~544 px wide)
    • Large area-chart wave (same design language as icon)
    • Glowing peak dot
    • Subtle grid lines

Background: diagonal blue → deep indigo gradient matching the icon.
"""

import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

W, H = 1024, 500

FONT_BOLD   = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_REGULAR= '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

# Brand colours (same as icon)
BG_LEFT   = (21,  101, 192)   # #1565C0
BG_RIGHT  = (10,   8,  38)    # #0A0826
ACCENT    = (64,  156, 255)   # #409CFF  (dot / pill border)
WHITE     = (255, 255, 255)

def lerp(a, b, t):       return a + (b - a) * t
def clamp(v, lo, hi):    return max(lo, min(hi, v))
def lerp_c(c1, c2, t):   return tuple(int(lerp(a, b, clamp(t,0,1))) for a,b in zip(c1,c2))


# ── 1. Gradient background ────────────────────────────────────────────────────
img  = Image.new('RGB', (W, H))
draw = ImageDraw.Draw(img)

C_TL = (25, 110, 200)    # top-left
C_TR = (16,  60, 140)    # top-right
C_BL = (18,  35, 110)    # bottom-left
C_BR = ( 8,   6,  30)    # bottom-right

for y in range(H):
    ty = y / (H - 1)
    for x in range(W):
        tx = x / (W - 1)
        top   = lerp_c(C_TL, C_TR, tx)
        bot   = lerp_c(C_BL, C_BR, tx)
        color = lerp_c(top, bot, ty)
        draw.point((x, y), fill=color)

# ── 2. Dot-grid overlay ───────────────────────────────────────────────────────
STEP, DR = 28, 1
for gx in range(STEP, W, STEP):
    for gy in range(STEP, H, STEP):
        alpha = 22
        # blend white dot into background
        px = img.getpixel((gx, gy))
        blended = tuple(int(c * (1 - alpha/255) + 255 * (alpha/255)) for c in px)
        img.putpixel((gx, gy), blended)

# ── 3. Vertical divider (subtle) ─────────────────────────────────────────────
DIV_X = 460
for y in range(30, H - 30):
    t     = abs(y - H/2) / (H/2 - 30)
    alpha = int(lerp(40, 0, t))
    px    = img.getpixel((DIV_X, y))
    blended = tuple(int(c * (1 - alpha/255) + 255 * (alpha/255)) for c in px)
    img.putpixel((DIV_X, y), blended)

draw = ImageDraw.Draw(img)   # refresh after pixel ops

# ── 4. Chart (right panel) ────────────────────────────────────────────────────
CL   = DIV_X + 30    # chart left
CR   = W - 40        # chart right
CB   = H - 60        # chart bottom baseline
CT   = 55            # chart top bound
CW   = CR - CL
CH   = CB - CT

STEPS = 400

def wave_y(xn):
    """Normalised y in [0,1] — same curve as icon but slightly different inflection."""
    if xn < 0.12:
        return 0.34 - 0.04 * math.sin(xn / 0.12 * math.pi)
    elif xn < 0.30:
        t = (xn - 0.12) / 0.18
        return lerp(0.34, 0.22, t - 0.10 * math.sin(t * math.pi))
    elif xn < 0.50:
        t = (xn - 0.30) / 0.20
        return lerp(0.22, 0.58, t - 0.07 * math.sin(t * math.pi))
    elif xn < 0.68:
        t = (xn - 0.50) / 0.18
        return lerp(0.58, 0.42, t - 0.09 * math.sin(t * math.pi))
    else:
        t = (xn - 0.68) / 0.32
        return lerp(0.42, 0.82, t - 0.05 * math.sin(t * math.pi))

pts = []
for i in range(STEPS + 1):
    xn = i / STEPS
    x  = CL + xn * CW
    yn = wave_y(xn)
    y  = CB - yn * CH
    pts.append((x, y))

# Horizontal grid lines
for frac in [0.25, 0.5, 0.75]:
    gy = int(CT + (1 - frac) * CH)
    draw.line([(CL, gy), (CR, gy)], fill=(255, 255, 255, 15), width=1)

# Area fill slices
wave_layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
wdraw      = ImageDraw.Draw(wave_layer)

SLICES = 30
for s in range(SLICES):
    t_bot = s / SLICES
    t_top = (s + 1) / SLICES
    y_bot = CB - t_bot * CH * 0.88
    y_top = CB - t_top * CH * 0.88
    alpha = int(lerp(100, 5, s / SLICES))
    band_poly = []
    for px, py in pts:
        clipped_y = min(py, y_bot)
        band_poly.append((px, clipped_y))
    band_poly += [(pts[-1][0], y_bot), (pts[0][0], y_bot)]
    wdraw.polygon(band_poly, fill=(100, 181, 255, alpha))

# Glow passes + crisp stroke
for gw, ga in [(18, 15), (12, 28), (7, 50)]:
    wdraw.line(pts, fill=(180, 215, 255, ga), width=gw)
wdraw.line(pts, fill=(255, 255, 255, 248), width=5)

# Peak dot
px, py = pts[-1]
for r, a in [(38, 12), (28, 22), (20, 42)]:
    wdraw.ellipse([px-r, py-r, px+r, py+r], fill=(100, 200, 255, a))
wdraw.ellipse([px-16, py-16, px+16, py+16], fill=(255, 255, 255, 255))
wdraw.ellipse([px- 9, py- 9, px+ 9, py+ 9], fill=(64, 156, 255, 255))

# Baseline
wdraw.line([(CL, CB), (CR, CB)], fill=(255, 255, 255, 35), width=2)

img = img.convert('RGBA')
img = Image.alpha_composite(img, wave_layer)

# ── 5. Text (left panel) ──────────────────────────────────────────────────────
text_layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
tdraw      = ImageDraw.Draw(text_layer)

def draw_sparkle(d, cx, cy, r, color=(255,255,255,220)):
    for ang in range(0, 360, 90):
        a = math.radians(ang)
        ox, oy = cx + r*math.cos(a), cy + r*math.sin(a)
        perp = math.radians(ang+90)
        w = max(1, int(r*0.17))
        poly = [
            (cx + w*math.cos(perp), cy + w*math.sin(perp)),
            (ox, oy),
            (cx - w*math.cos(perp), cy - w*math.sin(perp)),
            (cx, cy),
        ]
        d.polygon(poly, fill=color)

# Sparkle cluster (top-left corner of left panel)
for sx, sy, sr, sa in [
    (54,  54, 18, 220),
    (86,  34, 11, 190),
    (90,  70,  8, 160),
    (110, 46,  5, 120),
]:
    draw_sparkle(tdraw, sx, sy, sr, (255,255,255,sa))

# App name "FlowSense"
try:
    fnt_name = ImageFont.truetype(FONT_BOLD, 88)
    fnt_tag  = ImageFont.truetype(FONT_REGULAR, 28)
    fnt_pill = ImageFont.truetype(FONT_REGULAR, 22)
except Exception:
    fnt_name = fnt_tag = fnt_pill = ImageFont.load_default()

NAME_X, NAME_Y = 52, 120

# Shadow pass
tdraw.text((NAME_X+3, NAME_Y+3), 'FlowSense', font=fnt_name, fill=(0,0,0,80))
# Main wordmark
tdraw.text((NAME_X, NAME_Y), 'FlowSense', font=fnt_name, fill=(255,255,255,255))

# Tagline
TAG_Y = NAME_Y + 100
tdraw.text((NAME_X, TAG_Y), 'AI-Powered Expense Tracking', font=fnt_tag,
           fill=(190, 220, 255, 230))

# Benefit pills
PILLS = ['Receipt Scan', 'SMS Capture', 'Smart Insights']
PILL_Y   = TAG_Y + 60
PILL_PAD = 14
PILL_H   = 38
PILL_GAP = 14

cx = NAME_X
for pill_text in PILLS:
    # Measure text width
    bbox  = tdraw.textbbox((0, 0), pill_text, font=fnt_pill)
    tw    = bbox[2] - bbox[0]
    pw    = tw + PILL_PAD * 2
    ph    = PILL_H
    rx    = cx
    ry    = PILL_Y

    # Pill background (semi-transparent white)
    pill_bg = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    pbg_draw = ImageDraw.Draw(pill_bg)
    pbg_draw.rounded_rectangle(
        [rx, ry, rx + pw, ry + ph],
        radius=ph // 2,
        fill=(255, 255, 255, 28),
        outline=(255, 255, 255, 70),
        width=1,
    )
    text_layer = Image.alpha_composite(text_layer, pill_bg)
    tdraw = ImageDraw.Draw(text_layer)

    tdraw.text(
        (rx + PILL_PAD, ry + (ph - (bbox[3]-bbox[1])) // 2 - 1),
        pill_text,
        font=fnt_pill,
        fill=(220, 240, 255, 240),
    )
    cx += pw + PILL_GAP

img = Image.alpha_composite(img, text_layer)

# ── 6. Subtle inner shadow along top & bottom edges ──────────────────────────
edge_layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
edraw      = ImageDraw.Draw(edge_layer)
for i in range(20):
    a = int(lerp(30, 0, i/20))
    edraw.line([(0,i),(W,i)],       fill=(0,0,0,a))
    edraw.line([(0,H-1-i),(W,H-1-i)], fill=(0,0,0,a))
img = Image.alpha_composite(img, edge_layer)

# ── 7. Convert to RGB (Play Store: no alpha) ──────────────────────────────────
final = Image.new('RGB', (W, H), (255, 255, 255))
final.paste(img.convert('RGB'))   # already no transparency concerns

out = 'store-listing/feature_graphic_1024x500.png'
final.save(out, 'PNG', optimize=True)
print(f'Feature graphic saved → {out}  ({W}x{H} px)')
