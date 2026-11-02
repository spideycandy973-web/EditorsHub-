# Changelog

All notable changes to this repository are documented here.

## 1.0.0 — 2026-11-02

### Motion Studio (native Android, `com.motionstudio.editor`)
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

## 1.1.0 — 2026-11-03 (EditorsHub Motion Studio v2)

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
