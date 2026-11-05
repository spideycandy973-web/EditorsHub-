# Measured visual audit — v4.1.0

This pass stopped guessing and **measured** the 28 reference JPGs
(1220 × 2712) against headless screenshots of the app at the same aspect ratio
(430 × 956). The measuring tool is
[`tools/visual-audit.py`](../tools/visual-audit.py); it reports card/lavender
row bounds and exact palette samples. Run it with
`python3 tools/visual-audit.py --mine /tmp/audit` after capturing your own
screens at 430 × 956.

## Exact palette sampled from the stills

| Token | Value | Sample (R,G,B) |
| --- | --- | --- |
| `--um-home` | `#121214` | 18,18,20 |
| `--um-card` | `#1c1c1e` | 28,28,30 |
| `--um-card-2` / `--um-input` | `#262628` | 38,38,40 |
| `--um-lav` | `#d8dcff` | 216,220,255 |
| `--um-teal` | `#01ffa7` | 1,255,167 |

These are the values now in `src/app.css` and `src/app/brand.ts`. (v3.5.0 had
used the `resources.arsc` tokens, which differ by one channel value; the JPGs
are what a user actually sees, so they are authoritative here.)

## Geometry (430 × 956 canvas)

| Screen | Element | Reference rows | v4.1.0 rows | Δ |
| --- | --- | --- | --- | --- |
| Launcher | Upload XML card | 129–226 | 129–225 | 0 / −1 px |
| Launcher | Import package card | 240–475 | 240–474 | 0 / −1 px |
| Launcher | Upload button | 157–198 | 157–197 | 0 / −1 px |
| Launcher | Import button | 367–407 | 367–406 | 0 / −1 px |
| Launcher | Nav pill | 855–921 | 855–920 | 0 / −1 px |
| Settings | Card | 137–745 | 135–748 | −2 / +3 px |
| Create | Sheet | 394–840 | 394–839 | 0 / −1 px |
| Add | Sheet | 542–861 | 539–860 | −3 / −1 px |

## Whole-screen luma difference (downscaled 215 × 478, 0–255)

| Screen | Mean abs diff | % |
| --- | --- | --- |
| Launcher | 6.58 | 2.58 % |
| Settings | 10.88 | 4.27 % |
| Create sheet | 19.15 | 7.51 % |
| Add sheet | 14.01 | 5.49 % |

The residual is text content (the app name and domain), original icon artwork,
and the Android status-strip values, which are real device readings rather than
reproduced pixels.

## What was changed to achieve it

- Global `box-sizing: border-box`: without it every explicitly-sized padded box
  was 8–38 px too tall. This was the single biggest cause of the drift.
- Typography: bundled **Roboto** (`@fontsource/roboto` weights 400/500/700/900)
  so text metrics match the reference instead of falling back to DejaVu Sans.
- Launcher header/body/card spacing, card radius 18 px, and all button radii
  reduced from pills to 12–13 px rounded rectangles.
- Flat teal FAB/nav-plus (removed the green glow), 48 px disc.
- Toggle changed to dark track + teal knob; the extra "Device chrome" settings
  row was removed (it remains a real control in the About menu) so Settings has
  the reference's four rows.
- Create sheet: default ratio changed to **16:9** (composition 1920 × 1080),
  transparent 48 px ratio chips with corner brackets, circled clear-name
  button, and a two-line "Composition Size" sub-label.
- Per-sheet bottom offsets for the create and add sheets.

All of this is original CSS/TypeScript. No image, font, icon or code from the
reference application was copied into the build; the JPGs are used only as
measurement input.
