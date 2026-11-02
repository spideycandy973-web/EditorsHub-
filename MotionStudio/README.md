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
