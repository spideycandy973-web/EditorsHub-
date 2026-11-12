# Changelog

## 4.5.4 — RomKit presentation re-skin (2026-11-11)

- Re-skinned the existing native Compose presentation with RomKit ink, panel, hairline, Ember/Cyan semantic accents, typography and motion tokens.
- Preserved the app's existing screens, controls, routes, data and behavior; no business logic or capability changes are intended.
- Retained thumbnail generation and keyframe workflows.
- Signed native Android APK built from the active branch and verified by the release workflow.

## 4.5.1 — v4.5.1 design baseline restored (2026-11-11)

- Restored the exact v4.5.1 web/Capacitor UI source under `src/` as the design source of truth; removed the later APK-measurement CSS token changes.
- Realigned the native Kotlin/Compose theme (`Design.kt`) to the v4.5.1 `--um-*` tokens: `#121214`, `#1c1c1e`, `#262628`, `#d8dcff`, `#01ffa7`.
- Set the native app `versionName` to `4.5.1`; version code 21 is retained for monotonic installs. Updated `AppVersionTest`.
- Deleted the `v4.5.3` release/tag and made the exact linked `v4.5.1` release **Latest**, named exactly `v4.5.1`, with only `EditorsHub-Motion.apk`.
- Verified by Android CI run `43114300889` (all 18 instrumentation tests and nine state captures) and import run `43116353207` (`native-*-v4.5.1.png`). No new APK was built or published; the Latest asset remains the historical Capacitor APK. Exact reference parity is not claimed.

## 4.5.2 — first GamersHub-style signed release (2026-11-10)

- Bumped the native Android app to version 4.5.2 / version code 21.
- Added a generated, committed PKCS12 release keystore at `keystore/editorshub.p12`; no user-supplied key or repository secret is required.
- Replaced secret-based release signing with `.github/workflows/release.yml`, which builds a signed release APK from source on `v*` tags.
- Publishes a stable **Latest** release named exactly the tag, with one `EditorsHub-vX.Y.Z.apk` asset; no rolling `apk-latest` update, source archive, or checksum asset.
- Verified the committed keystore with `keytool` and the produced APK with `apksigner`; a no-publish dry run passed before tagging.
- This APK is signed with a new key. Android may refuse to update an existing installation signed with a different key; back up projects and uninstall the previous build first.
- Exact pixel-clone parity to the supplied reference is not claimed.

## 4.4.0 — native Android release (2026-11-09)

- Corrected the native app build metadata to version 4.4.0 / version code 20 after the rejected 4.6.0 label.
- Added generated `APP_VERSION_NAME` / `APP_VERSION_CODE` fields, made Settings → About display them, and added `AppVersionTest`.
- Aligned the Android CI source archive and optional debug-prerelease labels with 4.4.0.
- Rebuilt the debug APK from the same verified native editor implementation; project/effects thumbnails and keyframe editing remain functional.
- Full Android CI, 14 emulator instrumentation tests, and launch smoke passed in [42677408773](https://github.com/spideycandy973-web/EditorsHub-/actions/runs/42677408773). This is a debug-signed GitHub release build, not a production-signed Play Store artifact and not a claim of exact reference parity.

## 4.5.0 — measured Ultra Motion editor alignment (2026-11-06)

- **Rechecked the screenshots and RE evidence instead of guessing.** Fresh
  APK/REA/Ghidra measurements are in `apk-analysis/`; the clean-room app now
  uses the decoded surface palette and the visible reference dimensions.
- **Fixed the central shape mismatch:** the default `Rounded Rectangle 1` is
  centered at 200 × 200 composition units, filled `#ba7e38`, radius matched to
  the 50 px rendered square, and has no painted white stroke. The white edge in
  the selected screenshot is the real selection outline; deselecting the layer
  removes it, as it does in the reference.
- **Rebuilt the shape picker:** first page is the reference 18-shape, six-column
  grid; the picker has three pages, reference-sized controls and path-point
  previews. The rest of the original shape/vector library remains available.
- **Aligned the editor/timeline details:** preview tools are hidden until their
  real toggle is used; reference property/detail pane geometry and the rounded
  selection handles are preserved; default layer duration is four seconds and
  the ruler is 53 px/sec so a new shape's clip matches the screenshot width.
- **Launcher and editor styling:** header coordinates and decoded dark/accent
  resource colours were corrected; the native/PWA version is 4.5.0 / code 18,
  with web shell cache `editorshub-v19`.
- Added a regression check for the default shape's dimensions, fill, stroke,
  duration and rail behavior. `npm run typecheck`, `npm run build`, GIF checks,
  and the headless smoke suite are recorded in the release notes after the final
  verification run.
- Clean-room boundary remains unchanged: no decompiled code, proprietary
  assets, fonts, or re-signed reference APK are included; package remains
  `com.editorshub` and branding is still editable in-app.

## 4.4.0 — screenshot pass and reliable APK updates (web/Capacitor, historical, 2026-11-06)

- **Fixed the reason an installed APK can look unchanged after an update.**
  v4.3.0 registered a cache-first service worker inside the Capacitor WebView.
  Its cached `index.html` pins the previous build's hashed JS/CSS, so a new APK
  could keep drawing the old UI. Native builds no longer register the worker;
  existing registrations and caches are cleared on launch. Hosted-web builds use
  network-first navigation with a versioned cache (`editorshub-v18`). Added
  **About → Clear cached UI & reload** and a **Clear cached UI** button in the
  customise sheet, both wired to a real `window.__clearUiCache()` reset.
- **On-screen identity now matches the reference stills.** Default wordmark
  `ULTRA MOTION` / subtitle `ultramotionofficial.com`, with one-tap
  `Use reference look` and `EditorsHub identity` presets. Package stays
  `com.editorshub`; every field remains editable in-app.
- **Launcher glyphs redrawn** as original SVGs from the reference shapes: a
  stack of picture frames with a gold upload arrow, and a hexagon enclosing an
  isometric cube. No bitmap assets are copied.
- **Effect Browser rebuilt** from screenshot 23/24: five hand-drawn SVG quick
  tiles (Chroma Key, Directional Blur, Fade In/Out, Crystal, Hot Color), the
  teal separator dot, muted dark category plates, rounded panel top and the
  77px status gap. Mean per-pixel difference against that frame dropped from
  44.26/255 to 31.26/255.
- **Three new real effects** so those quick-pick labels are honest controls:
  `fadeInOut` (opacity ramp across the layer duration), `crystal` (mirrored
  prism facets) and `hotColor` (luminance-to-warm-gradient map). All three run
  through the canvas compositor and are exercised by the smoke suite.
- **Guide card** is now a half-screen artwork panel with the title/description
  over it and a fixed `Standard Settings` bar, as in screenshot 27.
- **Presets dialog**: wider card, centred close, taller transport illustration,
  and a real **Import elements** action that validates XML through the parser
  and stores it in the element library.
- **Create sheet**: the Background row is now the single swatch + label +
  chevron pill from screenshot 4, with a real colour input and real options.
- **Shape picker**: 5-column grid, 15 shapes on the first page (three dots in
  the reference), larger glyphs.
- **Editor FAB** uses a real X when a panel is open; the launcher nav plus now
  closes the open sheet instead of stacking another one.
- **Settings** brand badge uses the diamond glyph; status bar shows measured
  HTTP throughput from Resource Timing (never an invented number).
- `tools/capture-states.mjs` reproduces all audit frames at 430x956 with
  settled animations; `docs/screenshots/*-v4.4.0.png` regenerated.
- 95/95 headless checks pass with zero console errors/warnings.

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
