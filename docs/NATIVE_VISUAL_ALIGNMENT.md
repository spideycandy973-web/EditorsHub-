# Native Compose visual-alignment pass

> **Current design source of truth (2026-11-11): the
> [v4.5.1 release](https://github.com/spideycandy973-web/EditorsHub-/releases/tag/v4.5.1)
> UI/design.** The active branch builds a native Kotlin/Compose app with the
> restored v4.5.1 `--um-*` tokens and layout as its visual baseline. The legacy
> web/Capacitor source under `src/` is retained as the exact design reference,
> not as the shipped runtime. The reference-frame sheets and alignment notes
> below are historical research evidence, not a newer design target. Exact
> parity is not claimed.

## Evidence used

- `docs/reference-frames/key-states/key-0005.0s.jpg` — launcher.
- `docs/reference-frames/key-states/key-0018.0s.jpg` — Projects list.
- `docs/reference-frames/key-states/key-0030.0s.jpg` — Project/Element creation sheet.
- `docs/reference-frames/sheets/sheet-001.jpg` and `sheet-002.jpg` — tab and creation-flow transitions.
- `docs/reference-frames/key-states/key-0055.0s.jpg`, `key-0060.0s.jpg` — empty editor and populated timeline.
- `docs/reference-frames/key-states/key-0075.0s.jpg`, `key-0150.0s.jpg`, `key-0190.0s.jpg`, `key-0310.0s.jpg`, `key-0390.0s.jpg`, `key-0450.0s.jpg` — inspector/property/transform/effects states.
- `docs/reference-frames/key-states/key-0230.0s.jpg` — Add sheet and FAB close state.
- `docs/reference-frames/sheets/sheet-003.jpg` … `sheet-020.jpg` and `timeline-sheets/tl-001.jpg` — editor/timeline interaction sequence.
- `docs/screenshots/native-{home,projects,create}-v4.5.1.png` — CI-verified launcher, Projects and creation captures.
- `docs/screenshots/native-editor-v4.5.1.png` — empty 9:16 editor with transport, ruler, fixed playhead and FAB.
- `docs/screenshots/native-editor-add-v4.5.1.png` — reference-style Add sheet and rotating close FAB.
- `docs/screenshots/native-editor-{inspector,properties,transform,keyframe}-v4.5.1.png` — text inspector, property grid, scrolled Transform panel and a real timeline keyframe marker.

The reference stills are 610×1356, half the 1220×2712 recording, and both the
reference device and the Pixel 5 CI emulator use approximately 2.75 px/dp.
Measurements below therefore compare native pixels directly with 2× reference-still
pixels rather than stretching the taller reference screen to the emulator's
aspect ratio.

## Measured launcher corrections at 2.75 px/dp

| Element | Reference | Previous native | Change |
| --- | ---: | ---: | --- |
| First card top | ~366 px | ~362 px | +2 dp content top padding |
| Upload card height | ~278 px / 101 dp | ~263 px / 96 dp | 101 dp |
| Gap between cards | ~38 px / 14 dp | ~40 px / 14.5 dp | keep 14 dp |
| Package card height | ~668 px / 243 dp | ~645 px / 235 dp | 243 dp with more top/bottom padding |
| Navigation pill height | ~188 px / 68 dp | ~180 px / 65.5 dp | 69 dp |
| Navigation pill bottom from screen edge | ~98 px / 35.6 dp | ~89 px / 32.4 dp | 92 dp bar with centered 69 dp pill |
| FAB diameter | ~136 px / 49.5 dp | ~132 px / 48 dp | 50 dp |

The pill width is now proportional (about 57.5% of available width, capped at
254 dp) instead of a hard-coded 240 dp. This preserves the reference proportion
on both the taller source device and the narrower CI emulator.

## Projects and creation-state corrections

- The three in-screen tabs are now `Projects / Your Templates / Elements`, with
  a real selected state and section-specific search; they no longer navigate away
  to separate full-screen destinations.
- `Your Templates` opens real editable compositions from `TemplateCatalog`.
- `Elements` is backed by real persisted projects in `HomeViewModel`; creating
  an element creates and saves a composition, and removing one returns its
  project to the Projects list. The empty state's create action is functional.
- The Projects header now follows the reference order: tabs, search field, then
  the `Your projects` / `Sort by Date` row.
- Project rows use the reference's larger 92 dp cards, real thumbnail with
  duration badge, date-group headings, and icon-backed duration/aspect/size/fps
  metadata. Missing or corrupt thumbnails still show the visible fallback.
- The creation sheet is now a bottom-aligned compact overlay with working
  `Project`/`Element` tabs, name field, ratio chips, editable/swap resolution,
  frame-rate and background dropdowns, and an expandable advanced section that
  retains duration, background type/colors, gradient angle and timebase controls.
- The bottom FAB rotates from `+` to `×` while the sheet is open and closes it;
  the scrim also dismisses the sheet.

## Editor and timeline corrections

The editor/timeline pass replaces the earlier desktop-style DAW timeline with the
reference's mobile layout while keeping every editing target functional:

| Element | Reference evidence | Native implementation |
| --- | --- | --- |
| Top strip / toolbar | ~65 px status strip, ~55 px dark toolbar in `key-0055.0s.jpg` | 34 dp breathing strip + 42 dp dark toolbar; back, project title, real tools menu and export icon |
| Preview | 9:16 canvas ~386 reference-still px wide (~281 dp at 2×/2.75) | `PreviewSurface` fills the remaining weight, centered by aspect ratio; square zoom button with real preview zoom |
| Transport | 7 thin outline icons in a ~63 px band | 48 dp custom single-stroke undo/redo/start/play/end/duplicate/fullscreen buttons |
| Ruler / playhead | dense ticks, no time labels, centre white playhead, rounded timecode pill | 22 dp tick Canvas, fixed 50%-width playhead, 21 dp timecode pill; pan scrubs and pinch changes real zoom |
| Tracks | compact eye/thumbnail chip, one 28 dp clip row, filmstrip thumbnails | 28 dp rows, real rendered `LayerFilmstrip` tiles, visibility toggle, clip move/trim, audio waveform |
| Default timeline | deep black well below the clip, mint 50 dp FAB | 210 dp flexible timeline, 50 dp mint FAB with animated `+`→`×` |
| Inspector | ~54 dp left/right rails and ~220 dp centre panel | 54 dp rails, 210 dp panel; diamond/keyframes/mask rail, real move/duplicate/split/delete/close rail |
| Add sheet | dimmed screen, 4×2 grid plus Camera/Null/Empty Group bottom row | 292 dp bottom sheet with 11 real actions, `NEW` Template badge, chevrons, scrim dismissal |
| Properties | five compact tool buttons, 3-tile then 4-tile grid | real timing/trim/split/mute buttons and tiles for Color, Border, Blending, Transform, Shape, Presets, Effects |
| Transform | numeric X/Y/Z boxes and "Swipe here to move layer" pad | real resolved X/Y/rotation boxes and a drag pad writing Transform tracks; all sliders remain below |
| Text toolbar | hamburger, font/size dropdowns, colour swatch and check | real `TextSpec` font, weight, italic, size, colour and uppercase controls |
| Effects | thumbnail grid and parameter diamonds | real rendered effect demo thumbnails, animated-parameter count, and effect-parameter keyframe diamonds |

Thumbnail and keyframe regressions are treated as part of this pass:
`ThumbnailGenerator` now draws a visible branded poster for empty compositions
or media layers pointing at missing/zero-byte files; corrupt/stale PNG files are
still atomically replaced. Timeline chips and clips show actual rendered frames,
with a visible "No frame" fallback. Transform/Effects diamonds, the Keyframes
panel, the graph editor's pure drag mapping, and timeline markers all remain
wired to the same real `KeyframeTrack` data. `EditorKeyframeRegressionTest`
exercises those UI paths; `KeyframeContractTest` and `ThumbnailGeneratorTest`
cover the engine and fallback contracts.

The reference device is taller than the Pixel 5 emulator, so the preview and the
empty timeline well flex with the available height. These measurements are
alignment targets, not an exact-parity claim.

## Verification

`.github/workflows/android-ci.yml` builds/tests the native APK and uploads
nine native state captures: `native-home.png`, `native-projects.png`,
`native-create.png`, `native-editor.png`, `native-editor-add.png`,
`native-editor-inspector.png`, `native-editor-properties.png`,
`native-editor-transform.png` and `native-editor-keyframe.png`.
`ci/capture-native-states.py` uses UI Automator bounds rather than hard-coded
coordinates, and scrolls the inspector to reach the first Transform keyframe
diamond. `.github/workflows/reference-frames.yml` imports those captures into
`docs/screenshots/` with a `capture_run_id` workflow input.

### v4.5.1 baseline verification

- Android CI run `43114300889` on commit `8068c11` succeeded: debug/release
  Kotlin compilation, unit tests, all 18 instrumentation tests, APK packaging,
  emulator launch smoke, and all nine state captures.
- Visual-evidence import run `43116353207` copied all nine captures into
  `docs/screenshots/native-*-v4.5.1.png`; commit `c05bae1` recorded them.
- The home, empty editor, Add-sheet and keyframe captures were reviewed against
  the v4.5.1 design. The home shows the v4.5.1 lavender Upload/Import buttons,
  mint FAB, graphite cards and ULTRA MOTION wordmark. The empty editor shows the
  dark canvas, transport, ruler, centre playhead and FAB. The Add sheet and
  keyframe inspector use the restored `--um-*` tokens and show real controls,
  including a green timeline keyframe diamond and lavender Transform sliders.
- These are alignment/working-control checks, not pixel-exact parity. The
  Add-sheet, inspector and Transform layouts come from the native
  reference-matched implementation rather than the older web sheets.

### Historical pre-rollback verification

- Android CI run `42988769349` succeeded on commit `53acf58`: debug/release
  Kotlin compilation, unit tests, all 18 instrumentation tests (including
  `EditorKeyframeRegressionTest`, `ThumbnailGeneratorTest` and the other
  emulator regressions), APK packaging, emulator launch smoke and UI-state
  capture all passed. Final CI run `42991308561` on commit `0f34f0c` also
  passed with all nine state captures produced.
- Visual-evidence import run `42990761782` copied all nine captures into
  `docs/screenshots/`; commit `135338d` recorded the imported evidence.
- The captured empty editor shows the dark toolbar, centered 9:16 preview,
  seven-icon transport, dense ruler, fixed centre playhead, timecode pill and
  empty timeline well. The Add-sheet capture shows the dimmed 11-action sheet,
  `NEW` Template badge and rotating FAB. The inspector capture shows the text
  toolbar and real Text panel; the properties capture shows the compact tool
  row and property tile grid; the transform capture shows the scrolled
  auto-key/Transform controls; and the keyframe capture shows a real green
  keyframe diamond added to the selected Text clip by a UI Automator tap.
- `EditorKeyframeRegressionTest` covers Transform diamond → timeline marker →
  Keyframes panel → Add keyframe → graph drag → Gaussian Blur effect → Radius
  diamond. The marker uses a `combinedClickable` control with an unrotated hit
  target; the test invokes its semantics click action directly, while the
  capture evidence verifies the real adb tap on the Transform diamond and the
  resulting timeline marker. `KeyframeContractTest` and
  `ThumbnailGeneratorTest` cover the engine, graph-drag and thumbnail-fallback
  contracts.
- Thumbnail fallbacks and keyframe behavior were left intact and are covered by
  the passing suite: real rendered project/effect/timeline frames, visible
  posters for empty/corrupt data, and real animated tracks from every keyframe
  entry point.

The reference device is taller than the Pixel 5 emulator, so the preview and the
empty timeline well flex with the available height. The current visual target is
the v4.5.1 release design, not the reference-frame alignment pass. The imported
captures show working native controls with v4.5.1 tokens, but this document
still does **not** claim pixel-exact parity: remaining differences include the
reference device's taller screen, populated project rows (CI starts from
cleared app data), proprietary artwork/fonts, reference-only animation detail,
and the fact that the Latest release APK is the historical Capacitor build while
this branch builds a native Compose app.
