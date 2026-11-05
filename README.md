# EditorsHub v4.1.0

A **real, working mobile video & motion-graphics editor** that runs entirely in
the browser, packaged as an Android app (`com.editorshub`). Layers, keyframes,
44 effects, blending, masks, media, XML project import/export, six real export
encoders — all backed by a canvas compositor that draws every frame for real.

**v4.1.0 measured pixel alignment.** The 28 reference JPGs were measured at
430 × 956 and the launcher, settings, create sheet and add sheet now match
their card/button/nav bounds within 0–3 px; the exact palette and Roboto
metrics were restored. See [`docs/VISUAL_AUDIT.md`](docs/VISUAL_AUDIT.md) and
[`tools/visual-audit.py`](tools/visual-audit.py). Headless checks: **67/67**,
zero console errors.

**This is a fully new application layer.** The previous UI tree was removed and
rebuilt under `src/app/`; the old screenshots, source archives and legacy
components were deleted. `src/editor/` is the real engine.

## Customise it

One file: [`src/app/brand.ts`](src/app/brand.ts) holds the app name, wordmark,
domain, Android package, version, links and the complete palette. `applyBrand()`
pushes the palette into CSS custom properties when the app starts. Change that
file first, then anything else.

## Screen map

[`docs/MASTER_CLONE.md`](docs/MASTER_CLONE.md) maps all 30 screens and panels to
their implementation, including the v4.0.0 flows: template editor / replace
media, the "Are you done replacing media?" gate, the missing-fonts report, the
export preview and the keyframes-required hint.

The reverse-engineering evidence used to match the reference geometry is in
[`docs/APK_AUDIT.md`](docs/APK_AUDIT.md) and
[`docs/VIDEO_AUDIT.md`](docs/VIDEO_AUDIT.md).

## Build & verify

```sh
npm ci
npm run typecheck
npm run build
node tools/verify-gif.mjs              # 7/7 GIF89a encoder checks
node tools/smoke.mjs --shots          # 58+ headless UI checks
```

`tools/smoke.mjs` needs optional tooling:
`npm install --no-save --package-lock=false @sparticuz/chromium playwright-core`
then `node tools/frames-libs.mjs` and run against `npm run preview`.

## Android

`EditorsHub-Motion.apk` (Capacitor, installable) is committed at the repository
root and attached to `apk-latest`, `v4.1.0` and `editorshub-latest`, together
with `EditorsHub-debug.apk`, `EditorsHub-release-unsigned.apk`,
`EditorsHub-release.aab` and `EditorsHub-source-v4.1.0.zip`. Release APK/AAB are
unsigned by design; see `EditorsHub/docs/RELEASE_SIGNING.md` for the keystore
workflow. All Android builds run in GitHub Actions.

## Boundary

EditorsHub is an **original, clean-room implementation**. Its layout, colours,
interaction and behaviour are matched to the reference with original TypeScript,
original SVG glyphs and original CSS artwork. It contains no code, bitmap,
vector, font or audio assets from any other application, and no third-party
trademark. All assets and code here are yours to customise.
