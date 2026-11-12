# EditorsHub v4.5.4 — RomKit presentation re-skin

Native Kotlin/Compose build for `com.editorshub`.

## What's changed

- Applied RomKit's near-black ink/panel palette, hairline borders, semantic Ember/Cyan/Good/Warn/Bad colors, and compact typography to the existing native UI.
- Kept screens, routes, controls, permissions, editing state and feature behavior unchanged.
- Preserved thumbnail and keyframe functionality.

## Verification

The release workflow runs unit tests, builds and signs the native APK from the active branch, verifies its package/version/signature, and installs/launches it on an Android emulator before publishing.

## Signing notice

The repository's committed signing key is public. Do not use it as a secure production identity or for Google Play; Android update compatibility depends on the signing certificate.
