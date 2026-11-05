# Master-base screen & feature map (v3.6.0)

This document is the audit of what the app actually implements. It maps every
screen, panel and behaviour observed in the reference recording (747 distinct UI
states), the 28 attached screenshots and the decoded `UM Clone_8.0.1.apk`
resource/layout tables to a concrete file in this repository.

**Boundary.** This is an original, clean-room implementation. It reproduces
layout, colour, typography, interaction and behaviour with original code and
original SVG/CSS artwork. It does **not** contain the reference application's
DEX bytecode, decompiled source, bitmap assets, fonts, audio or trademark.
Everything you may want to change first lives in one file:
[`src/theme/brand.ts`](../src/theme/brand.ts) — name, wordmark, domain, package,
version, links and palette.

## Screen map

| # | Reference screen | Implementation | Status |
| --- | --- | --- | --- |
| 1 | Cold-launch swirl splash | `src/um/Splash.tsx` | real timed animation, reduced-motion path |
| 2 | Launcher (wordmark, gear, avatar, Upload XML, Import Project Package, QR) | `src/um/Home.tsx` | real file/URL/QR import |
| 3 | Settings (Low Quality Preview, Show Off Timeline, Remember Add page, Default Layer Duration, Audio Resampling, social rows, About) | `src/um/Home.tsx` | all controls persisted and functional |
| 4 | About (wordmark, domain, device/build info) | `src/um/AboutPage.tsx` | live `navigator`/screen/storage values only |
| 5 | Projects / Your Templates / Elements | `src/um/Home.tsx` | sort, date groups, search, select-all/export/duplicate/delete |
| 6 | Elements empty state | `src/um/Home.tsx` | real create-element action |
| 7 | Create Project/Element (ratio chips, custom ratio, resolution, fps, background) | `src/um/Overlays.tsx` | creates a real project |
| 8 | Editor top bar, canvas, magnifier, right preview/zoom rail, selection handles | `src/um/Editor.tsx` | real hit-testing, drag, scale, zoom |
| 9 | Transport, ruler, playhead, timeline clips, eye, keyframe, trim | `src/um/Editor.tsx` | real playback and edit controls |
| 10 | Add sheet (Media, Text, Shape, Audio, Freehand/Vector Drawing, Element/Project, Template, Camera, Null, Empty Group) | `src/um/EditorSheets.tsx` | every tile creates a real layer or opens a real picker |
| 11 | Media / audio / font picker | `src/um/EditorSheets.tsx`, `src/editor/media.ts` | real file import and streaming |
| 12 | Shape picker (5 pages, 18 built-ins + extras + polygon/star variants + 18 original vector silhouettes) | `src/um/EditorSheets.tsx`, `src/um/ShapeGlyph.tsx` | all shapes render through `Path2D` |
| 13 | Vector pen / Edit Points | `src/um/EditorSheets.tsx` | real point placement and path generation |
| 14 | Text tool (font, pt size, colour palette, live on-canvas editing) | `src/editor/renderer.ts`, property panels | real text rendering |
| 15 | Move & Transform (X/Y/Z pad, width/height, skew, rotation dial, easing graph) | `src/um/PropertySheets.tsx` | real keyframes and transforms |
| 16 | Border & Shadow (Add Border, Stroke, Start/End, INSIDE/CENTER/OUTSIDE, Shadow size/alpha/position) | `src/um/PropertySheets.tsx` | real style changes |
| 17 | Blending & Opacity (opacity slider, blend groups, Mask) | `src/um/PropertySheets.tsx` | real `globalCompositeOperation` |
| 18 | Edit Shape (Size/Radius rulers) | `src/um/PropertySheets.tsx` | real shape geometry |
| 19 | Color & Fill (solid/linear/radial, palette) | `src/um/PropertySheets.tsx` | real gradients |
| 20 | Effects stack, Brightness/Contrast, + Add Effect, effect keyframes | `src/um/PropertySheets.tsx` | 44 real effects |
| 21 | **Effect Browser** (32 categories, quick picks, subcategory grids, Guide card, Standard Settings, star) | `src/um/EffectBrowser.tsx` | every tile applies a working effect |
| 22 | Presets dialog (`No presets... yet!`, Discover Presets, Close) | `src/um/PresetsDialog.tsx` | real localStorage presets + motion gallery |
| 23 | Camera Options (Camera View, FOV, Force Blur) | `src/um/PropertySheets.tsx` | real camera layer |
| 24 | Freehand Drawing (palette, brush sliders, pen tools) | `src/um/EditorSheets.tsx` | real strokes |
| 25 | Element/Project and Template browser (View All, Discover Templates, My Group) | `src/um/EditorSheets.tsx`, `src/um/Overlays.tsx` | real saved elements/templates |
| 26 | Overflow menu (Select All Layers, Duplicate, Save Preset, Copy incl. style, Paste Layer/Style, Split, Keyframe, Delete) | `src/um/EditorSheets.tsx` | all real |
| 27 | Project Settings (ratios, resolution 180p–2160p, fps 12–240, background, total edit time) | `src/um/Overlays.tsx` | real project mutation |
| 28 | Export & Share (Video, Project Package, PNG, Image Sequence, GIF, XML, template toggle, video settings) | `src/um/Overlays.tsx`, `src/editor/export.ts`, `src/editor/gif.ts` | six real encoders |
| 29 | Template editor / Replace media / delete confirmation / Missing-fonts-style report | `src/um/Overlays.tsx`, `src/editor/xml.ts` | real replace/import report |
| 30 | Selection mode with bottom action bar and delete confirmation | `src/um/Home.tsx` | real |

## Engine

- `src/editor/store.ts` — undo/redo, batching, layer/clip/keyframe/effect mutations.
- `src/editor/renderer.ts` — canvas compositor: shapes, text, media, gradients,
  masks, camera, blending, **44 effects** (21 original + 23 added in v3.6.0),
  per-frame pixel operations and procedural overlays.
- `src/editor/types.ts` — `EFFECT_DEFS` (44), shapes, props, blend modes.
- `src/editor/xml.ts` — portable project XML import/export with honest reports.
- `src/editor/export.ts`, `src/editor/gif.ts` — MediaRecorder video, PNG, PNG ZIP,
  XML, project ZIP and a hand-written GIF89a/LZW encoder.
- `src/editor/media.ts`, `src/editor/storage.ts` — streaming media and local projects.

### The 23 v3.6.0 effects (all real, all rendered)

`posterize`, `threshold`, `solarize`, `duotone`, `gradientMap`, `levels`,
`colorBalance`, `emboss`, `edgeDetect`, `halftone`, `scanlines`, `glitchBlocks`,
`ripple`, `bulge`, `twirl`, `mirror`, `kaleidoscope`, `motionBlur`,
`directionalBlur`, `lensFlare`, `starfield`, `bokeh`, `rain`.

They are distributed across all 32 Effect Browser categories, so every category
now exposes 6–12 working effects instead of 4.

## Customising the master base

1. Edit `src/theme/brand.ts` (name, wordmark, domain, links, palette, version).
2. If changing `packageName`, also update `EditorsHub/app/build.gradle.kts`,
   `android/app/build.gradle` and `capacitor.config.json`.
3. Replace `public/` icons and the launcher wordmark artwork if desired.
4. `npm run typecheck && npm run build && node tools/smoke.mjs --shots`.

## Verification

`node tools/smoke.mjs --shots` → **58/58** headless checks, zero console
errors/warnings. `node tools/verify-gif.mjs` → 7/7. Native and Capacitor CI build
the installable APK, release APK/AAB and source ZIP for `v3.6.0`.
