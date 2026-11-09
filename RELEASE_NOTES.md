# EditorsHub v4.5.1 — Ultra Motion interaction polish

Package `com.editorshub`. This is the production Capacitor Android app; the release asset is the signed `EditorsHub-Motion.apk` only. Debug APKs are never attached to stable release tags.

## What's new in 4.5.1

- Smooth spring feedback when selecting Home / Projects and opening the create action.
- Consistent tactile press feedback for buttons, icons, project rows, property tiles, effect tiles and shape tiles.
- Spring entrances and fading backdrops for sheets, dialogs, full-screen panels, media-replacement confirmations and popups.
- Animated transient notifications and switches.
- Respects Android/browser reduced-motion preferences.
- Keeps the existing lightweight Ultra Motion-inspired UI and original canvas editor engine; no large native redesign.

## Verification

The release workflow runs TypeScript checks, the Vite production build, Android unit tests, a signed release APK build, APK signature verification, and an Android emulator install/launch smoke test before publishing.
