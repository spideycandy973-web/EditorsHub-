# EditorsHub v4.1.0 — measured pixel alignment

Package `com.editorshub`, application label `EditorsHub`; all code and assets are original.

## What's new in 4.1.0

- **Measured, not guessed.** `tools/visual-audit.py` samples all 28 reference
  JPGs on a 430 × 956 canvas and reports exact card/button/sheet bounds and
  palette values; `docs/VISUAL_AUDIT.md` records the deltas.
- **Global `box-sizing: border-box`** removed 8–38 px of layout inflation from
  every padded box. Bundled Roboto (400/500/700/900) so text metrics match.
- **Exact palette** from the stills: `#121214`, `#1c1c1e`, `#262628`,
  `#d8dcff`, `#01ffa7`.
- Launcher card/button/nav bounds within 0–1 px of the reference; settings
  −2/+3 px, create sheet 0/−1 px, add sheet −3/−1 px. Mean whole-screen luma
  difference 2.58 % / 4.27 % / 7.51 % / 5.49 %.
- Rounded rectangles instead of pills, flat teal FAB, dark-track toggle,
  four-row Settings, 16:9 default create ratio with 1920 × 1080 composition,
  circled clear-name button, two-line Composition Size.
- `versionCode 15`, `versionName 4.1.0`, PWA cache `editorshub-v15`.
  `npm run typecheck`, `npm run build`, `node tools/verify-gif.mjs` (7/7) and
  `node tools/smoke.mjs --shots` (**67/67**, zero console errors) all pass.

## Install

`EditorsHub-Motion.apk` (Capacitor, `com.editorshub`) is installable and is
attached to `apk-latest`, `v4.1.0` and `editorshub-latest`. Release APK/AAB are
unsigned by design (see `EditorsHub/docs/RELEASE_SIGNING.md`).
`EditorsHub-source-v4.1.0.zip` contains the complete source tree.

---

# EditorsHub v4.0.0 — fully new

Package `com.editorshub`, application label `EditorsHub`; all code and assets are original.

## What's new in 4.0.0

- **Fully new application layer.** The previous UI tree, stylesheet and brand
  module were removed and rebuilt under `src/app/`, `src/app.css` and
  `src/app/brand.ts`. All stale screenshots and source archives were deleted.
- **Template editor / replace media.** A real render of the current composition,
  one slot per media layer, and a device file picker that genuinely swaps the
  layer's asset. Finishing requires the "Are you done replacing media?" gate.
- **Missing Fonts report.** Imported projects are checked with
  `document.fonts.check`; missing families are listed honestly with an upload
  instruction instead of being silently substituted.
- **Export Preview.** A real frame render plus resolution / fps / duration /
  layer count before exporting.
- **Keyframes Required** hint on the easing graph when fewer than two keyframes
  exist.
- Everything else stays real: 44 canvas effects, 32 Effect Browser categories,
  seven property panels, five shape pages, XML import, and six export encoders
  (video, project package, PNG, PNG sequence, XML, GIF89a).
- `versionCode 14`, `versionName 4.0.0`, PWA cache `editorshub-v14`.
  `npm run typecheck`, `npm run build`, `node tools/verify-gif.mjs` and
  `node tools/smoke.mjs --shots` all pass.

## Install

`EditorsHub-Motion.apk` (Capacitor, `com.editorshub`) is installable and is
attached to `apk-latest`, `v4.0.0` and `editorshub-latest`. Release APK/AAB are
unsigned by design (see `EditorsHub/docs/RELEASE_SIGNING.md`).
`EditorsHub-source-v4.0.0.zip` contains the complete source tree.
