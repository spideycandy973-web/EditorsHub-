# Reference APK feature matrix — v4.3.0

This table was derived from the reverse-engineering evidence committed in
[`apk-analysis/`](../apk-analysis/): `manifest.json` (72 activities, from
APKTool), `rea-package.json`/`rea-classes.json` (REA + JADX MCP, 37,206
classes), `native.json`/`ghidra.log` (Ghidra 12.2 headless, 43 native
functions), `values.json` (3,310 strings), `layouts.json` (250 layout trees),
`drawables.json` (2,208 resource records), `ui-strings.json` and `tools.json`.

Only **user-facing editor features** are listed. Account, paywall, ad-network,
firebase-auth and telemetry activities are intentionally excluded: they would
be decorative controls pretending to work, which this project does not ship.
Everything below is an original implementation backed by the real engine.

## Launcher / projects

| APK activity / evidence | Feature | Implementation |
| --- | --- | --- |
| `MainActivity` | Launcher wordmark, gear, avatar, Upload XML, Import Project Package, QR scan, pill nav | `src/app/Home.tsx`, `src/app/Splash.tsx` |
| `ImportActivity`, `ElementDownloadActivity` | XML file / URL / QR / zipped package import with compatibility report | `src/app/Overlays.tsx` (`XmlReportBody`), `src/editor/xml.ts`, `src/app/lib.ts` |
| `SettingsActivity` | Low Quality Preview, Show Off Timeline, Remember Add page, Default Layer Duration, Audio Resampling, Website/Telegram/TikTok, About | `src/app/Home.tsx` (`SettingsBody`) |
| `AboutActivity` | Wordmark, domain, live device/build info | `src/app/AboutPage.tsx` |
| `ColorPickerActivity` | Solid / gradient / hex / palette / hue colour picker | `src/app/FeatureSheets.tsx` (`ColorPickerBody`), `src/app/PropertySheets.tsx` (`ColorBody`) |
| `FontBrowserActivity` | Font list with live preview, custom font import | `src/app/FeatureSheets.tsx` (`FontBrowserBody`) |
| `OpenSourceLibrariesActivity` | Dependency/licence list | `src/app/FeatureSheets.tsx` (`OpenSourceBody`) |
| `TutorialActivity`, `OnboardingActivity` | Step-by-step guided tour with real "open screen" actions | `src/app/FeatureSheets.tsx` (`TutorialBody`) |
| `SaveToStorageActivity` | Save project, download JSON, save as element | `src/app/FeatureSheets.tsx` (`SaveToStorageBody`) |
| `NewFeaturePagerActivity` | What's-new / release notes | `RELEASE_NOTES.md`, `CHANGELOG.md` |

## Editor

| APK activity / evidence | Feature | Implementation |
| --- | --- | --- |
| `EditActivity` | Portrait canvas, top bar, right zoom rail, transport, ruler, playhead, clip timeline, selection handles | `src/app/Editor.tsx`, `src/app/Root.tsx` |
| `ExportPreviewActivity` | Real frame preview before export | `src/app/Flows.tsx` (`ExportPreviewFlow`) |
| `TemplateImportPreviewActivity` | Template editor, replace media, "Are you done replacing media?" gate | `src/app/Flows.tsx` (`ReplaceMediaFlow`) |
| `PresetPreviewActivity` | Saved presets, "No presets… yet!", Discover Presets, motion gallery | `src/app/PresetsDialog.tsx`, `src/app/FeatureSheets.tsx` (`PresetPreviewBody`) |
| `EffectBrowserActivity` | 32 categories, quick picks, Guide cards, Standard Settings, favourites | `src/app/EffectBrowser.tsx` |
| `MediaBrowser`, `AudioBrowserActivity` | Imported media/audio browser with real add-to-project | `src/app/FeatureSheets.tsx` (`MediaBrowserBody`) |
| `FontBrowserActivity` | Missing-fonts report on import (`document.fonts.check`) | `src/app/Flows.tsx`, `src/app/Root.tsx` |

### Editor controls recovered from the reference JPGs and video

| Control | Implementation |
| --- | --- |
| Add sheet: Media, Text, Shape, Audio, Freehand/Vector Drawing, Element/Project, Template, Camera, Null, Empty Group | `src/app/EditorSheets.tsx` |
| Shape picker: 5 pages, 18 built-ins + extras + polygon/star variants + 18 original vector silhouettes | `src/app/EditorSheets.tsx`, `src/app/ShapeGlyph.tsx` |
| Overflow menu: Select All Layers, Duplicate, Save Preset (NEW), Copy incl. style, Paste Layer, Paste Style | `src/app/EditorSheets.tsx` |
| Project Settings: ratio chips, 720p–2160p, 12–240 fps, background, **Total Edit Time**, advanced playback/camera options, X close | `src/app/Overlays.tsx` (`ProjectSettingsBody`) |
| Move & Transform: X/Y/Z pad with corner brackets, 200.0 width/height with chain link, X/Y skew, rotation dial, cubic bezier easing, **Keyframes Required** hint | `src/app/PropertySheets.tsx` |
| Border & Shadow: **Add Border** empty state, **Stroke** with 5 dash styles, colour toggle, 4.0 width, **Start/End trim**, Shadow Size/Alpha/Position X/Y | `src/app/PropertySheets.tsx` (`BorderBody`), `src/editor/renderer.ts` |
| Blending & Opacity: opacity slider, **Contrast / Difference / Color / Mask** accordion groups with real blend thumbnails, mask feather/invert | `src/app/PropertySheets.tsx` (`BlendBody`) |
| Edit Shape: Size, Radius, star points, inner radius | `src/app/PropertySheets.tsx` |
| Effects: 44 real effects, effect stack, keyframes, + Add Effect | `src/editor/types.ts`, `src/editor/renderer.ts`, `src/app/PropertySheets.tsx` |
| Camera Options: **Active Camera View** (renders through camera POV), **Force Blur**, FOV/zoom/pan readouts, camera X frustum, magenta camera clip, hint tooltip | `src/app/PropertySheets.tsx` (`CameraBody`), `src/editor/store.ts`, `src/editor/renderer.ts`, `src/app/Editor.tsx` |
| Blend modes, masks, keyframes, trim/split, undo/redo, snapping, auto-keyframes | `src/editor/store.ts`, `src/editor/animation.ts` |
| Six export formats (video, package, PNG, PNG sequence, XML, GIF89a) | `src/editor/export.ts`, `src/editor/gif.ts`, `src/app/Overlays.tsx` |
| Portable XML project import/export | `src/editor/xml.ts` |

## New in v4.3.0

- **Editor geometry rebuilt** from the 20 reference JPGs: status 0–34, top bar
  69–112 (#262628), portrait canvas 269×480 at x=80/y=113, transport,
  ruler/clip timeline, property pane 728–936 with 64px left / 56px right rails.
- **Default composition is 9:16 portrait** (the reference editor canvas).
- **Active Camera View** + **Force Blur** engine flags with real compositor
  effects; camera hint tooltip opens Camera Options.
- **Stroke** dash styles, Start/End trim and **Add Border** empty state wired to
  real canvas rendering.
- **Blend accordion groups** with working blend-mode thumbnails.
- **Project Settings** rebuilt with ratio chips, resolution/fps/background and a
  live **Total Edit Time** counter.
- **Nine APK-derived screens** added (colour picker, font/media/audio browsers,
  save to storage, open source libraries, tutorial, preset preview, and a fully
  persisted **in-app brand editor**).
- **In-app identity customisation**: wordmark, app name, domain, website,
  About text and three palette colours are editable and apply immediately.
  No third-party name or artwork is baked in — set whatever you want.

## Verification

`npm run typecheck`, `npm run build`, `node tools/verify-gif.mjs` (7/7),
`node tools/smoke.mjs --shots` (headless, zero console errors) plus the
measured comparison in [`VISUAL_AUDIT.md`](VISUAL_AUDIT.md) and
`tools/visual-audit.py`.
