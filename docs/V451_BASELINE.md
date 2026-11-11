# v4.5.1 design baseline

## Decision

The user rejected the newer `v4.5.3` native/RomKit output as "trash" and
reaffirmed that the
[`v4.5.1` release](https://github.com/spideycandy973-web/EditorsHub-/releases/tag/v4.5.1)
is the main app because it has the accurate UI and design. Two choices were
confirmed:

1. **Native app, v4.5.1 design** — keep Kotlin/Compose and real editing, restore
   v4.5.1 layout/colours/controls as the visual source of truth, remove the
   newer RomKit/native redesign. This respects the earlier standing requirement
   that the app be a real Android app, not a PWA/WebView wrapper.
2. **Release state** — delete `v4.5.3`, set `v4.5.1` as Latest, rename it exactly
   `v4.5.1`, and keep only one APK asset.

## Exact reference

- Tag: `v4.5.1` → commit `4edd0e89d76e3165a0f6c67714a70a34e2b074d5`.
- Original release published: `2026-11-10T18:54:04Z`.
- Linked APK asset: `EditorsHub-Motion.apk`, 4,763,234 bytes, SHA-256
  `70c597016f0a2d88cc2966af5329b3780c2791e18bd09a34c1a8331a39d115b8`.
- The release body identifies it as the production Capacitor Android app with
  the Ultra Motion-inspired UI and smooth spring interactions.

## Source changes on `arena/be31b8e1-editorshub`

- Restored the exact v4.5.1 web/Capacitor design source from the tag:
  `src/`, `public/sw.js`, `package.json`, `package-lock.json`,
  `capacitor.config.json`, `index.html`, `vite.config.ts`, `tsconfig.json`,
  `android/app/build.gradle`, `rebuild-capacitor-apk.txt`, `RELEASE_NOTES.md`,
  and `docs/screenshots/README.md`. These files are the **design reference**,
  not the runtime of the active native app.
- `EditorsHub/app/src/main/java/com/editorshub/ui/theme/Design.kt` now uses the
  exact v4.5.1 `src/app.css` `:root` tokens: `--um-bg #070707`,
  `--um-home #121214`, `--um-card #1c1c1e`, `--um-input #262628`,
  `--um-line #2e2e33`, `--um-text #f3f3f6`, `--um-muted #92929c`,
  `--um-lav #d8dcff`, `--um-teal #01ffa7`, `--um-pink #ff3b7f`. Native
  `EditorPalette` legacy names map to those tokens.
- `EditorsHub/app/build.gradle.kts` sets `versionName = "4.5.1"`;
  `versionCode = 21` is retained so installs remain monotonic.
  `AppVersionTest` was updated accordingly.
- Android CI source-archive/debug-prerelease labels and reference-frame capture
  filenames now use `v4.5.1` instead of `v4.5.2`.
- `README.md`, `CHANGELOG.md`, `RELEASE_NOTES.md`,
  `docs/RELEASE_POLICY.md`, `docs/NATIVE_VISUAL_ALIGNMENT.md`, and
  `docs/screenshots/README.md` record the rollback and its limits.

No RomKit references remain in the active branch. `main` still has its own
RomKit/native code at `2432927`; this session does not modify `main`.

## Release changes

- Deleted the `v4.5.3` GitHub release **and** its remote tag.
- Set `v4.5.1` as **Latest** and renamed it exactly `v4.5.1`.
- Removed the extra `SHA256SUMS.txt` asset. The release now has exactly one
  asset: `EditorsHub-Motion.apk`.
- `v4.5.2` remains a historical non-Latest release with one APK.
- No new release, tag, or APK was created. No debug pre-release was published.

## Important limitation

The GitHub **Latest** APK is the **exact historical v4.5.1 Capacitor/WebView
build**, because the user explicitly chose to clean and restore the linked
release. The active branch source is a **native Kotlin/Compose** implementation
of that v4.5.1 design. These are intentionally different artifacts: the user
selected "native app, v4.5.1 design" for the code and "clean v4.5.1 to Latest"
for the release. This document does not claim the Latest APK is a native build,
does not claim pixel-exact parity with Ultra Motion, and does not authorize a
new release.

## Verification

- Android CI run `43114300889` on commit `8068c11` succeeded: debug/release
  Kotlin compilation, unit tests, all 18 instrumentation tests (including
  `EditorKeyframeRegressionTest`, `ThumbnailGeneratorTest` and the other
  emulator regressions), APK packaging, emulator launch smoke, and all nine
  `native-*.png` state captures.
- Visual-evidence import run `43116353207` copied the nine captures into
  `docs/screenshots/native-*-v4.5.1.png`; commit `c05bae1` recorded them.
- The home, empty editor, Add-sheet and keyframe captures were reviewed. The
  home uses the v4.5.1 lavender Upload/Import buttons, mint FAB, graphite
  cards and ULTRA MOTION wordmark. The empty editor uses the v4.5.1-style dark
  canvas, seven-icon transport, ruler, centre playhead and FAB. The Add sheet
  and keyframe inspector use the v4.5.1 `--um-*` tokens and show real controls
  (a green timeline keyframe diamond and lavender Transform sliders).
- These are alignment/working-control checks, not pixel-exact parity. The
  Add-sheet, inspector and Transform layouts come from the native
  reference-matched implementation rather than the older web sheets, and the
  reference device is taller than the Pixel 5 CI emulator.
