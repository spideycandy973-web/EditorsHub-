# EditorsHub v2.0.0 — Professional Motion Graphics & Video Editor

An original, production-structured Android motion editor: Kotlin + Jetpack Compose + Material 3,
Room, Hilt, WorkManager, Media3, OpenGL ES 2.0, MediaCodec/MediaMuxer and the Storage Access
Framework. Package `com.editorshub`.

## Install
`EditorsHub-debug.apk` is installable for development (`adb install`). The release APK is
unsigned by design — configure a keystore (see `EditorsHub/docs/RELEASE_SIGNING.md`) or run the
`android-release.yml` workflow with keystore secrets. `EditorsHub-release.aab` is for Play upload.
`EditorsHub-source-v2.0.0.zip` contains the complete source tree.

## What works
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

## Verification
GitHub Actions runs `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease` and
`:app:bundleRelease` on every relevant push. Instrumented tests cover importers, media and real
MP4 export on a device/emulator.

## Honest limitations
Binary `.aep`, AE expressions/3D/plugins, zero-copy SurfaceTexture decoding, optical flow,
video-reference AI analysis, AV1/MOV/WebM/image-sequence output and 120 fps hardware encoding are
not implemented and are not exposed as working features. Full details:
`EditorsHub/docs/ARCHITECTURE.md`.

MIT licensed. No proprietary source, branding or assets from Alight Motion.
