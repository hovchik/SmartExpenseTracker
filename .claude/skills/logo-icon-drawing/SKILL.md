---
name: logo-icon-drawing
description: >-
  Design and generate app logos and launcher/app icons — especially Android
  adaptive icons — from a single vector source. Use when the user wants to
  create, redesign, or update an app icon, launcher icon, logo, or brand mark,
  or regenerate icon assets (all mipmap densities, Play Store 512, monochrome
  themed icon, store-listing preview). Covers the design method (concept,
  silhouette, color, safe zone), an SVG-first workflow, visual iteration by
  rendering previews, and a script that produces every required Android asset.
---

# Logo / Icon Drawing

Produce a clean, modern app icon that reads at every size, from one vector
source of truth. The golden rule: **design in SVG, look at what you drew,
iterate, then generate all raster + Android assets from that source** — never
hand-edit individual density PNGs.

## When to use

- Creating or redesigning the app icon / launcher icon / logo.
- Regenerating icon assets after a design tweak (all densities + Play Store).
- Adding a monochrome themed icon (Android 13+) or a store-listing preview.

## Design method (do this before drawing)

1. **Concept** — one idea, tied to the product. FlowSense (this app) tracks the
   *flow* of money with AI. The current mark is a rising gradient flow-arrow
   (growth / uptrend) plus an AI *sense* sparkle. Pick a single metaphor; a
   busy icon fails at 48dp.
2. **Silhouette first** — the shape must be recognizable as a solid black
   fill. If the silhouette is mush, the icon is mush.
3. **Bold and simple** — one focal mark, generous stroke weight, few colors.
   Modern launcher icons favour a bold shape on a rich gradient, not fine
   detail.
4. **Color** — a 2–3 stop gradient background and a contrasting foreground.
   This app's palette: deep navy `#0B1220` → teal `#0F3B39` → `#0D9488`
   background; emerald→cyan `#34D399 · #22D3EE · #67E8F9` foreground.
5. **Safe zone** — Android adaptive icons crop to a circle/squircle and the
   system animates parallax, so **keep all essential marks inside the centered
   66dp circle** (roughly x/y `27..81` in the 108 viewport). The outer ring is
   decorative background only.

## Android adaptive icon anatomy

An adaptive icon has three layers, each 108dp, with the inner 72dp guaranteed
visible:

- **background** — full-bleed, usually the gradient (`ic_launcher_background`).
- **foreground** — the mark on transparency (`ic_launcher_foreground`).
- **monochrome** — a single-color silhouette for themed icons, Android 13+
  (`ic_launcher_monochrome`); the system tints it, so draw it in one flat color.

`mipmap-anydpi-v26/ic_launcher.xml` binds the three layers. Older launchers
fall back to the raster PNGs in `mipmap-*dpi/`.

## Workflow

1. **Author the source SVGs** in `icon-source/` (viewBox `0 0 108 108`):
   - `ic_launcher_background.svg` — gradient rectangle.
   - `ic_launcher_foreground.svg` — the mark, transparent background.
   - `ic_launcher_full.svg` — the two composited (for previews / Play Store).
   Keep path data identical between the SVGs and the Android vector drawables
   so raster and vector renderings match exactly.
2. **Preview and iterate** — `python3 scripts/preview.py icon-source/ic_launcher_full.svg`
   writes a contact sheet showing full-bleed, squircle-masked, circle-masked,
   and tiny 48px/72px samples. Open it, judge it honestly (does it read at
   48px? is the silhouette clear? is the safe zone respected?), and refine the
   SVG before generating anything else.
3. **Author the Android vector drawables** by hand in
   `app/src/main/res/drawable/` and `mipmap-anydpi-v26/`, reusing the exact SVG
   path data. Gradients on fill/stroke use inline `aapt` attributes
   (`xmlns:aapt="http://schemas.android.com/aapt"`).
4. **Generate every raster asset**:
   `python3 scripts/render_icons.py` (see below).
5. **Verify** — re-open the previews and the generated `ic_launcher.png` at
   xxxhdpi; confirm the adaptive XML references the intended drawables.

## Generating raster assets

`scripts/render_icons.py` renders, from the three source SVGs, into the app:

- `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` — composited, squircle-masked
  (48/72/96/144/192 px).
- `.../ic_launcher_round.png` — composited, circle-masked.
- `.../ic_launcher_foreground.png` / `ic_launcher_background.png` — raw layers.
- `app/src/main/ic_launcher-playstore.png` — 512×512 full-bleed square.
- `store-listing/icon_preview_512.png` — 512×512 squircle preview.

Run it from the repo root. Paths and density tables live at the top of the
script — adjust them if the project layout differs. It depends on `cairosvg`
and `Pillow` (`pip install cairosvg Pillow`).

## Checklist before committing

- [ ] Reads clearly at 48px (check the contact-sheet thumbnails).
- [ ] Silhouette is a clean, recognizable shape.
- [ ] Essential marks inside the 66dp safe zone.
- [ ] All mipmap densities regenerated + Play Store 512 + store preview.
- [ ] Adaptive `ic_launcher.xml` points at the right background/foreground/monochrome.
- [ ] Monochrome themed icon is a single flat color.
- [ ] Vector drawable path data matches the SVG source.
