# EditorsHub Motion Studio

EditorsHub is a mobile-first motion-design and video-editing workspace built with React, Vite and Capacitor. It uses an original EditorsHub identity and an original implementation inspired by the supplied mobile-editor references.

## Current feature set

- Liquid Glass interface: translucent gold-and-black surfaces, specular edges, adaptive reduced-transparency fallback, and mobile-friendly frosted navigation
- Home, project/template/element libraries, search, sorting, duplicate/delete, and responsive editor
- Composition setup and in-editor settings: aspect ratio, manual 1–99 FPS, dimensions, 2K (2048px long-edge) output, background, and duration
- Local image/video/audio import stored in IndexedDB, with project metadata saved locally
- Portable `.ehproj` packages that embed local media; XML import with best-effort project/layer parsing and a relink workflow for unavailable source files
- Multi-layer timeline with visibility, z-order controls, modifier-key multi-select, organizational groups, playhead scrubbing, playback, actual audio waveforms, split, trim/source-in, speed, and volume controls
- Stage manipulation by touch/mouse/stylus: select, drag, and resize layers; animated position, scale, opacity, rotation, and 3D pose keyframes with easing
- Text, vector shapes, freehand drawing, vector paths, and a basic 3D lab with Cube/Sphere/Pyramid/Plane/Torus primitives plus Wavefront OBJ import
- Searchable effect browser with per-effect strength and 2,700 generated look variations (27 recipes × 100 intensity levels), using editable CSS-filter approximations
- PNG frame export and on-device video rendering with progress, preview, download, up-to-99-FPS paced capture, 2K balanced output, and format preferences (MP4 when supported; WebM fallback)
- Keyframed velocity ramps with easing; experimental low-resolution block-flow prediction for sub-1× export frames
- On-device ESRGAN-Slim 2× neural super-resolution for still images and extracted video frames (TensorFlow.js/WebGL, local only); original media is preserved
- Optional export-time unsharp mask after resampling, separate from AI super-resolution
- Adjustable performance preview that reduces 3D raster detail without reducing export quality
- Capacitor Android app ID: `com.editorshub.mr.arfat`

This is a substantial functional prototype, not complete feature parity with Gold Motion or other commercial editors. The 2,700 entries are variations of 27 procedural filter recipes, not 2,700 distinct GPU effects. The renderer is browser/WebView-based, so MP4 availability and export performance depend on the Android WebView's `MediaRecorder` support. Proprietary project XML formats are parsed on a best-effort basis; unsupported constructs and unavailable media need manual review/relinking. The 3D renderer is a lightweight canvas mesh preview (OBJ geometry only; no materials, rigging, or GPU lighting). ESRGAN currently enhances still images or one extracted video frame at a time; large sources are reduced to a mobile-safe input size, and it does not AI-process an entire video clip. Slow-motion uses low-resolution block-motion estimation to extrapolate fractional frames below 0.98×; it can soften edges and is not yet Twixtor-grade interpolation. A 99 FPS project setting is an export target and actual capture rate depends on the Android WebView and device encoder. Cloud sync, cross-device asset sharing, nested groups/parent rigs, and a native hardware-accelerated compositor are not included.

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

## Model attribution

The bundled ESRGAN-Slim 2× model is provided through UpscalerJS and runs locally with TensorFlow.js. The model and runtime remain subject to their respective package licenses.
