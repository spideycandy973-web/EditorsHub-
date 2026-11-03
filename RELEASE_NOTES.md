# EditorsHub v2.1.0 — Alight-Motion-style Editor

An original, production-structured Android motion editor: Kotlin + Jetpack Compose + Material 3,
Room, Hilt, WorkManager, Media3, OpenGL ES 2.0, MediaCodec/MediaMuxer and the Storage Access
Framework. Package `com.editorshub`, application label `EditorsHub`.

## Install
`EditorsHub-debug.apk` is installable for development (`adb install`). The release APK is
unsigned by design — configure a keystore (see `EditorsHub/docs/RELEASE_SIGNING.md`) or run the
`android-release.yml` workflow with keystore secrets. `EditorsHub-release.aab` is for Play upload.
`EditorsHub-source-v2.1.0.zip` contains the complete source tree (source only, no build outputs).
The web/PWA editor and its Capacitor APK (`EditorsHub-Motion.apk` / `apk-latest`) are also rebuilt.

## What's new in 2.1.0 — Alight-Motion-style UI

This release rebuilds the visible interface around Alight Motion's layout and interaction model,
while keeping **EditorsHub**'s own name, logo and original code. No proprietary assets are copied.

### Light "Projects" launcher (web/PWA + home)
- Hamburger menu, centred wordmark, avatar; "Explore Templates" circular carousel;
  "Latest projects" with a dashed **Create New Project** card and project cards showing duration,
  ratio, resolution and layer count; "Get started" cards; bottom navigation with the signature
  **teal circular +** button.
- Projects tab with "Upload an XML file from your device", filter chips and a saved-project list.
- Templates tab opens real editable demo projects; Tutorials tab wires to real editor actions.
- AM-style **Create Project** sheet: name, six aspect ratios, 480p–4K, 24/25/30/50/60 fps,
  duration, background colour and an editable demo-composition option.

### Dark midnight editor
- Navigation bar: back, project name/resolution/fps, guides, overflow menu, export.
- Control bar: undo, redo, skip-to-start, play, skip-to-end, duplicate, fit — plus a tick ruler
  and a centred `00:00:00` timecode pill, matching Alight Motion's control bar.
- Preview with a watermark pill, circular fit button, checkerboard/guides and zoom controls.
- Timeline with an eye/lock/thumbnail layer column, **amber text, teal shape, blue media,
  purple audio, coral solid** clip bars, a white playhead and diamond keyframe markers.
- Teal rounded-square **Add Layer FAB** anchored to the timeline, plus a bottom tool dock and a
  vertical tool rail in the inspector.
- **Add menu** rebuilt as Alight Motion's category row (Shape / Media / Audio / Object / Element /
  Text) with a right-hand quick-action rail and real layer/import actions behind every item.
- Inspector panels restyled as AM-style property rows with teal sliders, numeric value boxes and
  per-property keyframe diamonds.
- New mint/teal accent (`#10E0A0`) on midnight navy (`#141824`) throughout both the web editor
  and the native Compose app; launcher icon, splash, notification icon and PWA icons recoloured to
  the same palette.

### Native Android app
- `EditorPalette` reskinned to midnight navy + teal; top bar, control bar, timeline clip colours,
  playhead, inspector tabs and the new Add Layer FAB all match the web editor.
- Home hero restyled as "What will you create?" with a teal Create New Project button.
- All existing destinations and features remain real and functional; no fake controls added.

## What works (unchanged functionality)
- **Workspace**: real multilayer compositor, hardware Canvas + OpenGL ES shader chain, playback,
  frame stepping, zoom/fit/fullscreen, transparency checkerboard and safe-area/grid/centre guides.
- **Timeline**: drag/trim/split/duplicate/reorder, multi-select, group/ungroup, lock/hide/mute,
  magnetic + frame snapping, zoom-to-fit/selection, undo/redo.
- **Animation**: Bezier/linear/ease/hold/elastic/bounce keyframes, draggable curve editor, custom
  control points, colour tracks, copy/paste, motion presets.
- **Effects**: 18 stackable animatable effects; Effects Library with live rendered previews;
  validated node-based custom effect builder (DAG → real render stack).
- **Text / shapes / vectors / masks**: gradients, strokes, shadows, editable paths, multiple masks
  with feather/expand/invert and keyframes.
- **Transitions / audio**: 9 transition types, crossfades, PCM waveforms, volume/fades, AAC mixing.
- **Import**: streaming native JSON, Alight Motion XML, Node Video ZIP, After Effects AEPX, AEP
  reference preservation; no size cap; conversion reports; media relinking; cancellation.
- **Export**: MediaCodec H.264/H.265 → MP4, exact rational frame rates, 480p–4K, bitrate/quality,
  AAC, MediaStore or app storage, real progress and cancellation, output validation.
- **Projects**: versioned JSON in Room, autosave, recovery, thumbnails, share, export, version
  history, search/sort/grid-list, full creation settings (144p–4K, 8 ratios, 13 frame rates,
  background, colour space, timebase, audio sample rate).
- **AI Studio**: real OpenAI-compatible inference with Android Keystore-encrypted key storage;
  generation is disabled (never simulated) when the service is unavailable.
- **UI**: directional navigation/tab transitions, animated selection indicators and inspector
  panels, six implemented destinations, UI scaling and language switching.

## Stability (carried from 2.0.1)
- The cold-start AppCompat theme crash is fixed, and CI runs an **Android 33 emulator launch
  smoke test** that fails on process death or `FATAL EXCEPTION` in logcat.
- Runtime guards for Hilt/WorkManager init order, DataStore corruption, project load, autosave,
  import, export, GL/EGL/shader failures, OOM and invalid composition sizes.

## Honest limitations
Binary `.aep`, AE expressions/3D/plugins, zero-copy SurfaceTexture decoding, optical flow,
video-reference AI analysis, AV1/MOV/WebM/image-sequence output and 120 fps hardware encoding are
not implemented and are not exposed as working features. Full details:
`EditorsHub/docs/ARCHITECTURE.md`.

MIT licensed. No proprietary source, branding or assets from Alight Motion.
