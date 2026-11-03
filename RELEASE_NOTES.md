# EditorsHub v2.0.1 — Professional Motion Graphics & Video Editor

An original, production-structured Android motion editor: Kotlin + Jetpack Compose + Material 3,
Room, Hilt, WorkManager, Media3, OpenGL ES 2.0, MediaCodec/MediaMuxer and the Storage Access
Framework. Package `com.editorshub`, application label `EditorsHub`.

## Install
`EditorsHub-debug.apk` is installable for development (`adb install`). The release APK is
unsigned by design — configure a keystore (see `EditorsHub/docs/RELEASE_SIGNING.md`) or run the
`android-release.yml` workflow with keystore secrets. `EditorsHub-release.aab` is for Play upload.
`EditorsHub-source-v2.0.1.zip` contains the complete source tree.

## What changed in 2.0.1

### Crash fixes
- **Fixed the launch crash.** `MainActivity` extends `AppCompatActivity` but the theme inherited
  from `android:Theme.Material.NoActionBar`. AppCompat throws
  `IllegalStateException: You need to use a Theme.AppCompat theme` as soon as `setContent` is
  called, so every cold start died. Both day/night themes now inherit
  `Theme.AppCompat.NoActionBar`.
- Added `INTERNET` / `ACCESS_NETWORK_STATE` permissions so AI Studio's real inference endpoint
  is actually reachable instead of failing with a permission error.
- Guarded WorkManager configuration against being requested before Hilt injects the Application.
- Guarded every startup/IO boundary: project loading, settings flow, autosave/thumbnails,
  importer, WorkManager enqueue, language switching and AI key storage now degrade gracefully
  instead of killing the process.
- Guarded rendering: invalid composition dimensions (zero/negative width or height), GL ES
  support detection, EGL/shader failures and `OutOfMemoryError` in the compositor, thumbnail
  generator and export encoder now fall back to a safe frame or an honest failure message.
- Fixed playback modulo-by-zero and invalid `coerceIn` ranges on zero-duration projects; fixed
  the preview-guides setting mapping; replaced the vector notification small icon with proper
  density-specific PNGs.
- **New CI launch smoke test**: the debug APK is installed on an Android 33 emulator,
  `MainActivity` is started, and the job fails if the process dies or `FATAL EXCEPTION` appears
  in logcat. Unit tests alone could not have caught the v2.0.0 startup crash.

### Branding / logo
- New **EditorsHub "EH" monogram**: a violet `E` (timeline bars), a cyan `H` and three connected
  hub nodes. It is the same mark everywhere — native adaptive launcher icon (with monochrome
  themed-icon variant), legacy launcher PNGs, splash screen, notification icon, PWA icons
  (`icon.svg`, `icon-192/512.png`, maskable, Apple touch, favicon), Capacitor launcher/splash
  assets, and the in-app wordmark (`logo.svg`, Home hero and web top bar).
- App label, PWA manifest, page title, service-worker cache and Capacitor `appName` are now
  consistently **EditorsHub**. The web/Capacitor wrapper keeps `com.editorshub.motion` so it can
  coexist with the native `com.editorshub` build.

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
GitHub Actions runs `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`,
`:app:bundleRelease` and an emulator launch smoke test on every relevant push.

## Honest limitations
Binary `.aep`, AE expressions/3D/plugins, zero-copy SurfaceTexture decoding, optical flow,
video-reference AI analysis, AV1/MOV/WebM/image-sequence output and 120 fps hardware encoding are
not implemented and are not exposed as working features. Full details:
`EditorsHub/docs/ARCHITECTURE.md`.

MIT licensed. No proprietary source, branding or assets from Alight Motion.
