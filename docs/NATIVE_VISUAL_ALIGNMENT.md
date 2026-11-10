# Native Compose visual-alignment pass

This is an evidence-based alignment pass for the **native Kotlin/Compose** app. It
does not claim exact parity with Ultra Motion v8.0.1; the imported reference
frames and native CI captures remain the source of truth.

## Evidence used

- `docs/reference-frames/key-states/key-0005.0s.jpg` — launcher.
- `docs/reference-frames/key-states/key-0018.0s.jpg` — Projects list.
- `docs/reference-frames/key-states/key-0030.0s.jpg` — Project/Element creation sheet.
- `docs/reference-frames/sheets/sheet-001.jpg` and `sheet-002.jpg` — tab and creation-flow transitions.
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
- Project rows use the reference's larger 92 dp cards, real thumbnail with
  duration badge, date-group headings, and icon-backed duration/aspect/size/fps
  metadata. Missing or corrupt thumbnails still show the visible fallback.
- The creation sheet is now a bottom-aligned compact overlay with working
  `Project`/`Element` tabs, name field, ratio chips, editable/swap resolution,
  frame-rate and background dropdowns, and an expandable advanced section that
  retains duration, background type/colors, gradient angle and timebase controls.
- The bottom FAB rotates from `+` to `×` while the sheet is open and closes it;
  the scrim also dismisses the sheet.

## Verification

`.github/workflows/android-ci.yml` builds/tests the native APK and uploads
`native-home.png`, `native-projects.png` and `native-create.png`.
`ci/capture-native-states.py` uses UI Automator bounds rather than hard-coded
coordinates. `.github/workflows/reference-frames.yml` can import those captures
into `docs/screenshots/` with a `capture_run_id` workflow input. Visual parity
must be checked against those imported captures before making any exact-parity
claim.
