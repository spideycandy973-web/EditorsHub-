# EditorsHub v4.6.0 — motion-first studio pass

Android candidate package `com.editorshub` (version code 19). The landing flow now
opens as a video-editing workspace: create a composition, bring in multiple clips,
resume recent projects, or browse project imports and templates. Tab selection,
inspector panels, expandable sections, and important touch targets have deliberate
motion feedback. The underlying editor/render/export engine remains the existing
native implementation; this candidate is not a claim of complete Alight Motion
parity and is not a production-signed release.

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
