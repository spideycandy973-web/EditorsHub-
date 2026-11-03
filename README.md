# EditorsHub

A **real, working mobile video & motion-graphics editor** that runs entirely in the browser.
It is an Alight‑Motion‑style tool: layers, keyframes, effects, blending, masks, media and
XML project import/export — backed by a canvas compositor that draws **every frame for real**.
No placeholder screens, no mocked buttons.

> The old repository (a super-resolution enhancer) was removed; this is a complete rewrite.

---

## Run it

```bash
npm install
npm run dev          # http://localhost:5173  (binds 0.0.0.0, preview-host friendly)
npm run typecheck    # TypeScript, no errors
npm run build        # production bundle in dist/
npm run preview      # serve dist/
```

Open it on a phone (or your browser's device toolbar) — the UI is mobile-first and also docks
the inspector to the right on desktop. It is an installable PWA (`manifest.json` + `sw.js`),
works offline after first load, and needs **no server, account or API key**.

### Android / native app

```bash
npm run cap:add:android   # generates android/ and copies the web bundle
npm run cap:sync          # rebuild + resync
npx cap open android      # open in Android Studio to build an APK/AAB
```

Capacitor wraps the same app; no native code needs to be written.

### Prebuilt APK

`EditorsHub-Motion.apk` at the repository root is a ready-to-install, debug-signed build.
It is produced by `.github/workflows/build-apk.yml` on every relevant push (and can be run
manually with **Actions → Build Android APK → Run workflow**). The same workflow publishes
the APK to the `apk-latest` GitHub Release and uploads it as a workflow artifact.

To install: copy the APK to an Android device, allow "Install unknown apps", and open it.
Minimum Android 6.0 (API 23); built against API 35.

---

## What actually works

| Area | Features |
| --- | --- |
| **Composition** | 9:16 / 1:1 / 16:9 / 4:5 / custom resolution, 12–60 fps, duration, background color, grid & safe-area guides, preview zoom |
| **Layers** | Text, Shape (8 shapes), Solid/gradient, Image, Video, Audio, Group/null parent. Visibility, lock, rename, duplicate, copy/paste, delete, reorder, split at playhead, drag-to-move, drag-edges-to-trim, in/out points, speed, loop, volume |
| **Transform** | Position X/Y, scale X/Y, rotation, opacity, anchor X/Y, skew — all keyframeable |
| **Keyframes** | Add/remove/drag on the timeline, 10 easing functions (linear, hold, cubic, back, elastic, bounce, spring…), auto-keyframe mode, frame snapping, easing curve graph, per-keyframe time/value/easing editing, 12 motion presets (fade, slide, pop, spin, swing, pulse, float, typewriter…) |
| **Effects** | 19 effects: Gaussian blur, sharpen, radial zoom blur, color/light (brightness, contrast, saturation, hue, exposure), grayscale, sepia, invert, tint, glow, drop shadow, outline, mosaic/pixelate, film grain, vignette, chromatic aberration, **chroma key (green screen)**, wave distortion, camera shake, beat pulse. Every parameter is animatable. Reorder / enable / disable. |
| **Styling** | Solid, linear and radial gradients, stroke, corner radius, font family (including uploaded TTF/OTF/WOFF fonts), weight, italic, caps, alignment, letter spacing, line height, text stroke, shape parameters, fit modes |
| **Blending** | Normal, Multiply, Screen, Overlay, Darken, Lighten, Color Dodge/Burn, Hard/Soft Light, Difference, Exclusion, Hue, Saturation, Color, Luminosity, Add |
| **Masks** | Rectangle, ellipse and linear fade masks with feather + invert |
| **Parenting** | Any layer can be parented to another layer/group; transforms compose through the chain |
| **Media** | Import images / videos / audio / fonts from disk or drag-and-drop. Stored in IndexedDB so projects survive reloads. Built-in gradient backgrounds and a generated sample video clip. Video frames are decoded by real `<video>` elements and audio by real `<audio>`/Web Audio |
| **Playback** | Play / pause, loop, frame stepping, scrubbing, timeline zoom, auto-sync of all media to the playhead |
| **Export** | **Real MP4/WebM video** (canvas.captureStream + MediaRecorder + Web Audio mix), **PNG/JPEG frame-sequence ZIP** (frame-accurate seeks), **single PNG frame**, project **XML**, project JSON |
| **Import** | XML projects/presets (tolerant Alight-Motion-style parser, embedded `data:` media), JSON projects |
| **Projects** | Autosave, multiple saved projects, save-as, open/delete, undo/redo (80 steps) |
| **Input** | Touch/pointer editing, keyboard shortcuts (Space, ←/→, K, S, D, Delete, Ctrl+Z/Y, 1–5, E, ?) |

### Keyboard shortcuts

`Space` play/pause · `←` `→` frame step · `K` keyframe · `S` split · `D` duplicate ·
`Delete` delete layer · `Ctrl/⌘+Z` undo · `Ctrl/⌘+Y` redo · `1`–`5` panels · `E` export · `?` help

---

## Architecture

```
src/
  editor/
    types.ts        data model + all property/effect definitions
    defaults.ts     layer/project factories, demo composition, motion presets
    animation.ts    easing functions, keyframe interpolation, helpers
    store.ts        editor state, history, selection, playback, project CRUD
    storage.ts      localStorage projects + IndexedDB media
    media.ts        import/probe, MediaLibrary (video/audio sync + exact seeks),
                    built-in assets, generated sample clip
    renderer.ts     canvas compositor: transforms, parenting, blend modes,
                    shapes/text/gradients, masks, 19 effects, hit testing
    xml.ts          XML serializer + tolerant project/preset parser
    export.ts       MediaRecorder video, frame ZIP (fflate), still PNG
  components/       TopBar, Preview, PlayerBar, Timeline, BottomNav,
                    panels (Add/Edit/Effects/Keys/Project), Modals, ui primitives
  App.tsx           app shell, render loop (rAF), keyboard shortcuts
  index.css         full mobile + desktop responsive design
public/             PWA manifest, EditorsHub logo/icons, service worker
```

**Rendering pipeline** — each frame the compositor clears the background, walks layers
bottom-to-top, computes animated values at the layer-local time, composes parent/child
matrices, draws content into an isolated scratch canvas (with CSS canvas filters and
shadows), applies pixel-level effects (`getImageData`), applies the mask, then composites
with `globalCompositeOperation` and alpha. The preview renders at fitted device resolution;
exports render at 50/75/100% of the project resolution.

---

## XML projects

Export from **Project → Export XML** (optionally embed media as `data:` URIs), import from
**Project → Import XML** — paste text or pick a `.xml` file, see a validation report, then
either open it as a new project or append its layers to the current one.

The parser is deliberately tolerant: it accepts the documented `EditorsHubMotion` schema,
Alight-Motion-style preset fragments (`<Layer>`, `<Item>`, `<Track>`…), shorthand
`<Position x=… y=…>`, inline `keyframes="0:0;1:100"`, alias attribute names, and nested
`<Keyframe>` elements. See **[XML_FORMAT.md](./XML_FORMAT.md)** for the schema.

> Note: Alight Motion's own XML is a proprietary, app-specific format that can reference
> media and effects not available in a browser. This importer reconstructs the standard
> parts (layers, transforms, keyframes, text, colors, common effects) and reports anything
> it cannot map; it does not claim 100% binary/project compatibility with that app.

---

## Browser support / honest limitations

- **Video export** uses `MediaRecorder`: Chrome/Edge produce WebM (and MP4 on newer builds),
  Safari produces MP4. It records in **real time** (a 10 s project takes 10 s) because canvas
  capture is a live stream. Use **Frames (ZIP)** for deterministic frame-accurate output.
- **Frame ZIP export** is capped at 900 frames and full-resolution PNGs can be large.
- Canvas `filter` (blur, glow, drop-shadow, color adjustments) needs a modern Chromium/Safari;
  unsupported filter strings are ignored gracefully.
- Media stays on **your device** (IndexedDB / localStorage); clearing site data removes it.
  Use XML or JSON export to back projects up.
- Mobile browsers may limit autoplay; tap Play once to unlock audio/video playback.

---

## EditorsHub — native Android app (`EditorsHub/`)

This repository also contains **EditorsHub**, a complete, original Android Studio project:
Kotlin + Jetpack Compose + Material 3, package `com.editorshub`. It implements a real
multilayer compositor, keyframe engine with Bezier curves and graph editor, 18 animatable GPU/Canvas
effects, transitions, text/shape/vector editing, masks, audio mixing with waveforms, Room project
autosave/versioning, and genuine MediaCodec/MediaMuxer MP4 export (480p–4K, H.264/H.265, AAC).

```bash
cd EditorsHub
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

CI builds it on every relevant push, runs JVM unit tests, and publishes a debug APK, an unsigned
release APK, an AAB and a complete source archive to the `v2.0.1` and `editorshub-latest`
GitHub releases and to `/artifacts`.
Use `android-release.yml` with keystore secrets for a **signed** production release — no keystore
or password is committed. Full docs: [EditorsHub/README.md](EditorsHub/README.md),
[architecture](EditorsHub/docs/ARCHITECTURE.md), [dependencies](EditorsHub/docs/DEPENDENCIES.md),
[release signing](EditorsHub/docs/RELEASE_SIGNING.md).

The web/PWA editor above and its Capacitor APK (`EditorsHub-Motion.apk`) remain fully functional;
nothing was removed.
