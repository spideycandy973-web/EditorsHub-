# Motion Studio

An original, professional **Android** motion-graphics and video editor — Kotlin, Jetpack Compose,
Material 3, Room, Hilt, Media3, OpenGL ES and MediaCodec. Package `com.motionstudio.editor`.

> This is an independent project inspired by the *workflow* of professional motion editors.
> It contains no Alight Motion source, branding, logos or copyrighted assets.

## What is real (no mockups, no fake controls)

| Area | Implementation |
| --- | --- |
| Preview | `CanvasCompositor` draws every layer every frame; `GlProgram` applies a GPU shader chain on a `GLSurfaceView`; hardware Canvas fallback; frame stepping, playback loop, zoom, fullscreen |
| Timeline | Drag clips, drag edges to trim, split at playhead, duplicate, delete, reorder, hide, lock, zoom, snapping, keyframe diamonds |
| Keyframes | Pure-Kotlin interpolation engine (linear, ease in/out, cubic Bezier, elastic, bounce, hold), color tracks, draggable graph editor, custom Bezier control points, copy/paste |
| Effects | 18 stackable effects, each with animatable params: blur/motion/directional blur, glow, shadow, brightness, contrast, saturation, hue, exposure, vignette, sharpen, grain, RGB split, chromatic aberration, pixelation, wave, color grading |
| Transitions | Fade in/out, crossfade, zoom, slide, spin, blur, wipe, custom; resolved per frame into opacity/scale/rotation/blur/wipe |
| Text | Multi-line text, font family/weight/italic/alignment, gradients, outlines, shadows, letter/line spacing, caps, animation presets |
| Vectors & shapes | 8 shapes plus bezier path commands (M/L/Q/C/Z), editable points, fill/stroke/gradients, animation |
| Masks | Rectangle, circle and path masks with feather, opacity, expansion, invert and keyframes |
| Audio | SAF import, multiple tracks, real PCM waveform decoding, volume keyframes, fade in/out, PCM mixing, AAC encoding |
| Export | MediaCodec video encoder (H.264/H.265), OpenGL ES frame writer, MediaMuxer MP4, 480p/720p/1080p/1440p/4K, 24/30/60 fps, bitrate, AAC audio, MediaStore save, share, WorkManager progress + cancel |
| Projects | Room database, versioned JSON snapshots, autosave, thumbnails, duplicate, version history, interrupted-session recovery |
| Settings | Dark/dynamic theme, preview quality, default resolution/fps/bitrate/audio, snapping, cache clearing, language, about, licenses, privacy |
| CI | Builds, unit tests, debug APK, unsigned release APK, AAB; separate signed-release workflow |

## Build

Requirements: **JDK 17+**, Android SDK platform 35, build-tools 35.0.0, Gradle 8.11.1 (wrapper included).

```bash
cd MotionStudio
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assembleRelease        # unsigned release APK (no keystore committed)
./gradlew :app:bundleRelease          # AAB
./gradlew :app:connectedAndroidTest   # instrumented tests (needs a device/emulator)
```

Open `MotionStudio/` as the project root in Android Studio. The generated APKs land in
`MotionStudio/app/build/outputs/`; CI also copies them to `/artifacts` and publishes them to the
`motion-studio-latest` GitHub Release.

## Install

```bash
adb install MotionStudio/app/build/outputs/apk/debug/app-debug.apk
```

Debug builds are signed with the Android debug key and are fine for development. **For a public
production release, configure your own signing key** — see [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).
Unsigned release APK/AAB artifacts are intentional; they are not production-signed.

## Media support

Imported through the Storage Access Framework and copied into private app storage so project
references survive: MP4, MOV (device-dependent), WebM, PNG, JPG/JPEG, WebP, GIF, MP3, WAV, AAC/M4A.
Unsupported codecs are reported as errors rather than silently faked.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

```
app/src/main/java/com/motionstudio/editor/
  core/common/ core/database/ core/data/ core/di/
  core/domain/{model,keyframe,timeline,effects,transitions}
  core/media/ core/rendering/{canvas,gl,audio,export} core/settings/
  ui/{theme,components,feature/home,feature/editor}
app/src/test/          JVM unit tests
app/src/androidTest/   instrumented media, export and UI tests
```

## Honest scope notes

A 1:1 feature clone of a mature commercial editor is a multi-year project. This release is a
**working foundation with real implementations** of the core: compositor, keyframes, effects,
masks, text/vectors, audio mixing and MP4 export. Deliberately bounded areas are documented in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#known-limitations) instead of being hidden behind
placeholder buttons.

## License

MIT — see [`LICENSE`](../LICENSE). Third-party dependencies are Apache 2.0 / MIT.

## Version 2 — full workspace, animation and interoperability

- **Animated navigation**: six real main destinations (Home, Projects, Templates, Effects Library,
  AI Studio, Settings) on a bottom bar / navigation rail with directional slide+fade transitions,
  animated selection indicators, animated inspector-tab switching and animated play/pause.
- **Projects**: search, sorting (date/name/duration/size), animated grid/list switch, real metadata
  (thumbnail, resolution, exact FPS, duration, size, modified date), and real actions: open, rename,
  duplicate, delete (confirmation), share native project JSON, background video export and a
  project-information dialog.
- **Project creation**: 144p–4K + custom resolution, eight aspect ratios, 12/15/23.976/24/25/29.97/
  30/48/50/59.94/60/90/120 fps (exact rational rates used by playback, timeline and encoder),
  solid/transparent/linear/radial backgrounds, audio sample rate, colour space, preview quality,
  default duration and timebase — all persisted.
- **Preview guides**: transparency checkerboard, safe-area, grid, centre and aspect guides;
  proxy-media quality scaling; zoom/fit/fullscreen.
- **Editor operations**: undo/redo (80 steps), multi-select, copy/paste layers, group/ungroup,
  enable/disable, mute, extend, zoom-to-fit / zoom-to-selection, magnetic snapping.
- **Effects Library & Node Editor**: 18 effects with live compositor previews; a validated DAG
  node editor where topological order becomes the real render stack; save/import/export graphs.
- **AI Studio**: text-to-effect and reference-image colour analysis through a configured
  OpenAI-compatible inference endpoint. API keys are encrypted with Android Keystore AES-GCM.
  Generation is disabled (not simulated) when the service is unavailable; rate limits, errors and
  connection status are real. Generated graphs are validated against a node whitelist.
- **Universal project import**: streaming import of native `.json`, Alight Motion `.xml`, Node Video
  `.zip`/`.amproj`, After Effects `.aepx` (XML) and `.aep` (preserved reference only). No 5 MB cap;
  storage validation, incremental XML parsing, streamed ZIP extraction, media relinking, duplicate
  asset detection, cancellation, import logs and an honest conversion report for every file.
- **Settings**: General / Performance / Export / Storage / AI / About pages; every preference
  persists and changes real behaviour (UI scale, language, autosave interval, hardware acceleration,
  background rendering, proxy media, preview guides, snapping, export defaults, cache limit, AI
  configuration).

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map and
[docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) for versions.
