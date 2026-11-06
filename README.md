# EditorsHub v4.4.0

A **real, working mobile video & motion-graphics editor** that runs entirely in
the browser, packaged as an Android app (`com.editorshub`). Layers, keyframes,
47 effects, blending, masks, media, XML project import/export, six real export
encoders — all backed by a canvas compositor that draws every frame for real.

**v4.4.0 — screenshot pass and reliable APK updates.** An installed APK could
keep showing the previous build because v4.3.0 registered a cache-first service
worker inside the WebView; native builds no longer register it, stale caches are
cleared at launch, and **About → Clear cached UI & reload** performs a real
reset. The launcher wordmark/domain, card glyphs, effect browser, guide card,
presets dialog, create sheet, shape picker and FAB states were rebuilt from the
28 reference JPGs. Three new real effects (Fade In/Out, Crystal, Hot Color)
back the quick-pick tiles. See [`docs/VISUAL_AUDIT.md`](docs/VISUAL_AUDIT.md)
for measured before/after differences. Headless checks: **95/95**, zero console
errors.

**v4.3.0 — APK features + editor rebuild.** The editor canvas was wrong (a
small landscape rectangle); it is now the reference's **9:16 portrait canvas at
x=80/y=113** with the exact top bar, transport, timeline and property-pane
bands. Added the features recovered from the 72 APK activities and the reference
JPGs: Active Camera View + Force Blur, Stroke styles/Start-End trim, Add Border,
blend accordion groups, Total Edit Time, colour/font/media/audio browsers, save
to storage, open-source libraries, tutorial, preset preview, and a **fully
persisted in-app brand editor** (wordmark, name, domain, colours — set whatever
you want). See [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md). Headless
checks: **94/94**, zero console errors.

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
node tools/smoke.mjs --shots          # 95 headless UI checks
node tools/capture-states.mjs           # 430x956 comparison frames
```

`tools/smoke.mjs` needs optional tooling:
`npm install --no-save --package-lock=false @sparticuz/chromium playwright-core`
then `node tools/frames-libs.mjs` and run against `npm run preview`.

## Android

`EditorsHub-Motion.apk` (Capacitor, installable) is committed at the repository
root and attached to `apk-latest`, `v4.4.0` and `editorshub-latest`, together
with `EditorsHub-debug.apk`, `EditorsHub-release-unsigned.apk`,
`EditorsHub-release.aab` and `EditorsHub-source-v4.4.0.zip`. Release APK/AAB are
unsigned by design; see `EditorsHub/docs/RELEASE_SIGNING.md` for the keystore
workflow. All Android builds run in GitHub Actions.

## Boundary

EditorsHub is an **original, clean-room implementation**. Its layout, colours,
interaction and behaviour are matched to the reference with original TypeScript,
original SVG glyphs and original CSS artwork. It contains no code, bitmap,
vector, font or audio assets from any other application. The on-screen wordmark
and subtitle are plain text labels taken from the reference screenshots the user
supplied; they are editable in-app (one-tap reference / EditorsHub presets), and
the Android package remains `com.editorshub`. All assets and code here are yours
to customise.
