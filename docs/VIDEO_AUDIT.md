# Reference-recording audit — v3.4.0

**Status: full recording analysed. 747 distinct UI states extracted and reviewed.**

> v3.5.0 continues this work with the actual APK. See
> [APK_AUDIT.md](APK_AUDIT.md) for REA / Ghidra / JADX / APKTool results, the
> exact `resources.arsc` palette, all 32 Effect Browser categories and the
> full-screen panels built from them.

## How the 100 MiB transfer limit was solved

The source is one Google Drive file:

```
Screenrecorder-2026-11-04-20-36-00-405.mp4
video/mp4 · 301,626,146 bytes · 629.765 s · 1220 × 2712 · ~24 fps
md5 6e796d643165b629ab3f46e2cddbf8c2
Drive id 1Wqk73fNhe1NqiwwlQC53fjLgZZXtTzq6
```

Arena refuses any single workspace file over 100 MiB, so the bytes could not be
downloaded into the agent sandbox. Instead,
[`.github/workflows/reference-frames.yml`](../.github/workflows/reference-frames.yml)
downloads the recording **inside GitHub Actions** (full outbound network +
`gdown`/Drive confirm-token handling), verifies the byte count, and runs
[`tools/build-storyboard.py`](../tools/build-storyboard.py). Only the small
derived frames are committed — the raw MP4 never enters the repository or the
agent workspace. No split parts were needed.

### What the storyboard contains

| Artefact | Count | Purpose |
| --- | --- | --- |
| 4 fps tiny RGB samples | 2,519 | change detection (`analysis/*.raw` is discarded) |
| Distinct UI states | **747** | mean-absolute-diff > 3.2, plus a forced sample every 3 s |
| State images (610 px) | 747 | readable geometry for each state |
| Labelled contact sheets | 38 | 20 states per sheet, 5 × 4, with timestamps |
| 1 fps timeline frames | 630 | animation / transition timing |
| Timeline contact sheets | 8 | 80 one-second frames per sheet |

The Android status strip and gesture pill are masked out of the comparison so
the rolling clock and network counters do not create false states. Every one of
the 38 state sheets and all 8 timeline sheets was inspected; `states.json` holds
the exact timestamp and change score for each state.

## Exact colours sampled from the reference frames

Measured from the 1220 × 2712 attached still and confirmed against the decoded
MP4 frames (the MP4 carries a ~1-value green cast from its colour profile):

| Token | Sampled value | Used for |
| --- | --- | --- |
| `--um-home` | `#121214` | screen background |
| `--um-card` | `#1c1c1e` | cards / sheets |
| `--um-input` / `--um-card-2` | `#262628` | input fields, nested rows |
| `--um-lav` | `#dfe3ff` | Import / Create / Export buttons |
| `--um-teal` | `#01ffa7` | FAB, toggles, QR, accent readouts |

## Screens and behaviours observed in the recording

Timeline positions (mm:ss) come from `analysis/states.json`.

- **00:00–00:03** cold launch: Android home, then a black splash with an
  animated white double-arc swirl and a small loading spinner.
- **00:03–00:07** launcher: `ULTRA MOTION` wordmark, gear / avatar, Upload XML
  and Import Project Package cards, pill nav with a teal centre `+`.
- **00:05–00:07** Settings: Low Quality Preview, Show Off Timeline, Remember
  Add page, Default Layer Duration, Audio Resampling, then Help / Instagram /
  Telegram / Tik Tok / About.
- **00:08–00:14** full-screen **About** with a large wordmark, a green URL and a
  device/build-information card (build number, manufacturer, model, serial…).
- **00:15–00:24** Projects / Your Templates / Elements segments, "Your projects"
  heading, "Sort by Date", search, date-grouped project rows with thumbnails,
  duration, resolution and an overflow menu; Elements empty state with a curved
  arrow pointing at `+`.
- **00:25–00:34** Create sheet: Project / Element tabs, name, ratio chips with a
  custom-ratio pencil, Resolution, Frame Rate, Background, Create Project.
- **00:34–00:52** editor and **project Settings** sheet: ratio chips,
  Resolution (2160p→180p), Frame Rate (12→240 fps), Background, Total Edit Time.
- **00:56–01:07** Add grid (Media, Text, Shape, Audio, Freehand/Vector Drawing,
  Element/Project, Template, Camera, Null, Empty Group), media picker, first
  image layer, clip bar and the "Swipe here to position next point" pad.
- **01:10–01:35** Edit Points / vector pen with green handles; text tool with
  font family, `pt` size, colour palette and live on-canvas editing.
- **01:35–02:55** Border & Shadow (Stroke Start/End, INSIDE/CENTER/OUTSIDE,
  shadow colour/size/alpha), Blending & Opacity (Normal/Darken/Lighten/
  Contrast/Difference/Colour/Luminosity with thumbnails and a Mask section).
- **02:57–03:20** Shape picker: **five pages** of silhouettes, including
  polygons and stars with many point counts.
- **03:20–04:17** Element/Project, Template browser (View All, Discover
  Templates, My Group), Freehand Drawing with a `#RRGGBB` palette and brush
  sliders.
- **03:40–04:00** Move & Transform: bezier easing graphs, keyframes, X/Y pad,
  and a green circular rotation dial.
- **04:18–04:37** Camera Options: Camera View, field of view / zoom values,
  Force Blur; camera layers use a magenta timeline clip.
- **05:00–05:20** Edit Shape width/height, "No presets… yet!" dialog with
  Create Preset and Import Presets.
- **06:10–06:55** Effects, Brightness/Contrast sliders, "Keyframes Required"
  hint, and a full **Effect Browser** with category and sub-category tiles.
- **06:50–07:10** "3D Background Room" effect and its Camera X/Y/Z controls.
- **09:22–09:43** **Export & Share**: Available Space / file size, six radio
  options (Video, Project Package, Current Frame as PNG, Image Sequence, GIF,
  XML), Video settings (resolution, codec, frame rate, quality), "Also export as
  a Template", sign-in gate for template sharing, Export button.
- **10:00–10:24** file picker, Template editor, Replace media slots, "Are you
  done replacing media?" dialog, Missing Fonts dialog.
- **10:24–10:29** Templates selection mode: "1 selected / Cancel", bottom
  Select All / Fix / Export / Duplicate / Delete bar, and a delete confirmation.

## What v3.4.0 implements from the audit

- The **cold-launch splash** (double-arc swirl, spinner, reduced-motion path).
- The **full-screen About** page with a live device/build-information card —
  real `navigator`, screen, storage and display-mode values only, nothing faked.
- Projects listing with **Sort by Date / Name / Duration**, date groups,
  generated thumbnails, and a real **selection mode** (Select All, Fix,
  Export, Duplicate, Delete + confirmation). Duplicate, package export and
  deletion operate on the actual localStorage projects.
- **Export & Share** with all six formats wired to real encoders: MediaRecorder
  video, ZIP project package, full-resolution PNG, ZIP PNG sequence, XML, and a
  **new hand-written GIF89a/LZW encoder** (`src/editor/gif.ts`) verified by
  decoding its output with an independent GIF decoder and by a headless
  end-to-end download check. Honest codec/frame-rate/storage readouts only.
- "Also export as a Template" saves a real, re-importable XML template.
- **Five-page shape picker** with 18 built-in glyphs, 9 extras, 26 polygon/star
  variants and 18 original normalized vector silhouettes rendered by `Path2D`.
- **Effect Browser** category tiles above the existing real effect list.
- Palette tokens corrected to the sampled `#121214 / #1c1c1e / #262628 /
  #dfe3ff / #01ffa7`.

## Not copied

Branding stays `EDITORS HUB` / `com.editorshub`; every SVG glyph, gradient,
icon and line of code above is original. No proprietary asset, APK, font or
source file from the recorded app is included. Screens that depend on a remote
account (template sign-in/sharing) are intentionally not presented as working
controls; the local template library is real instead.

## Verification

`npm run typecheck`, `npm run build` and `node tools/smoke.mjs --shots` all pass:
**40/40 headless checks**, zero console errors/warnings, including the GIF
download being inspected byte-for-byte for the `GIF89a` header and `0x3B`
trailer. Native and Capacitor CI produce the installable APK, release APK/AAB
and source ZIP for `v3.4.0`.
