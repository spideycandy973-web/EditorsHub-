# Changelog

## 2026-11-14 — EditorsHub Name, English Interface & New Logo

### Name and logo

- The app is now called **EditorsHub** everywhere: page title, splash screen, home screen, About dialog, install prompt, share text, default text layer and the notices that name the app.
- New logo on the splash screen and home screen, and new app icons (`icons/editorshub-192.png`, `-512.png`, `-maskable-512.png`, plus the favicon and Apple touch icon). `icons/editorshub-logo.png` is the full-size logo. The old `open-motion-*` icons are removed.
- `manifest.webmanifest` and `sw.js` use the new name and icons. The service worker cache is renamed, and caches written by earlier builds are deleted when the new worker activates.
- Saved files use the new name: `*.editorshub.json`, `*.editorshub.xml`, `editorshub-export.mp4` and the preset and diagnostics downloads.
- Kept on purpose so existing projects still open: the format ids inside saved files, the XML root element and the browser storage names. Some of these still begin with `open-motion`.

### English interface

- The interface is English: about 2,700 strings in menus, panels, dialogs, effect and preset names, hints, toasts and error messages were translated from Indonesian.
- English is the default and the fallback language. Shape, layer, group and template names that the app creates are English.
- Effect categories, text preset categories and parameter groups were renamed consistently (for example **All**, **Color**, **Motion**, **Custom**), including the places where the code compares those names.
- The language picker still works. As before, the other 13 languages cover the home screen and the main inspector labels; everything else is shown in English.
- Numbers and dates no longer fall back to the Indonesian format.

### Licenses

- About → Licenses lists the third-party code that is inside the app, each with its license text: the base application code, FishTools-Studio, Motionity (reference), Acorn and the EffectCraft camera tracker with its Rust dependency notices.
- `THIRD_PARTY_NOTICES.txt` is rebuilt from that same list. Components that are only downloaded at runtime (Mediabunny, ONNX Runtime Web, MODNet) are named in a short separate section.
- The top-level `LICENSE` file is removed. The MIT notice it carried for the base application code is kept in `THIRD_PARTY_NOTICES.txt` and in the app.
- The start-up notice no longer describes the upstream project's roadmap.
- The credits and source-link lists are removed from the About dialog, and the links to the upstream repository are removed from the older entries of this changelog. The license notices listed above stay.

### Interface fixes

- Home screen on phones: the app name is no longer cut off. Below 480 px the **AM LINK** button shows its icon only, and below 416 px **IMPORT** does too.
- Home screen on phones, **Recent Projects**: cards are listed one per row below 640 px. Two cards side by side left about 40 px for the text, so a project name was cut to three letters.
- Home screen on phones, **Tutorial** tab: card descriptions wrap to up to three lines instead of one line cut off mid-word.
- Home screen on phones, wording: the tagline no longer names another product, the **Templates** card title fits on one line like its neighbours, and the tab is labelled **Templates**.
- Home screen on desktop: the **No projects yet** heading was white on a light background and could not be read.
- Text contrast is raised to at least 4.5:1 for the small grey text on the home screen, the language picker, the export sheet and the effect stack note.
- The timecode button no longer points assistive technology at a dialog that does not exist until it is opened.
- The language button, the project title, the timecode button and the **Blending & Opacity** tile now include the text they show in their accessible name (the language code, the project name, the current time). Their hints stay available as the description.
- Upstream build numbers are removed from headings and effect descriptions.

## 2026-11-14 — Layer Bar, Template Cards, Storage Cleanup & Safety Fixes

### Editor fixes

- Make the layer header bar work: the trash button deletes the layer and the name field renames it. Both called `history.push()` from a late patch script, where `history` is the browser's History object, so they threw and did nothing. Renaming is undoable and refreshes the timeline row.
- Fix the Inspector **Tautkan Ulang** tile throwing `openRelinkMenu is not defined` when tapped right after the Inspector opened.
- Fix a malformed `transition` declaration on the transform number popover; its `width` part was ignored.

### Home and New Project dialog

- Home template cards now always start a new project and apply the template to it. They called a function that does not exist and fell back to the blank New Project dialog, or added the template to whichever project was still loaded.
- Add the missing `fade-scale` template used by the fourth Home template card.
- Remove the PROYEK / ELEMEN switch from the New Project dialog; it had no function.
- Make the New Project dialog title readable on phones and tablets (dark text on the dark sheet).

### Browser storage

- Deleting projects on Home now also removes stored media that no remaining project uses. Media records are shared by asset id, so anything still referenced by another project, a local fallback snapshot or the open project's undo stack is kept.

### Safety

- Escape the project name wherever the editor title is rewritten. A project file with markup in its name could run script when the title was tapped.
- AM Link export: accept only an http(s) link from the publish response and escape it in the result dialog.
- Project import: keep the canvas (16–8192 px), frame rate (1–120) and duration inside the New Project limits, ignore non-object layer entries and refuse files with more than 10,000 layers.

### Accessibility

- Label the form controls that had none (New Project dialog, export sheet, canvas zoom) and mark the New Project dialog as a dialog.

## 2026-11-08 — Timeline Playhead, Layer Header & Export Menu Fixes

## Timeline and playhead

- Fix the playhead line stopping prematurely instead of extending to the bottom of the timeline.
- Add frame-by-frame snapping based on the timeline ruler.
- Fix inconsistent playhead positioning and unwanted drifting during timeline interaction.

## Layer interface

- Add a dedicated header when selecting a layer.

## Export and Share menus

- Fix crashes and performance issues when opening or interacting with the Export and Share menus.

## Source code optimization

- Streamline the source code to reduce the overall HTML file size.

## 2026-10-30 — Timeline Markers, Home About & Recent Projects

### Timeline markers and Alight Motion bookmarks

- Import native AM Link/XML `<bookmark>` nodes, retaining millisecond times and appearance indices. Keep nested-scene bookmarks in their own group workspace, with the same trim, in-point and speed mapping as the group's contents.
- Show colored marker flags at the top of the ruler. Tap the live timecode to add/remove a current-frame marker; hold for 500 ms to open marker settings without changing markers on release.
- Rename, retime, recolor, delete, and navigate markers through the marker settings dialog. Share data with the expression marker API and retain Undo/Redo, project JSON and additive EditorsHub XML persistence.
- Use the existing timeline start/end buttons to seek previous/next markers on tap and composition boundaries on hold. From the first/last marker frame, one additional tap reaches the corresponding timeline edge. Apply the same behavior inside groups; keep the separate keyframe buttons unchanged.
- Preserve imported times while mapping display/seek positions to frames. Keep out-of-span imported bookmarks in project data. Increase the shared marker access budget to 4096. Add root bookmark nodes to the existing AM XML exporter; this does not replace the legacy layer exporter with a complete native AM exporter.
- Support mouse, touch and keyboard operation, including gesture cancellation, focus restoration and Back/Escape handling. No separate marker toolbar is added.

### Home About, credits and recent projects

- Open **About EditorsHub** from the existing Home logo/wordmark without requiring an open project. Include searchable credits, source links, full offline license notices and a bundled `THIRD_PARTY_NOTICES.txt` copy.
- Surface existing library/reference/community notices and the pinned Mediabunny MPL-2.0 notice. Keep Alight Motion/Aurea compatibility references separate from verified open-source licenses. Retain the original embedded notices.
- Fix unresponsive cards in **Recent Projects**: handle the actual click/tap or Enter/Space on each card rather than forwarding an untrusted synthetic click to another card. Preserve project storage, thumbnails, selection behavior and homepage-first navigation guards.

### Preserve published changes and refresh the PWA

- Merge and retain the ruler stacking-order and start-of-project gap fixes that were already published. Keep every prior changelog entry and all unrelated repository files, including README, unchanged.
- Refresh only the service worker's shell-cache identity; retain its network-first navigation and exclusion of imported media/project storage.
- Local Chrome checks cover marker navigation, group boundaries, imported bookmarks, gesture cancellation, timecode controls, recent project loading and About. Physical phones, live resolver services, full native AM visual parity and complete video export remain outside this release's validation.

## 2026-10-23 — Timeline Ruler: Start-of-Project Gap

- Cover the leading blank area to the left of the time ruler at 0 seconds and the first frames, so vertically scrolled layer headers cannot show through above the track boundary.
- Let that area receive ruler input rather than selecting the hidden layer underneath. Retain the previous stacking-order correction, zero/tick positions, horizontal scrolling and playhead visibility.
- Refresh the PWA shell-cache identity for this build.
- Validation: 166 scoped browser checks passed in four desktop/mobile layouts, including the entire leading gap at 0 seconds, frames 1 and 10, later times, vertical scrolling, ruler input and layer controls. The v1 gap was reproduced. Physical phones were not tested.

## 2026-10-23 — Timeline Ruler Visibility

- Keep sticky layer headers below the fixed time ruler when vertically scrolling the timeline, including the left side during horizontal scrolling.
- Preserve playhead visibility, ruler seeking, horizontal timeline zoom/pan, and layer controls. Only the header stacking order changes; project data and application scripts are unchanged.
- Refresh the PWA shell-cache identity for this build.
- Validation: 181 scoped browser checks passed across two portrait phone sizes, mobile landscape and desktop, including real touch gestures, ruler seeking, layer visibility controls and animation-data preservation. No uncaught page errors. Physical phones were not tested.

## 2026-10-23 — AM Template Workflow, Effect Geometry & Element Labels

### AM Link starts in Template Editor

- After a successful AM Link download/import, close the import dialog and open the existing Template Editor automatically, for official, premium and third-party resolver choices.
- Replace shared photo, video and audio sources before continuing through **Lanjut → Buka Timeline** or **Ekspor**. Retain clip timing, keyframes, effects and shared-source slot grouping.
- Update the import button, description and Home tooltip to explain this flow. Failed/cancelled imports remain in their existing dialogs; reopening a saved project, other file imports and missing-media recovery do not gain a new automatic Template Editor step.

### Native AM layer geometry

- Correct the shared shader contract: `acLayerSize` is the untransformed primitive/content size, not the extent after layer or parent scaling, rotation or mirroring.
- Keep logical layer/scene sizes, centers and motion vectors separate from preview raster buffers, FBOs and texture dimensions. Preserve shader source, imported values and keyframes.
- Fix the Turbulent Displace pattern distortion in the supplied Renaxprst 9:16 project. The shared correction also covers live size readers such as Tiles, Noise, Displacement Map, Fractal Warp and procedural patterns.
- Audit 315 registered effect definitions: 104 have live layer-size consumers in WebGL1/2; three other Swirl definitions have unused size expressions optimized out by the compiler. This is not a claim of complete native Android effect parity.

### Element labels and PWA refresh

- Restore **Kamera**, **Objek Null**, **Grup Kosong** and **Elemen** labels. Limit icon-only text hiding to the actual shape grid; retain readable icon-above-text object cards and scrolling in short panels.
- Refresh the offline shell cache for this release without changing imported media or project storage.

### Verification and limitations

- 64 local import-flow/UI checks pass on the new HTML in isolated Chrome desktop and mobile emulation, covering all three resolver choices, delayed completion, errors/cancellation, project selection, empty templates, media replacement and returning to/reopening the timeline. Resolver responses are mocked; these are not live premium-service or physical-phone tests.
- The retained geometry implementation was separately verified with 642 WebGL1/2 graph variants, 48 supplemental variants, 1,544 numeric contract assertions, 12 user-project frame cases, 16 real local/composition/parent/group/animated-bounds route cases and 295 UI regressions. Its shader/runtime changes are preserved unchanged by the import-flow update.
- Physical iPhone/iPad/Android GPUs, full MP4 encoding/muxing and full native AM visual parity remain unverified. This release does not include the unfinished V5.300/V5.301 experiments or fix the separate AM effect-readiness export failure.

## 2026-10-23 — Home Banner, Media Recovery & Preview Warnings

Previously published; this entry backfills the missing changelog.

- Fix the giant desktop download icon caused by install-banner CSS being limited to mobile media queries. Keep the banner compact, dismissible and hidden in standalone mode.
- Let media recovery explicitly choose premium, official anonymous (5 MB maximum package), or third-party resolver, with provider-specific notes and no automatic provider switching.
- Recover media without reimporting layers or changing IDs, animations, effects, trim or relationships. Reject stale recovery results after switching projects; commit the resolver choice only after media is complete and recovery starts.
- Add a focusable dismiss button and readable controls to preview warnings. Clear recovered/deleted/hidden/replaced media errors, ignore late events from obsolete sources and avoid video-codec advice for image failures.
- Historical validation: 121 added checks and 755 scoped checks across the then-published build. Live resolver downloads, physical iPhone/iPad/Infinix and full 4K export were not verified. The static 1080p/4K encoder probe was diagnostic evidence only; no encoder fallback policy was changed.

## 2026-10-23 — Mobile Compatibility, Resizable Panels & Freehand

Previously published; this entry backfills the missing changelog.

- Add separate photo, video, audio and Files picker paths, safer MIME fallback and bounded media-probe cleanup for browser/device compatibility.
- Guard video/audio proxy ownership, pending playback and background teardown to prevent overlapping playback.
- Keep the timeline visible alongside responsive inspector/Add panels. Center resize grips inside existing panel headers, remember per-layout sizes, and let Add content fill the resized area with vertical overflow and retained shape paging.
- Add adjustable freehand stroke smoothing, optional stylus-pressure width and single-tap dots as editable vector outlines, with Undo/Redo and XML persistence.
- Keep Bezier handles reachable without rewriting curve overshoot. Expose GPU/media failures and retain a limited 2D startup fallback.
- Preserve the PWA, AM share/export, project thumbnails, keyboard shortcuts and the prior export-resolution fixes.
- Historical validation: 634 scoped local checks on the then-published build. Physical iPhone/iPad/Infinix, hardware stylus and complete video export remain unverified; not every black-preview case is confirmed fixed.

## 2026-10-22 — Export Resolution & Diagnostics

### Export resolution detection

- Fix the misleading 480p ceiling: GPU Fast and the other WebCodecs export engines now query their encoder configuration instead of being limited by Media Capabilities recording results.
- Match capability checks to the selected engine, output dimensions, FPS, quality bitrate and encoder preference. Recheck support after engine, codec, FPS or quality changes.
- Preserve an existing resolution choice when it remains available, debounce quality-slider checks and ignore stale asynchronous responses.
- Distinguish unsupported configurations from unavailable, failed or timed-out capability queries. Unknown choices are explicitly marked unverified rather than reported as a detected device limit.
- Remove the fabricated low-resolution maximum when every configuration is rejected; ask for another engine, codec or FPS instead. Unverified options up to 4K are not a guarantee of successful export.

### Diagnostics and export UI

- Retain detailed capability matrices, optional user-reported device/browser information and GPU effect-readiness failure context in the Diagnostics panel.
- Remove the direct "Diagnostik resolusi & GPU Fast" shortcut from the export sheet.
- Capability queries do not instantiate encoders, decoders or additional WebGL contexts. Codec profiles, rendering algorithms and output quality remain unchanged.

### Verification and limitations

- 345 local checks passed: 75 resolution-policy checks, 80 diagnostics checks and 190 UI/keyboard/community-feature regression checks in desktop/mobile-size Chrome and WebGL1 contexts.
- Capability responses used by the tests are fixtures, not measurements from an iPhone 17. Physical Safari/iPhone and complete video exports remain unverified; a supported configuration is not proof that a full project will export successfully.
- The separate AM effect-readiness failure is not fixed by this change. Unfinished V5.300/V5.301 native-effects experiments are not included.

## V5.285 — 2026-10-19

### Mobile & PWA UX Refinements

- **Add Object Drawer Physics**: smooth slide-down close animations (`.drawerClosing`) for mobile bottom sheets and desktop docked trays; prevent stuck states and race conditions upon rapid toggle.
- **Experimental Notice Suppression**: add persistent "Jangan tampilkan lagi" / "Don't show again" secondary action with synchronous `localStorage` verification and 14-language translations.
- **Projects Tab Empty State**: high-contrast dark theme empty state with styled icon card (`#18202e`, `#73e2bd` accent) and clear typographic hierarchy (`#f1f5f9` header, `#94a3b8` description).
- **iOS Safari Viewport Docking**: switch mobile browser viewport height to `100dvh` to keep bottom navigation cleanly above the Safari URL bar, while preserving edge-to-edge `100lvh` for standalone Home Screen PWAs.
- **Android PWA Viewport Alignment**: restrict hardware screen scaling to iOS and standardize non-iOS standalone PWAs on `100dvh`, preventing bottom navigation bars from sinking beneath Android system buttons or gesture navigation areas.
- **Navbar Polish**: high-contrast white text for language selector badge ID in mobile navigation bar.

## V5.284 — 2026-10-19

### Alight Motion UX Alignment & Add Object Drawer Overhaul

- Redesign Add Object (`+`) popup drawer matching Alight Motion v5.0.273 (`activity_edit_addpopup.xml`):
  - 5 categorical top tabs (Shapes, Elements, Media, Audio, Templates) with AM icon assets and active pill indicators.
  - Dedicated right-side quick-add action rail (Freehand Drawing, Vector Drawing, Text, and Close).
  - Clean responsive layouts: mobile bottom sheet with 18px top radius and desktop docked tray below the timeline.
- Implement parametric vector shape tracing for primitive shapes: plus, heart, pie, and crescent/moon.
- Expand export frame rates up to 240 FPS (15, 18, 20, 24, 25, 30, 48, 50, 60, 90, 120, 144, and 240 FPS), with auto-sync of current project FPS upon opening the export sheet.
- Refine timeline splitter bar, playback transport controls, and responsive preview surface sizing.

## V5.283 — 2026-10-17

This update brings the tested single-file experimental build to main, including
the configurable keyboard shortcuts. The app remains a prototype.

### Configurable keyboard shortcuts

- Open the categorized guide with the editor's ? button, F1, or ?.
- Remap 13 actions with Ctrl/Cmd, Shift, and Alt; conflicting combinations cannot be saved.
- Persist per-browser settings and restore defaults. Retain the nine UI languages of the shortcuts guide.
- Keep typing/IME isolated from editing commands, restore dialog focus, and respect the active group timeline when seeking.

### Layer groups, appearance and audio

- Dedicated group timelines with breadcrumb navigation, ordinary groups, Mask and Exclude groups, and AM Link group import.
- Ungrouping retains child layers and the supported Mask/Exclude relationships.
- Inspector border and shadow controls, audio spectrum, and reusable audio-spectrum presets.
- Preserve existing tracking, Text Animator and project-thumbnail features.

### Experimental Alight Motion compatibility

- Expanded native/script, drawing, path, text, repeat and GPU effect integration with editable imported parameters/keyframes.
- WebGL1/2 compatibility and effect-graph, blur/bounds and procedural-kernel improvements.
- Scatter Repeat parameter/parent caching avoids repeated metadata work without reducing source copy counts or replacing 3D with 2D.
- Improved native Float parameter/default and Hz/RPM accumulation boundaries, waveform/noise/script-return handling, and script environment/Timecode serialization.
- Preserve AM XML source ordering, nested-scene ownership, transform parents and roundtrip data.

### Verification and limitations

- V5.283 passes 52 keyboard/UI checks, nine languages, 190-layer XML roundtrip with 117 source-owner references, 96 effect call-site controls and 50 original 3D Scatter time points across WebGL 1/2.
- Its renderer/effect definitions remain unchanged from verified V5.281. All 108 earlier suites were not rerun for this UI-only update.
- These are scoped desktop controls, not full-project phone FPS or proof of native Android parity. Effect-specific gaps, nonlinear easing, some source-clock/retiming, camera/parent, typography, media and export contracts remain under development.
- The unfinished V5.282 live source-clock draft is NOT included. Full Alight Motion effect parity is not claimed.
- Refresh the PWA shell-cache version while leaving imported media and project storage untouched.
- README is unchanged.

## V5.238 — 2026-10-11

This update publishes the previously unreleased V5.238 work and carries forward
the project thumbnails. The app remains a prototype.

### Tracking (experimental)

- Motion 2D: Point, Two Points, Planar, Corner Pin, and Stabilizer modes with editable baked keyframes.
- Camera 3D: experimental camera analysis, re-solving saved observations, and camera/Null placement.
- Playback displays tracked observations as `+` markers. Select a continuous track to create a Null for parenting other layers.
- Supports source-plane tracking for footage placed in 3D Space, as well as 2D observation Nulls.
- Adds continuous-lifetime filters and optional poor-to-excellent quality colors. Display filtering leaves the saved analysis and existing Null keyframes unchanged.
- New analyses record measured matching quality; older saved tracks use labeled estimates until reanalyzed.

### Text Animator

- Character, word, line, and whole-text targets with independent range selectors.
- XYZ position/rotation, scale, pivots, styling, paths, and restricted expressions.
- 130 built-in presets, including continuous 3D animation, with editable keyframes and easing.
- Custom presets and reusable effect packages with local JSON import/export.
- Per-character glyph planes can use the scene camera and parent transforms in 3D Space.

### Home project thumbnails

- Real saved-preview thumbnails on project cards.
- Bounded thumbnail capture from an eligible paused preview without starting additional rendering or decoding.
- Thumbnail persistence, invalid-image fallback, and compatibility with older projects. If no valid preview is available, the previous thumbnail is retained.

### Current limitations

- Tracking is experimental and depends on footage quality and browser/device capability. A video-plane Null does not reconstruct physical depth inside the recording; missing observations are not fabricated across gaps.
- Quality colors express relative matching quality, not accuracy probabilities. Old tracks require reanalysis for measured scores.
- 3D text uses flat glyph planes without extrusion. Some effects use a compatibility path that flattens per-character depth.

### PWA update

- Refreshes the offline-shell cache version while retaining network-first navigation and leaving imported media and project storage untouched.


