# EditorsHub v2.3.0 — pixel-level Alight Motion geometry

An original, production-structured Android motion editor: Kotlin + Jetpack Compose + Material 3,
Room, Hilt, WorkManager, Media3, OpenGL ES 2.0, MediaCodec/MediaMuxer and the Storage Access
Framework. Package `com.editorshub`, application label `EditorsHub`.

## Install
`EditorsHub-debug.apk` is installable for development (`adb install`). The release APK is
unsigned by design — configure a keystore (see `EditorsHub/docs/RELEASE_SIGNING.md`) or run the
`android-release.yml` workflow with keystore secrets. `EditorsHub-release.aab` is for Play upload.
`EditorsHub-source-v2.3.0.zip` contains the complete source tree (source only, no build outputs).
The web/PWA editor and its Capacitor APK (`EditorsHub-Motion.apk` / `apk-latest`) are also rebuilt.

## What's new in 2.3.0 — pixel-level Alight Motion geometry

The editor's layout and interaction model now follow the Alight Motion screenshots closely, while
keeping **EditorsHub**'s own name, logo and original code. No proprietary assets are copied.

- **Seven transport icons spread across the full width** of a 38 px control bar; separators removed.
- **Contextual navigation bar**: with a property panel open it becomes back / panel name / overflow
  (`Move & Transform`, `Effects`, `Keyframes`, `Project`); otherwise project name / gear / export.
- **Panel replaces the bottom tab bar**: the inspector fills the bottom of the screen with AM's
  `▼ property` dropdown, a left rail (back / keyframes / fit / layers) and a labelled right rail
  ending in the big **X**. Closing the panel reveals the icon-only tab bar again.
- **Add menu rebuilt** to AM's six categories (Shape, Media, Audio, Object, Element, Freehand
  Drawing) with a teal selected-icon box, an **All / Browse** filter rail, a two-column built-in
  thumbnail grid and a right rail of **All / Vector Drawing / Text / X** with text labels.
- **Real freehand drawing**: a pointer-driven canvas with brush colour, size, undo and clear; the
  result is rasterised to a PNG and added as a genuine image layer. No simulated features.
- **Property sheet header** (`▼ Move & Transform`) with a working panel-switch dropdown; top bar
  overflow opens the real Layer options dialog.
- **AM-style timeline**: flat orange / teal / blue clip bars with dark labels, a hamburger on every
  clip, a grey `‹` selected-layer arrow attached to the bar, real asset thumbnails in the layer
  gutter, a faint `EditorsHub` ruler watermark, and short ruler ticks. The floating layer chip was
  removed because AM attaches that control to the clip.
- Lighter AM navigation-bar navy (`#2b3046`), panel navy (`#22263a`), rail navy (`#191d2b`).
- Launcher gained the classic AM **Welcome** card with three outlined pill buttons and a tutorial
  card; all buttons perform real actions (help, templates, XML import).
- Mobile grid locked to the viewport so wide property controls can no longer push the UI off-screen.
- `versionCode 5`, `versionName 2.3.0`; PWA cache `editorshub-v5`.

## Previous release — 2.2.0 (closer Alight Motion fidelity)

This release tightens the editor to Alight Motion's actual layout and interaction model while
keeping **EditorsHub**'s own name, logo and original code. No proprietary assets are copied.

- **Clean preview**: floating zoom chip, resolution/FPS badges, layer chip and zoom toolbar were
  removed from the canvas. The composition now starts light grey, with a subtle vertical
  `EditorsHub` watermark at the right edge, exactly as Alight Motion's empty project does. Those
  controls are still real and reachable from Settings (zoom slider, grid/guides) and the Add menu.
- **Navigation bar**: back / project name / settings gear / export, single-line title on phones.
- **Single-row transport**: undo, redo, start, play/pause, end, duplicate, fit — no second ruler row.
- **Centre-anchored playhead**: the playhead is fixed at the horizontal centre of the timeline and
  layers scroll underneath it. Scrubbing uses drag-delta so it stays stable while the ruler moves;
  playback, scrubbing and zoom were verified to keep the playhead at the exact centre.
- **AM-style ruler**: small tick marks only, plain white `mm:ss:ff` timecode above the playhead,
  no numeric labels on phones. The layer chip with previous/next arrows sits above the clip bars.
- **Layer gutter**: eye + thumbnail pinned at the left edge; amber/teal/blue clip bars; no
  timeline toolbar on phones (all its actions live in the real Layer options dialog).
- **Circular outlined Add Layer button** at the bottom-right of the timeline, replaced by the big
  **X** in the inspector rail while the Add menu is open.
- **Two rails, not four**: one panel rail on the left, one persistent quick-action rail on the
  right (All / Text / Shapes / close). Every button performs a real action.
- All template circles now carry Alight Motion's thin teal ring.
- `versionCode 4`, `versionName 2.2.0`; PWA cache `editorshub-v4`.

## Previous release — 2.1.0 (Alight-Motion-style UI)

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
