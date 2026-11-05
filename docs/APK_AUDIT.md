# Reference APK reverse-engineering audit — v3.5.0

**Status: complete.** v4.0.0 builds on this evidence with 23 additional real
effects, a single-file brand/palette configuration and the
[MASTER_CLONE.md](MASTER_CLONE.md) screen map. REA, Ghidra, JADX, APKTool, androguard and `aapt`-style
resource decoding were installed and run against the user-supplied APK inside
GitHub Actions (`.github/workflows/apk-re.yml`). Compact structural evidence is
committed in `apk-analysis/`; the APK itself is never committed.

## Source

| Property | Value |
| --- | --- |
| Drive id | `1QP75dHFWSSUBESfW36vVNiQeBnA6vTIE` |
| Name | `UM Clone_8.0.1.apk` |
| Size | 294,346,248 bytes |
| MD5 | `9a75e85edfc1c1dbdb4da793ed95bbf8` |
| SHA-256 | `e9700dc901e814222ceb84d88594d024b2ef04247808f08e44363bf083e4c40f` |
| Package | `com.alightcreative.ultramotion` |
| Archive entries | 5,437 |

The APK is larger than the agent workspace's 100 MiB per-file cap, so CI
downloads it with `gdown` / the Drive confirm-token flow, verifies the byte
count and hash, and commits **only measurements** back to the branch.

## Toolchain actually run

| Tool | Version / result |
| --- | --- |
| **REA** (`rea-agents`) | 6.7/6.8 CLI; `doctor` reports `ghidra: true` and `jadx: true`. `identify-flutter-build` produced signed Evidence. `inspect-android-package` through the caller-supplied `jadx-headless-mcp` 0.7.1 JAR loaded **37,206 classes** and 5,437 resources. `search-android-classes` and `inspect-artifact` also ran. |
| **Ghidra** | Official 12.2 release, `analyzeHeadless` with a Java `GhidraScript` (Ghidra 12 removed Jython, so a Python post-script is not available). Imported a native `lib/arm64-v8a/lib*.so` and exported **43 discovered functions** (`apk-analysis/native.json`), including `JNI_OnLoad`. |
| **JADX** | 1.5.6 — Java class inventory (12,000 names committed, 37,206 total per REA). |
| **APKTool** | 3.0.3 — decoded manifest, `res/values*`, 250 layout widget trees, 2,208 drawable resource records. |
| **androguard / pyaxmlparser / Pillow** | Python fallback and structural JSON generation. |

Evidence files: `hashes.json`, `inventory.json`, `manifest.json`,
`values.json`, `layouts.json`, `drawables.json`, `ui-strings.json`,
`classes.json`, `native.json`, `tools.json`, `ghidra.log`,
`rea-package.json`, `rea-classes.json`, `rea-artifact.json`, `rea-flutter.json`,
`SUMMARY.md`.

## What was recovered

- **Resource table**: 1,076 colours, 1,237 dimensions, 3,310 strings,
  1,379 styles, 1,690 attrs, 78 integers, 9 bools.
- **Manifest**: 60+ activities including `EditActivity`, `SettingsActivity`,
  `AboutActivity`, `EffectBrowserActivity`, `FontBrowserActivity`,
  `ColorPickerActivity`, `PresetPreviewActivity`, `TemplateImportPreviewActivity`,
  `ExportPreviewActivity` — confirming the screen graph seen in the recording.
- **Exact palette tokens** (now used by `src/app.css`):

  | APK token | Value | CSS variable |
  | --- | --- | --- |
  | `A10` | `#ff121215` | `--um-home` |
  | `A9` | `#ff1c1c1f` | `--um-card` |
  | `A9_W5P` | `#ff262629` | `--um-input` / `--um-card-2` |
  | `A7` | `#ff2e2e33` | `--um-line` |
  | `Y1` | `#ff00ffa8` | `--um-teal` |

  These match the pixel samples from the 28 attached screenshots to within one
  channel value.

- **Exact Effect Browser category wording**, pulled from the decoded string
  table rather than guessed from thumbnails: `Color & Light`, `Drawing & Edge`,
  `Blur`, `Distortion/Warp`, `Procedural`, `3D`, `Move/Transform`, `Repeat`,
  `Matte/Mask/Key`, `Opacity/Visibility`, `Text`, `Other`, plus
  `Nature & Weather`, `Space & Cosmic`, `Flares, Rays & Energy`,
  `Glow & Gradients`, `Grids & Tunnels`, `Backgrounds & Textures`,
  `Glitch & Digital Damage`, `Retro TV, VHS & Film`, `Warp & Lens`,
  `Water, Glass & Refraction`, `3D Layer & Depth`, `3D Shapes & Solids`,
  `3D Props & Objects`, `Blur, Focus & Sharpen`, `Color Grading`,
  `Color Looks & Tone Maps`, `Ink, Paint & Halftone`,
  `Strokes, Bevels & Shadows`, `Wipes, Keys & Shatter`, `Shake & Transform`.
  All 32 are implemented as real, navigable categories in
  `src/app/EffectBrowser.tsx`.
- **Panel wording** recovered from the screenshots and the APK: `Move &
  Transform`, `Border & Shadow`, `Blending & Opacity`, `Edit Shape`, `Color &
  Fill`, `Presets`, `Effects`, `No presets... yet!`, `Discover Presets`,
  `Standard Settings`, `Guide`, `Select All Layers`, `Duplicate`, `Save
  Preset`, `Copy ... (including style)`, `Paste Layer`, `Paste Style`.

## What v3.5.0 implements from the APK

- **Full-screen Effect Browser** with the X / title / search header, five quick
  picks, the 32 two-column illustrated category tiles, 3-column subcategory
  grids, and the bottom **Guide card** (hero, description, tag chips,
  `+ Standard Settings`, star favourites). Every tile maps to a real canvas
  effect; `Standard Settings` adds it to the selected layer and opens the
  Effects panel.
- **Presets dialog** matching `No presets... yet!` with the mini-editor
  illustration, `Discover Presets`, `Close`, plus real local preset storage
  (save / list / apply / delete) and a built-in motion-preset gallery that
  applies real keyframes.
- **Seven-panel bottom tab strip** in the property pane, the always-visible
  right preview/zoom rail, and the exact APK palette tokens.
- `Save Preset` in the overflow menu now writes a local preset instead of only
  downloading an XML file.

## Deliberate limits

Only structural measurements were kept. No DEX bytecode, decompiled Java
source, proprietary bitmap, font, audio or APK bytes were committed, and none
are embedded in the app. All artwork in the new panels is original CSS
gradients and original SVG; all code is original TypeScript. Branding remains
`EDITORS HUB` / `com.editorshub` per the standing requirement. Effects that
EditorsHub's engine does not implement are not displayed as if they worked —
each visible tile applies a real, working canvas effect.

Account-gated features (cloud templates, sign-in, subscriptions) are not
presented as working controls; a real local template/preset library is provided
instead.

## Verification

`npm run typecheck`, `npm run build`, `node tools/verify-gif.mjs` (7/7) and
`node tools/smoke.mjs --shots` (**52/52**, zero console errors/warnings) pass,
including byte-level GIF export verification and end-to-end checks that the
Effect Browser adds a real effect and presets persist. Native and Capacitor CI
build the installable APK, release APK/AAB and source ZIP for `v3.5.0`.
