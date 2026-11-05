# Changelog

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
