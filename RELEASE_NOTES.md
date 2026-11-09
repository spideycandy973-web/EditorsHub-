# EditorsHub v4.4.0 — native Android release

Native Kotlin/Compose app, package `com.editorshub`, version 4.4.0 (version
code 20). This is the corrected release of the current native editor after the
mistakenly labeled 4.6.0 candidate. The APK manifest, About screen, CI source
archive, and release tag all report 4.4.0. It is a debug-signed CI build, not a
production-signed Play Store artifact.

## v4.4.0 verification

- The rebuilt APK was verified in GitHub Actions run [42677408773](https://github.com/spideycandy973-web/EditorsHub-/actions/runs/42677408773):
  unit tests, 14 emulator instrumentation tests, packaging, and emulator
  install/launch smoke checks passed on release commit `97702d00b71d5fc7b3bad716286477ea356bacd7`.
- A new unit test asserts `APP_VERSION_NAME = 4.4.0` and
  `APP_VERSION_CODE = 20`.
- The earlier successful native regression run 42642800416 covered the
  unchanged editor code, including thumbnails and keyframes.
- Exact pixel-clone parity to the supplied reference is not claimed; the final
  screen-capture artifact could not be visually reviewed from the release
  environment.

## Previous v4.5.0 notes — measured reference alignment

Package `com.editorshub` (version code 18). The reference screenshots and fresh
REA/Ghidra structural measurements were used as the visual targets. This is an
original implementation; no reference-app source, proprietary assets, fonts,
or re-signed APK are included. Branding remains editable in the app.

## What changed

- The default `Rounded Rectangle 1` now renders at 200 × 200 composition units,
  centered and filled with the screenshot-measured `#ba7e38`. It has no painted
  white stroke: the white border and handles appear only while selected.
- The shape picker now starts with the reference's 18 shapes in six columns,
  with three page dots, reference-sized spacing and vector edit-point previews.
- The editor's initially hidden preview rail now follows its actual toggle;
  the measured stage, timeline, property root/detail layout and 53 px/sec ruler
  are aligned. New shape clips default to four seconds, matching the visible
  clip width in the reference.
- Launcher header geometry and decoded resource colors were corrected. PWA
  shell cache is `editorshub-v19`; Android build metadata is 4.5.0 / code 18.

## Verification

- `npm run typecheck` and `npm run build`.
- `node tools/verify-gif.mjs`: GIF89a encoder checks.
- `node tools/smoke.mjs --shots`: 100 headless checks, zero console errors or
  warnings; includes shape geometry/fill/stroke/duration and real preview-rail
  show/hide behavior.
- Android debug/release/AAB and Capacitor APK builds run in GitHub Actions; the
  installable Capacitor debug APK is attached to this release by CI.
- Pixel differences are recorded per state in `docs/VISUAL_AUDIT.md`; they are
  measurements, not a claim of pixel identity.
