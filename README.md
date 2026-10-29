# EditorsHub Motion Studio

EditorsHub is a mobile-first motion-design workspace built with React, Vite and Capacitor. It uses an original EditorsHub identity and an original implementation inspired by the supplied mobile-editor references.

## Working preview features

- Home, project/template/element library, and responsive composition editor
- Project setup with aspect ratio, resolution, frame rate, and background controls
- Local image, video, and audio layer import; limited XML title/metadata import; `.ehproj` package import/export
- Layered timeline with visibility, playhead scrubbing, video/audio preview, split, trim, playback speed, and volume controls
- Text and shape layers; transform, color, opacity, duplicate, rename, and delete controls
- Transform keyframes with linear, ease-in, ease-out, and ease-in-out interpolation
- Searchable effect library and CSS-filter-based preview/render effects
- PNG frame export and on-device WebM render with progress, preview, and download
- Capacitor Android app ID: `com.editorshub.mr.arfat`

This is a functional first-pass prototype, not a feature-complete replacement for Ultra Motion, Gold Motion, or After Motion Z. There is no native multi-track compositor or MP4/H.264 export; advanced keyframe property animation, full third-party XML compatibility, and persistent storage of imported media still need work. Video rendering depends on browser/WebView support for canvas capture and `MediaRecorder`.

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
