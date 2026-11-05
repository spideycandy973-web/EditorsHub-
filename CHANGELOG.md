# Changelog

## 4.3.0 — APK features and editor rebuild (2026-11-06)

- **Fixed the editor canvas.** All 20 reference editor JPGs show a 9:16
  portrait canvas at x=80–349/y=113–593; the app had a small 16:9 landscape
  rectangle centred in a 718px stage. Rebuilt the editor bands from measured
  pixels: status 0–34, top bar 69–112 (#262628), stage 112–592, transport
  590–636, ruler/timeline 636–728, property pane 728–936, 64/56px rails.
  Default composition is now 9:16. `docs/VISUAL_AUDIT.md` has the deltas;
  blank-editor mean luma difference is 3.47 %.
- **Used the REA/Ghidra/APKTool evidence** (`apk-analysis/`: 72 activities,
  37,206 classes, 43 Ghidra functions, 3,310 strings, 250 layout trees) to
  build [`docs/FEATURE_MATRIX.md`](FEATURE_MATRIX.md) and implement the missing
  user-facing features.
- **Camera**: Active Camera View (renders through the camera POV), global Force
  Blur, FOV/zoom/pan readouts, camera-X frustum, magenta clip, hint tooltip
  that opens Camera Options.
- **Border & Shadow**: Add Border empty state, 5 real stroke dash styles,
  Start/End trim, colour toggle, Shadow Size/Alpha/Position X/Y — all wired to
  the canvas renderer via new animatable props.
- **Blending & Opacity**: Contrast / Difference / Color / Mask accordion groups
  with working blend-mode thumbnails.
- **Project Settings**: ratio chips (real composition resize), 720p–2160p,
  12–240 fps, background, X close, and a live **Total Edit Time** counter.
- **Nine APK-derived screens**: Colour Picker, Font Browser, Media Browser,
  Audio Browser, Save To Storage, Open Source Libraries, Tutorial, Preset
  Preview, and a **brand editor**.
- **In-app identity customisation**: wordmark, app name, domain, website, About
  text and three palette colours persist in localStorage and apply live via a
  `BRAND` Proxy + change event. No third-party name/artwork is baked in.
- `tools/smoke.mjs` extended to **94 checks** (all passing, zero console
  errors), including live brand changes, camera flags, stroke props, blend
  thumbnails and every new screen.
- `versionCode 16`, `versionName 4.3.0`, PWA cache `editorshub-v16`.

## 4.1.0 — measured pixel alignment (2026-11-05)

- Added [`tools/visual-audit.py`](../tools/visual-audit.py) and
  [`docs/VISUAL_AUDIT.md`](VISUAL_AUDIT.md): exact card/button/sheet row bounds
  and palette samples from all 28 reference JPGs on the 430 × 956 canvas, with
  a `--mine` mode that reports the deltas against headless screenshots.
- **Global `box-sizing: border-box`** — without it every explicitly-sized
  padded box was 8–38 px too tall. This was the main cause of the layout drift.
- Bundled `@fontsource/roboto` (400/500/700/900) so text metrics match the
  reference instead of falling back to DejaVu Sans.
- Restored the exact palette measured from the stills: `#121214`, `#1c1c1e`,
  `#262628`, `#d8dcff`, `#01ffa7`.
- Launcher card/button/nav bounds now match within 0–1 px; settings −2/+3 px,
  create sheet 0/−1 px, add sheet −3/−1 px. Whole-screen mean luma difference:
  2.58 % launcher, 4.27 % settings, 7.51 % create, 5.49 % add.
- Buttons changed from pills to 12–13 px rounded rectangles; flat 48 px teal
  FAB/nav-plus with no glow; dark-track/teal-knob toggle.
- Settings reduced to the reference's four rows; the still-functional Device
  chrome control moved to the About menu.
- Create sheet: default ratio **16:9** (1920 × 1080), transparent 48 px ratio
  chips with corner brackets, circled clear-name button, two-line Composition
  Size label.
- `tools/smoke.mjs` extended to **67 checks** including measured launcher
  geometry and the Roboto/palette tokens; all passing.
- `versionCode 15`, `versionName 4.1.0`, PWA cache `editorshub-v15`.

## 4.0.0 — fully new (2026-11-05)

- **Everything from the previous app removed and rebuilt.** The old UI tree was
  moved out and replaced by a new `src/app/` application layer; `src/um.css`
  became `src/app.css`; `src/theme/brand.ts` became `src/app/brand.ts`. All stale
  screenshots and source archives were deleted.
- **New full-screen flows** (`src/app/Flows.tsx`): template editor with real
  `renderStill()` preview and per-layer media replacement through a device file
  picker; the "Are you done replacing media?" gate; the missing-fonts report
  (real `document.fonts.check` detection on imported projects); and a real
  export preview with composition stats.
- **Keyframes Required hint** in the easing graph, shown only when fewer than
  two keyframes actually exist.
- **44 real effects**, 32 Effect Browser categories, seven property panels,
  five shape pages, six real export encoders including a hand-written GIF89a
  encoder — carried over into the new layer, none removed.
- `src/app/brand.ts` remains the single customisation point.
- `versionCode 14`, `versionName 4.0.0`, PWA cache `editorshub-v14`.
