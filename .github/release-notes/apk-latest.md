Installable debug-signed Android APK for EditorsHub.

Built from the **native Kotlin/Compose Android app** by GitHub Actions — not a
PWA or WebView wrapper. This rolling `apk-latest` release tracks the latest
tested native build.

Current build: **4.4.0** (version code 20), package `com.editorshub`,
commit `97702d00b71d5fc7b3bad716286477ea356bacd7`.

Download **`EditorsHub-Motion.apk`** from this rolling release. The original
asset name is preserved so the existing download URL continues to work; the
file is now the native APK. It is signed with the CI debug key: back up
projects before replacing an installation signed with a different key. This
is not a production-signed Play Store artifact.

Verification: GitHub Actions run [42677408773](https://github.com/spideycandy973-web/EditorsHub-/actions/runs/42677408773) passed unit tests,
14 emulator instrumentation tests, packaging, and emulator install/launch
smoke checks. Exact pixel-clone parity to the supplied reference is not
claimed.

## Clean-room boundary

Original Kotlin/Compose code and artwork only. No reference APK source, DEX,
proprietary artwork, fonts, audio, or re-signed APK are included.
