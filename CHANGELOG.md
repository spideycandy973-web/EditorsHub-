# Changelog

## 3.4.0 — full reference-recording pass (2026-11-04)

- **The whole 10:29.66 recording was analysed.** `.github/workflows/reference-frames.yml`
  downloads the 301,626,146-byte Drive MP4 in CI (the agent workspace caps a single file at
  100 MiB), verifies its size, and runs `tools/build-storyboard.py`: 4 fps change detection →
  **747 distinct UI states**, 38 labelled contact sheets, 630 one-second timeline frames and 8
  timeline sheets. Every sheet was reviewed; `docs/VIDEO_AUDIT.md` records the screen-by-screen
  findings and the exact sampled palette.
- Palette corrected to the measured pixels: `#121214` background, `#1c1c1e` cards,
  `#262628` fields, `#dfe3ff` lavender, `#01ffa7` teal.
- Added the cold-launch **splash** (animated white double-arc swirl + spinner) with a
  reduced-motion path, and a **full-screen About** page whose device/build card shows only live
  `navigator`, screen, storage and display-mode values.
- Projects now has a real **Sort by Date / Name / Duration** control, date groups, per-project
  thumbnails, and a **selection mode** with Select All, Fix (checks real asset references),
  Export (ZIP of XML packages), Duplicate (clones stored projects) and Delete with confirmation.
- **Export & Share** rebuilt with the six recorded formats. New `src/editor/gif.ts` is a real
  GIF89a/LZW encoder (256-colour quantisation, NETSCAPE2.0 loop); its output was decoded with
  an independent GIF decoder and verified end-to-end in headless Chromium. "Also export as a
  Template" saves a real re-importable XML template (`src/um/lib.ts`).
- Shape picker expanded from 3 to **5 pages**: 18 built-ins, 9 extras, 26 polygon/star variants
  and 18 original normalized vector silhouettes rendered by `Path2D`.
- Effect Browser category tiles added above the existing real effect list.
- `tools/smoke.mjs` extended to **40 checks** (splash completion, About, sort/selection, export
  formats, GIF byte signature, five shape pages) — all passing, zero console errors.
- CI/release branch moved to `arena/e30aca1f-editorshub`, tag to `v3.4.0`; the Capacitor
  workflow now waits for the versioned release before attaching the APK.
- `versionCode 11`, `versionName 3.4.0`, PWA cache `editorshub-v11`.

## 3.3.0 — reference chrome + video frame-audit tooling (2026-11-04)

- Added a reproduction of the device chrome present in all 20 reference frames: status strip
  (clock, signal bars, Wi-Fi, battery capsule) and bottom gesture pill. `src/um/Chrome.tsx` reads
  real values from `Date`, the Battery Status API and the Network Information API; unsupported
  fields are omitted instead of faked. Native Android builds suppress it so the OS chrome is not
  duplicated.
- Added a real `Device chrome` switch in Settings (`Preferences.showDeviceChrome`, persisted).
- Added screen and tab transition animations with `prefers-reduced-motion` support; tab bodies are
  keyed so the animation replays on every Home/Projects switch.
- Added `tools/video-frames.mjs`: joins split MP4 parts into one timeline, extracts 1 fps review
  frames and scene changes, and writes `analysis/manifest.json`, `scenes.json`, `frames.json` and
  `contact-sheet.html`. Generated frames are gitignored.
- Added `docs/VIDEO_SPLIT_GUIDE.md` (exact lossless and re-encode split commands) and
  `docs/VIDEO_AUDIT.md` (verified recording metadata, every failed download route, definition of
  done). The 301,626,146-byte Drive file exceeds the 100 MiB per-file transfer limit, so frame
  analysis is explicitly marked pending rather than claimed as complete.
- CI/release branch moved to `arena/a5fead45-editorshub` and tag to `v3.3.0`.
- `versionCode 10`, `versionName 3.3.0`, PWA cache `editorshub-v10`.

## 3.2.0 — responsive, fully wired editor (2026-11-04)

- Fixed dead timeline controls (eye, clip-back, keyframe, trim) caused by the timeline's pointer
  capture swallowing child clicks; keyframe diamond is always available.
- Corrected Move & Transform rail labels to match their panels; added a real Border & Shadow
  "Rounded corners" panel; eliminated duplicate drop-shadow effects and easing toast spam.
- Coalesced store mutations: one React notification per animation frame, one undo snapshot per
  gesture (pointerdown/focusin marks a new interaction), no `activePropKey` churn; 30 synchronous
  slider events now produce 1 notification and undo reverts the whole gesture.
- Capped non-playback compositing at 24 fps; zero long tasks under 4× CPU throttling for slider
  drags and 10-layer playback.
- Editing UI: teal readouts, swipe-to-move pad with corner marks, five-button left rail,
  magnifier/bolt glyph, reference-style property-detail top bar, 13 fresh screenshots.
- 143/143 interactive control checks, 12/12 correctness checks, zero console errors/warnings.
- `versionCode 9`, `versionName 3.2.0`, PWA cache `editorshub-v9`, CI/release bumped to `v3.2.0`.

## 3.1.0 — performance rebuild and pixel alignment (2026-11-04)

- Demand-driven canvas compositor: no idle rAF loop, capped/adaptive playback frame rate, reduced
  playback backing store, no overlapping frames, throttled media sync, visibility-aware.
- Imperative timeline: playhead/timecode/clip strip update via transform + text node; the timecode
  now advances during playback and scrubbing.
- Removed StrictMode double work, unused legacy `index.css`, boot-time full-size JPEG generation and
  redundant autosave clones; reduced built-in swatch size.
- Launcher, settings, create sheet, editor, Add/Shape sheets, property tiles, transport and FAB
  re-aligned to the supplied reference screenshots; real draggable selection handles; descriptive
  layer names; reference-style back/export glyphs and bottom navigation.
- 27/27 headless checks, zero console errors/warnings; 61 fps unthrottled, 38 fps at 4× CPU
  throttling with ten layers.
- `versionCode 8`, `versionName 3.1.0`, PWA cache `editorshub-v8`, CI/release bumped to `v3.1.0`.

All notable changes to this repository are documented here.

## 3.0.0 — 2026-11-04 (dark studio rebuild from the supplied screenshots)

### Rebuilt
- **Complete UI replacement** (`src/um/*`, `src/um.css`): near-black launcher, bold centred
  wordmark, gear/avatar, XML upload card, project-package URL/QR import, pill bottom nav,
  Projects / Your Templates / Elements segments, searchable element library, Settings sheet,
  Project/Element create sheet, black editor with 9:16 canvas, seven-icon transport, centre
  playhead and dotted ruler, striped clip bars, right zoom rail, teal FAB.
- **Add / Shape / Properties flow**: 11 Add tiles, 18+ real canvas shapes in a three-page picker,
  freehand PNG drawing, vector pen paths, camera layers, nulls and groups, and eight property
  sheets with left/right rails. Every control is wired to the real engine.
- **New engine features**: 28 shape kinds (including arcs, crescents, flowers, seals and custom
  SVG paths), a composition-wide camera layer transform, real cubic-bezier easings, audio
  resampling in the export mix, and low-quality preview rendering.

### Added
- **Real project-package import**: remote XML or zipped `.amproj`-style packages with embedded
  media restored from the ZIP; QR images decoded on-device with `BarcodeDetector` or `jsQR`.
- **Element library**: save any project as a re-importable XML element, search and reopen it.
- Real multi-layer select/copy/paste/paste-style/duplicate/delete, split, trim, masks, effects,
  motion presets, video / PNG frame ZIP / still PNG / XML export and autosave.

### Verified
- `npm run typecheck` and `npm run build` clean; headless Chromium E2E suites 17/17, 19/19 and
  10/10 passed with zero console errors or warnings (QR import, ZIP media restoration, XML and
  PNG downloads, undo/redo, effects, masks, multi-select paste, drawing pads, camera, presets,
  split, rename and element library).
- `versionCode 7`, `versionName 3.0.0`, PWA cache `editorshub-v7`, CI/release bumped to `v3.0.0`.

## 2.4.0 — 2026-11-04 (APK release, runtime fixes, closer proportions)

### Fixed
- **Package unified**: Capacitor `appId`, `applicationId`, `package_name` and `custom_url_scheme`
  are now `com.editorshub` (was `com.editorshub.motion`), matching the native project. The launcher
  activity is referenced by its fully-qualified class name so the namespace can differ safely.
- **Timeline scroll desync**: horizontal scrolling no longer detaches the playhead from the current
  time. `onScroll` now scrubs (`time = scrollLeft / zoom`) and the playhead stays centred.
- Regression audit: clip move/trim, split, duplicate, undo/redo, XML export/import, still export,
  project save/open, effects, keyframes and 134 inspector buttons all pass with zero console errors.

### Added / changed
- `EditorsHub-Motion.apk` attached to `apk-latest`, `v2.4.0` and `editorshub-latest` releases.
- New `ci/smoke-capacitor.sh` + emulator smoke job in `build-apk.yml` verifies the real APK launches.
- Default new project ratio changed to 4:5; mobile grid rows compacted (timeline 140 px,
  inspector 28dvh) so the composition preview fills ~75% of the screen width.
- `versionCode 6`, `versionName 2.4.0`, PWA cache `editorshub-v6`, CI/release bumped to `v2.4.0`.

## 2.3.0 — 2026-11-03 (pixel-level Alight Motion geometry)

### Added / changed
- Transport icons spread evenly across the full control-bar width, AM-style thin white glyphs.
- Contextual top bar: property-panel name + back + overflow when a panel is open; project name +
  settings + export otherwise.
- Panel replaces the bottom tab bar; sheet head with `▼ property` dropdown (real panel switching),
  left back/keyframes/fit/layers rail, labelled right rail with the big X close.
- Add menu: six AM categories including **Freehand Drawing**, teal selected icon box, All/Browse
  filter rail, two-column thumbnail grid, right rail All / Vector Drawing / Text / X.
- **Real freehand drawing pad**: brush colour, size, undo, clear; strokes become a PNG image layer.
- Timeline: flat clip colours, dark labels, per-clip hamburger (real Layer options), grey `‹`
  selected-layer arrow attached to the clip, real asset thumbnails, faint ruler watermark, short ticks.
- Launcher: classic Welcome card with three outlined pill buttons + tutorial card, all wired to
  real actions.
- Mobile grid clamped to viewport; top bar / sheet no longer overflow horizontally.
- `versionCode 5`, `versionName 2.3.0`, PWA cache `editorshub-v5`, CI/release bumped to `v2.3.0`.

## 2.2.0 — 2026-11-03 (Alight Motion fidelity)

### Added / changed
- Clean light-grey composition preview with a vertical `EditorsHub` watermark; removed the floating
  resolution/FPS badges, zoom toolbar, layer chip and search button from the canvas. Their real
  functions remain in Settings and the Add menu.
- Navigation bar reduced to back / name / settings gear / export; single-line title on phones.
- Single-row seven-icon transport bar; the old second ruler row was removed.
- **Centre-anchored playhead**: timeline layers scroll under a fixed centre playhead; drag-delta
  scrubbing stays stable; verified with headless Chromium during playback and scrubbing.
- AM-style ruler (tiny ticks, no numeric labels on phones), plain `mm:ss:ff` timecode above the
  playhead, layer chip with prev/next arrows above the clip bars.
- Pinned eye/thumbnail layer gutter; phone timeline toolbar hidden (actions remain in Layer
  options); circular outlined teal Add Layer FAB.
- Add menu reduced to a single left panel rail + a persistent right quick-action rail with the
  big **X** close; all buttons are real actions.
- Teal rings on all template circles; Settings gained a grid of real editor actions.
- `versionCode 4`, `versionName 2.2.0`; PWA cache `editorshub-v4`; CI/release bumped to `v2.2.0`.

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
