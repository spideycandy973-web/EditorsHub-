# Changelog

All notable changes to this repository are documented here.

## 2.1.0 — 2026-11-03 (Alight-Motion-style UI)

### Added
- **Light Projects launcher** (web/PWA): hamburger, centred wordmark, avatar, Explore Templates
  circular carousel, Latest projects, dashed Create New Project card, Get started cards and a
  bottom nav with the signature teal circular + button. Projects tab with XML upload card, filter
  chips and saved-project cards; Templates and Tutorials tabs wired to real actions.
- **AM-style Create Project sheet**: name, 6 aspect ratios, 480p–4K, 24/25/30/50/60 fps,
  duration, background colour and editable demo composition.
- **Dark midnight editor** restyle: AM navigation bar, control bar with tick ruler + timecode pill,
  watermark pill, coloured clip bars (amber text / teal shape / blue media / purple audio / coral
  solid), white playhead, teal Add Layer FAB, bottom tool dock and vertical inspector rail.
- **AM-style Add menu**: Shape / Media / Audio / Object / Element / Text categories with a
  quick-action rail; every item performs a real layer or import action.
- Real **Settings modal** (grid, safe area, snap, loop, auto-key, zoom) and Settings gear in the
  editor top bar.
- Mint/teal `#10E0A0` on midnight navy `#141824` across web + native; launcher, splash,
  notification and PWA icons recoloured; new `EditorsHub` wordmark for the light launcher.

### Changed (native Android)
- `EditorPalette` reskinned; editor top bar reduced to back / title / guides / overflow / export;
  control bar rebuilt with transport + ruler + `00:00:00` timecode; timeline clip colours and
  playhead recoloured; Add Layer FAB added; Home hero restyled as "What will you create?".
- `versionCode 3`, `versionName 2.1.0`; releases `v2.1.0` and rolling `editorshub-latest`.

## 2.0.1 — 2026-11-03 (stability + brand)

### Fixed
- **Cold-start crash**: `MainActivity` (AppCompatActivity) used a plain `android:Theme.Material`
  descendant; AppCompat rejected it when `setContent` was called. Themes now inherit
  `Theme.AppCompat.NoActionBar`.
- Missing `INTERNET` / `ACCESS_NETWORK_STATE` permissions for AI Studio's real inference.
- Runtime guards for Hilt/WorkManager initialisation order, DataStore corruption, project load,
  autosave/thumbnails, import, export enqueue, language switching, invalid composition
  dimensions, GL/EGL/shader failures and `OutOfMemoryError` during compositing/export.
- Playback modulo-by-zero and invalid `coerceIn` ranges for zero-duration projects; preview-guides
  setting mapping; notification small icon now uses density-specific PNGs.
- CI now runs an Android 33 emulator launch smoke test (fails on process death or
  `FATAL EXCEPTION` in logcat).

### Changed
- New **EditorsHub "EH" logo** applied consistently to the native adaptive/legacy launcher icons,
  splash, notification icon, in-app wordmark, PWA icons/manifest, and Capacitor wrapper icons.
- App label / page title / PWA name / Capacitor `appName` unified as **EditorsHub**.
- `versionCode 2`, `versionName 2.0.1`; releases `v2.0.1` and rolling `editorshub-latest`.

## 1.0.0 — 2026-11-02

### EditorsHub (native Android, `com.editorshub`)
- Complete Kotlin / Jetpack Compose / Material 3 app: home dashboard, project management
  (create, rename, duplicate, delete, thumbnails, templates), settings and tutorials.
- Multilayer timeline with drag-to-move, edge trimming, split, reorder, visibility, lock,
  zoom, snapping, frame stepping and playhead scrubbing.
- Keyframe engine: linear / ease-in / ease-out / ease-in-out / cubic Bezier / elastic /
  bounce / hold, color interpolation, graph editor, copy/paste, 8 motion presets.
- 18 stackable, animatable effects with a GPU (OpenGL ES 2.0) shader chain plus a Canvas backend.
- 9 transition types, text editor (gradients, outlines, shadows), shape/vector editor,
  masks (rectangle/circle/path with feather and invert), audio tracks with real PCM waveforms.
- Real rendering + export: MediaCodec H.264/H.265, 480p–4K, 24/30/60 fps, bitrate control,
  AAC audio mixing, MediaMuxer MP4, MediaStore save, WorkManager background progress and cancel.
- Room project database with versioned JSON snapshots, autosave and interrupted-session recovery.
- JVM unit tests + instrumented media/export/UI tests; GitHub Actions CI producing debug APK,
  unsigned release APK and AAB; separate signed-release workflow.

### EditorsHub Motion (web/PWA)
- Browser-based compositor with layers, keyframes, effects, XML import/export,
  MediaRecorder video export and PNG frame ZIP export.
- Signed Capacitor APK (`EditorsHub-Motion.apk`) built by CI.

## 1.1.0 — 2026-11-03 (EditorsHub EditorsHub v2)

- Animated six-tab navigation, animated inspector/play transitions, premium dark design system,
  UI scaling and language switching.
- Home/Projects: search, sorting, grid/list, full metadata, share/export/info/confirm-delete actions.
- Full project creation (resolutions 144p–4K, custom, 8 aspect ratios, 13 exact frame rates,
  backgrounds, audio sample rate, colour space, timebase, preview quality).
- Preview guides and proxy media; undo/redo, multi-select, clipboard, grouping, magnetic snapping,
  zoom-to-fit/selection.
- Effects Library with real rendered previews; validated node-based custom effect builder.
- AI Studio with real inference endpoint, encrypted key storage and no simulated generation.
- Streaming universal import (native JSON, Alight Motion XML, Node Video ZIP, AEPX, AEP reference),
  conversion reports, no size cap, cancellation and import logs.
- Expanded settings pages wired to real behaviour; exact-FPS export, AAC bitrate and save location.
- New JVM tests (frame rates, effect graphs, project spec/migration) and instrumented importer tests.
