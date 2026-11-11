# Native Compose visual-alignment pass

This is an evidence-based alignment pass for the **native Kotlin/Compose** app. It
does not claim exact parity with Ultra Motion v8.0.1; the imported reference
frames and native CI captures remain the source of truth.

## Evidence used

- `docs/reference-frames/key-states/key-0005.0s.jpg` — launcher.
- `docs/reference-frames/key-states/key-0018.0s.jpg` — Projects list.
- `docs/reference-frames/key-states/key-0030.0s.jpg` — Project/Element creation sheet.
- `docs/reference-frames/sheets/sheet-001.jpg` and `sheet-002.jpg` — tab and creation-flow transitions.
- `docs/reference-frames/key-states/key-0055.0s.jpg`, `key-0060.0s.jpg` — empty editor and populated timeline.
- `docs/reference-frames/key-states/key-0075.0s.jpg`, `key-0150.0s.jpg`, `key-0190.0s.jpg`, `key-0310.0s.jpg`, `key-0390.0s.jpg`, `key-0450.0s.jpg` — inspector/property/transform/effects states.
- `docs/reference-frames/key-states/key-0230.0s.jpg` — Add sheet and FAB close state.
- `docs/reference-frames/sheets/sheet-003.jpg` … `sheet-020.jpg` and `timeline-sheets/tl-001.jpg` — editor/timeline interaction sequence.
- `docs/screenshots/native-home-v4.5.2.png` — pre-change native launcher capture from Android CI.

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
`native-home.png`, `native-projects.png`, `native-create.png`, and the editor,
Add-sheet, inspector, properties, transform and keyframe captures.
`ci/capture-native-states.py` uses UI Automator bounds rather than hard-coded
coordinates. `.github/workflows/reference-frames.yml` can import those captures
into `docs/screenshots/` with a `capture_run_id` workflow input. Visual parity
must be checked against those imported captures before making any exact-parity
claim.

### Current imported evidence

- Android CI run `42923181870` (build, unit tests, instrumentation tests and
  emulator smoke) succeeded on commit `23994f1`.
- Visual-evidence import run `42925036568` copied
  `native-home-v4.5.2.png`, `native-projects-v4.5.2.png` and
  `native-create-v4.5.2.png` from that CI run.
- The native Projects header now follows the reference's tab → search →
  section-heading order, and the creation sheet is bottom-aligned with the
  FAB-to-close state visible. Remaining differences include the reference
  device's taller screen, populated project rows (CI starts from cleared app
  data), and the still-unverified editor/timeline state.
