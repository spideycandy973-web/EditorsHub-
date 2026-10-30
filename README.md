# EditorsHub Motion Studio

EditorsHub is a mobile-first motion-design and video-editing workspace built with React, Vite and Capacitor. It uses an original EditorsHub identity and an original implementation inspired by the supplied mobile-editor references.

## Current feature set

- Home, project/template/element libraries, search, sorting, duplicate/delete, and responsive editor
- Composition setup and in-editor settings: project name, aspect ratio, dimensions, background, frame rate, and duration
- Local image/video/audio import stored in IndexedDB, with project metadata saved locally
- Portable `.ehproj` packages that embed local media; XML import with best-effort project/layer parsing and a relink workflow for unavailable source files
- Multi-layer timeline with visibility, z-order controls, modifier-key multi-select, organizational groups, playhead scrubbing, playback, actual audio waveforms, split, trim/source-in, speed, and volume controls
- Stage manipulation by touch/mouse/stylus: select, drag, and resize layers; transform keyframes with linear/ease-in/ease-out/ease-in-out interpolation
- Text, vector shapes, freehand drawing, and vector-path drawing layers
- Searchable effect browser with per-effect strength and CSS-filter-based preview/rendering
- PNG frame export and on-device video rendering with progress, preview, download, and quality/format preferences (MP4 when supported; WebM fallback)
- Capacitor Android app ID: `com.editorshub.mr.arfat`

This is a substantial functional prototype, not complete feature parity with commercial motion editors. The renderer is browser/WebView-based, so MP4 availability and export performance depend on the Android WebView's `MediaRecorder` support. Proprietary project XML formats are parsed on a best-effort basis; unsupported constructs and unavailable media need manual review/relinking. Cloud sync, cross-device asset sharing, nested groups/parent rigs, and a native hardware-accelerated compositor are not included.

## Run the web preview

```bash
npm install --legacy-peer-deps
npm run dev
```

## Build a debug APK locally

Install JDK 21 and the Android SDK (including Android SDK Platform 36 and Build Tools), then run:

```bash
npm install --legacy-peer-deps
npm run android:apk
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and uploads the APK artifact on pushes to `main`, `master`, or this working branch; the workflow can also be dispatched manually in GitHub Actions.
