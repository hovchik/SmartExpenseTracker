#!/usr/bin/env python3
"""
Generate 512x512 Google Play icon for FlowSense – AI Expense Tracker.

Design:
  • Deep blue-to-indigo gradient background with rounded corners
  • Subtle dot-grid overlay for depth
  • White area-chart wave (upward flow) with gradient fill
  • Glowing accent dot at the wave peak
  • Four small sparkle stars in the top-right corner (AI motif)
  • "FS" monogram hidden within the design (the flow curve forms an implied F shape)
"""

import math
from PIL import Image, ImageDraw, ImageFilter, ImageChops

SIZE = 512
CORNER_RADIUS = 96   # ~19 % of 512 – matches Google Play's mask

# ── Helpers ──────────────────────────────────────────────────────────────────

def lerp(a, b, t):
    return a + (b - a) * t

def lerp_color(c1, c2, t):
    return tuple(int(lerp(a, b, t)) for a, b in zip(c1, c2))

# ── 1. Gradient background ────────────────────────────────────────────────────
# Top: #1565C0   Mid: #1A237E   Bottom: #0D0B2E  (blue → deep indigo → near-black)

BG_TOP    = (21,  101, 192)   # #1565C0
BG_MID    = (26,  35,  126)   # #1A237E
BG_BOTTOM = (13,  11,  46)    # #0D0B2E

bg = Image.new('RGB', (SIZE, SIZE))
draw_bg = ImageDraw.Draw(bg)

for y in range(SIZE):
    t = y / (SIZE - 1)
    if t < 0.5:
        color = lerp_color(BG_TOP, BG_MID, t * 2)
    else:
        color = lerp_color(BG_MID, BG_BOTTOM, (t - 0.5) * 2)
    draw_bg.line([(0, y), (SIZE - 1, y)], fill=color)

# ── 2. Dot-grid overlay ───────────────────────────────────────────────────────
grid = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw_grid = ImageDraw.Draw(grid)

GRID_STEP = 32
DOT_R     = 1
DOT_ALPHA = 30   # very subtle

for gx in range(GRID_STEP, SIZE, GRID_STEP):
    for gy in range(GRID_STEP, SIZE, GRID_STEP):
        draw_grid.ellipse(
            [gx - DOT_R, gy - DOT_R, gx + DOT_R, gy + DOT_R],
            fill=(255, 255, 255, DOT_ALPHA)
        )

bg_rgba = bg.convert('RGBA')
bg_rgba = Image.alpha_composite(bg_rgba, grid)

# ── 3. Flow wave ──────────────────────────────────────────────────────────────
# The wave represents expense/income "flow" over time.
# It rises gently, dips slightly, then climbs to a peak – a positive trend.

def wave_y(x_norm):
    """Returns normalised y (0=bottom, 1=top) for a given x in [0,1]."""
    # Piecewise curve: starts mid-low, dips, then surges upward
    # Use a smooth blend of sine segments
    if x_norm < 0.15:
        return 0.38 - 0.05 * math.sin(x_norm / 0.15 * math.pi)
    elif x_norm < 0.35:
        t = (x_norm - 0.15) / 0.20
        return lerp(0.38, 0.28, t - 0.12 * math.sin(t * math.pi))
    elif x_norm < 0.55:
        t = (x_norm - 0.35) / 0.20
        return lerp(0.28, 0.55, t - 0.08 * math.sin(t * math.pi))
    elif x_norm < 0.75:
        t = (x_norm - 0.55) / 0.20
        return lerp(0.55, 0.44, t - 0.10 * math.sin(t * math.pi))
    else:
        t = (x_norm - 0.75) / 0.25
        return lerp(0.44, 0.76, t - 0.06 * math.sin(t * math.pi))

# Canvas margins
LEFT_MARGIN  = 58
RIGHT_MARGIN = 58
BOTTOM_LINE  = int(SIZE * 0.78)   # baseline of the chart
TOP_BOUND    = int(SIZE * 0.18)   # upper limit of chart area

CHART_W = SIZE - LEFT_MARGIN - RIGHT_MARGIN
CHART_H = BOTTOM_LINE - TOP_BOUND

STEPS = 300   # x-sample resolution

# Compute polyline points
pts = []
for i in range(STEPS + 1):
    xn = i / STEPS
    x  = LEFT_MARGIN + xn * CHART_W
    yn = wave_y(xn)
    y  = BOTTOM_LINE - yn * CHART_H
    pts.append((x, y))

# Area polygon (closed down to baseline)
area_poly = pts + [(pts[-1][0], BOTTOM_LINE), (pts[0][0], BOTTOM_LINE)]

# ── Area fill: layered glow approach ─────────────────────────────────────────
wave_layer = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw_wave  = ImageDraw.Draw(wave_layer)

# Draw area fills with decreasing alpha from bottom to top (fake vertical gradient)
# Render in 6 horizontal slices, each slightly more transparent toward the top
SLICES = 24
for s in range(SLICES):
    t_bot = s       / SLICES
    t_top = (s + 1) / SLICES
    y_bot = BOTTOM_LINE - t_bot * CHART_H * 0.85
    y_top = BOTTOM_LINE - t_top * CHART_H * 0.85
    alpha = int(lerp(110, 8, s / SLICES))   # fades out toward top
    # Build clipped polygon for this horizontal band
    band_poly = []
    for px, py in pts:
        if py <= y_bot:
            band_poly.append((px, min(py, y_top)))
        else:
            band_poly.append((px, y_bot))
    band_poly += [(pts[-1][0], y_bot), (pts[0][0], y_bot)]
    draw_wave.polygon(band_poly, fill=(100, 181, 255, alpha))

# Stroke – thick white line with slight glow
STROKE_W = 6

# Glow pass (wider, semi-transparent blue-white)
for glow_w, glow_a in [(14, 18), (9, 35), (5, 60)]:
    draw_wave.line(pts, fill=(180, 210, 255, glow_a), width=glow_w)

# Crisp white stroke on top
draw_wave.line(pts, fill=(255, 255, 255, 245), width=STROKE_W)

# ── Peak dot ─────────────────────────────────────────────────────────────────
peak_x, peak_y = pts[-1]   # rightmost / highest point
DOT_OUTER = 18
DOT_INNER = 10

# Outer glow rings
for r, a in [(34, 15), (26, 28), (20, 50)]:
    draw_wave.ellipse(
        [peak_x - r, peak_y - r, peak_x + r, peak_y + r],
        fill=(100, 200, 255, a)
    )
draw_wave.ellipse(
    [peak_x - DOT_OUTER, peak_y - DOT_OUTER, peak_x + DOT_OUTER, peak_y + DOT_OUTER],
    fill=(255, 255, 255, 255)
)
draw_wave.ellipse(
    [peak_x - DOT_INNER, peak_y - DOT_INNER, peak_x + DOT_INNER, peak_y + DOT_INNER],
    fill=(64, 156, 255, 255)
)

# ── Sparkle stars (AI motif) ─────────────────────────────────────────────────
def draw_sparkle(draw, cx, cy, r, color=(255, 255, 255, 230)):
    """Draw a 4-point star sparkle."""
    for angle_deg in range(0, 360, 90):
        angle = math.radians(angle_deg)
        ox = cx + r * math.cos(angle)
        oy = cy + r * math.sin(angle)
        # thin diamond arm
        perp = math.radians(angle_deg + 90)
        w = max(1, int(r * 0.18))
        poly = [
            (cx + w * math.cos(perp), cy + w * math.sin(perp)),
            (ox, oy),
            (cx - w * math.cos(perp), cy - w * math.sin(perp)),
            (cx, cy),
        ]
        draw.polygon(poly, fill=color)

sparkle_layer = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw_sparkle_layer = ImageDraw.Draw(sparkle_layer)

sparkles = [
    (358, 100, 22, (255, 255, 255, 230)),   # large
    (400,  72, 13, (200, 230, 255, 200)),   # medium
    (385, 132, 9,  (180, 210, 255, 170)),   # small
    (424, 110, 6,  (255, 255, 255, 130)),   # tiny
]
for sx, sy, sr, sc in sparkles:
    draw_sparkle(draw_sparkle_layer, sx, sy, sr, sc)

# ── Baseline rule ─────────────────────────────────────────────────────────────
baseline_layer = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw_bl = ImageDraw.Draw(baseline_layer)
draw_bl.line(
    [(LEFT_MARGIN, BOTTOM_LINE), (SIZE - RIGHT_MARGIN, BOTTOM_LINE)],
    fill=(255, 255, 255, 40),
    width=2
)

# ── 4. Compose all layers ─────────────────────────────────────────────────────
icon = bg_rgba
icon = Image.alpha_composite(icon, baseline_layer)
icon = Image.alpha_composite(icon, wave_layer)
icon = Image.alpha_composite(icon, sparkle_layer)

# ── 5. Rounded-corner mask ────────────────────────────────────────────────────
mask = Image.new('L', (SIZE, SIZE), 0)
draw_mask = ImageDraw.Draw(mask)
draw_mask.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=CORNER_RADIUS, fill=255)

# Apply mask to alpha channel
r_ch, g_ch, b_ch, a_ch = icon.split()
a_ch = ImageChops.multiply(a_ch, mask)
icon = Image.merge('RGBA', (r_ch, g_ch, b_ch, a_ch))

# ── 6. Light vignette ─────────────────────────────────────────────────────────
vignette = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw_vig = ImageDraw.Draw(vignette)
for step in range(50):
    t     = step / 50
    inset = int(step * 4.0)
    if SIZE - 1 - inset <= inset:
        break
    alpha = int(lerp(55, 0, t))
    radius = max(1, CORNER_RADIUS - inset)
    draw_vig.rounded_rectangle(
        [inset, inset, SIZE - 1 - inset, SIZE - 1 - inset],
        radius=radius,
        outline=(0, 0, 0, alpha),
        width=1
    )
icon = Image.alpha_composite(icon, vignette)

# ── 7. Final composite on white (Play Store needs RGB PNG without transparency)
final = Image.new('RGB', (SIZE, SIZE), (255, 255, 255))
final.paste(icon, mask=icon.split()[3])

out_path = 'store-listing/icon_512x512.png'
final.save(out_path, 'PNG', optimize=True)
print(f'Icon saved → {out_path}  ({SIZE}x{SIZE} px)')
