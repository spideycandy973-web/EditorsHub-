# Contributing

Thanks for helping improve EditorsHub / EditorsHub.

## Repository layout
- `/` — the EditorsHub Motion web/PWA editor (Vite + React + Capacitor) and its APK.
- `/EditorsHub` — the native **EditorsHub** Android Studio project (Kotlin + Compose).
- `/artifacts` — CI-generated Android APK/AAB builds.

## Development
1. Open `EditorsHub/` in Android Studio (Ladybug or newer), or run:
   ```bash
   cd EditorsHub
   ./gradlew :app:assembleDebug :app:testDebugUnitTest
   ```
2. Requires JDK 17+, Android SDK 35 and build-tools 35.0.0.
3. Web app: `npm install && npm run dev`.

## Code style
- Kotlin, `kotlin.code.style=official`, 4-space indent.
- Keep rendering logic out of Compose: add it under `core/rendering/**`.
- Every new effect needs a spec in `EffectCatalog`, a Canvas implementation in
  `CanvasCompositor` and (when GPU-capable) a shader uniform in `GlProgram`.
- Add JVM unit tests for pure logic; instrumented tests for media/codec work.

## Pull requests
- Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` before opening a PR.
- Do not commit keystores, passwords, tokens or generated `build/` output.
- Describe which phase/feature your change touches.

## Reporting bugs
Include device/API level, project JSON (with media paths redacted), steps to reproduce
and the export error message shown in the UI.
