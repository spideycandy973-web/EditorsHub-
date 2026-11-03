# Architecture

## Layers

```
UI (Compose)  ──► ViewModels (Hilt) ──► Repositories ──► Room / DataStore
     │                    │
     │                    └──► RenderGraph (pure Kotlin, resolved frame state)
     └──► PreviewSurface ──► CanvasCompositor ──► GlProgram (GLSurfaceView / EGL)
                                      │
ExportWorker ──► VideoExporter ──► GlFrameWriter(EGL→MediaCodec Surface) ──► MediaMuxer
                          └──► AudioMixer (MediaCodec PCM decode → AAC encode)
```

**Rendering logic is fully separated from the UI.** `RenderGraph` converts a `Project` + time into
`RenderFrame` (resolved matrices, effect params, mask state, transitions, crossfades) with no
Android types, so every interpolation/compositing decision is JVM-unit-testable.

### Module map

| Module | Responsibility |
| --- | --- |
| `core/domain/model` | Serializable project model, enums, ARGB color math |
| `core/domain/keyframe` | `KeyframeEngine`: easing, cubic-bezier solver, color interpolation |
| `core/domain/timeline` | Clip geometry, split/trim/move/snapping/frame math |
| `core/domain/effects` | `EffectCatalog`: parameter specs, defaults, animated resolution |
| `core/domain/transitions` | Pure per-frame transition state |
| `core/data` | Versioned JSON serializer, repository interface, templates |
| `core/database` | Room entities/DAO (projects + version snapshots) |
| `core/media` | SAF import, MediaMetadataRetriever probe, MediaCodec PCM waveform, bounded bitmap caches |
| `core/rendering` | `RenderGraph`, `CanvasCompositor`, `GlProgram`, `EglCore`, `GlFrameWriter`, `ThumbnailGenerator` |
| `core/rendering/export` | `VideoExporter`, `ExportWorker` |
| `core/settings` | DataStore preferences |
| `ui/feature/home` | Dashboard, project management, templates, settings, tutorials |
| `ui/feature/editor` | Preview, timeline, inspector panels, graph editor, export dialog |

### Data flow for one frame

1. `RenderGraph.resolve(project, timeMs)` → active layers, evaluated keyframes, parent chain
   matrices, resolved effect parameters, masks and transitions.
2. `CanvasCompositor.draw()` paints each layer into a reusable scratch bitmap: vector/text/shape
   content → mask pass (DST_IN/DST_OUT) → effect stack → composite with `PorterDuff`/`BlendMode`
   and the animated `Matrix`.
3. Preview: the resulting bitmap is uploaded as a GL texture and post-processed by the fragment
   shader (blur, color grading, RGB split, pixelation, wave, noise, vignette) on a `GLSurfaceView`.
   A hardware-Canvas `RenderView` is available as the fallback backend.
4. Export: `GlFrameWriter` binds an EGL window surface to the MediaCodec input `Surface`, draws the
   same shader chain per frame, and the encoder output is written to `MediaMuxer`. Audio is decoded
   to PCM, mixed with volume/fades, encoded to AAC-LC and muxed into the same MP4.

### Concurrency

- Playback advances on `viewModelScope` with a frame delay.
- Autosave is a debounced coroutine (700 ms) writing JSON to Room.
- Export runs in a Hilt `CoroutineWorker` (WorkManager) on `Dispatchers.Default`, publishing real
  per-frame progress and checking `isStopped` for cancellation.
- `LruCache` bounds decoded image/video bitmaps; `previewQuality` scales video decode size.

## Known limitations

These are documented rather than hidden behind fake buttons:

- Video frames for preview/export are decoded with `MediaMetadataRetriever.getFrameAtTime`
  (frame-accurate nearest-keyframe decoding). A `SurfaceTexture`/MediaCodec zero-copy decoder path
  is the next performance step for 4K/60fps projects.
- Audio waveform extraction and mixing decode through MediaCodec PCM; exotic codecs fall back to a
  video-only export with a UI message.
- Custom vector path editing currently exposes add/remove of M/L/Q/C commands and numeric point
  lists; direct pinch-to-edit handles are planned.
- H.265 and 4K are attempted only when the device exposes an encoder; otherwise the app falls
  back to H.264 and reports it.
- Instrumented media/export tests require an emulator or device with codec support; CI runs JVM
  unit tests plus compilation and packaging.
- Release artifacts are **unsigned by default**; production signing requires a local
  `keystore.properties` (see RELEASE_SIGNING.md).

## Version 2 additions

| Module | Responsibility |
| --- | --- |
| `core/importer` | `ProjectImporter` orchestration, `CountingInputStream`, `AlightMotionXmlImporter` (XmlPullParser), `NodeVideoZipImporter` (ZipInputStream), `AfterEffectsImporter` (AEPX XML), `NativeProjectImporter` |
| `core/ai` | `EffectGraph` / `NodeEvaluator` (validated DAG → real effect stack), `AiEffectService` (HTTPS inference), `AiKeyStore` (Keystore AES-GCM) |
| `ui/navigation` | `MainTab` destinations, animated `NavHost` transition specs, navigation bar/rail |
| `ui/feature/home` | Home, Projects, Templates, project creation sheet, settings pages, tutorials |
| `ui/feature/effects` | Effects Library with live `RenderPreview` and preset management |
| `ui/feature/ai` | AI Studio (no simulation), `ui/feature/node` visual node editor |
| `ui/feature/importer` | Import screen with progress, cancellation and conversion report |

Exact frame rates: `Project.fpsExact` feeds `TimelineMath.frameCount/frameTimeExact/snapExact`,
`EditorViewModel.play()`, `VideoExporter` (`MediaFormat.KEY_FRAME_RATE` as float and per-frame PTS).

Transparent/gradient backgrounds are handled by `CanvasCompositor.drawBackground`; the checkerboard
and guides are a Compose `Canvas` overlay in `PreviewSurface` (export intentionally keeps an opaque
MP4 frame because H.264 has no alpha channel — this is documented, not hidden).

### Known limitations (v2)

- Binary `.aep` is not parsed; the original is preserved and a report explains the AEPX /
  interchange workflow. Expressions, 3D layers, cameras and proprietary AE plugins are not
  reproduced.
- Alight Motion XML has no published schema: the importer is alias-based and reports unsupported
  properties, missing media and approximated Bezier curves rather than promising full parity.
- `SurfaceTexture` zero-copy video decoding and optical-flow interpolation are not implemented;
  nearest-keyframe `MediaMetadataRetriever` decoding is used.
- Image-to-effect uses real pixel statistics (average RGB/brightness/saturation/hue) rather than
  uploading the image to a vision model; video-reference analysis is not implemented.
- AV1, MOV/WebM output, image sequences and 120 fps hardware encoding are only used when the
  device exposes the encoder; otherwise the app falls back and reports it.
