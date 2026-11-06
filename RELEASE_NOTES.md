# EditorsHub v4.4.0 — screenshot pass and reliable APK updates

Package `com.editorshub`. All code and artwork are original; the app identity is
fully customisable in-app (About → Customise app).

## Install

**Install `EditorsHub-Motion.apk`** from this release (or from the rolling
`apk-latest` release). If you previously installed an older build, open
**About → Clear cached UI & reload** once: older builds registered an offline
cache inside the WebView that could keep showing the previous interface even
after the APK was replaced. v4.4.0 removes that cache on native builds.

## What's new in 4.4.0

- **APK updates actually show the new UI.** v4.3.0's cache-first service worker
  pinned `index.html` and the old hashed JS bundles. Native builds no longer
  register it, clear stale registrations/caches at startup, and expose a real
  cache reset in the About menu. Hosted-web builds are network-first with a
  versioned cache.
- **Launcher matches the reference JPGs**: `ULTRA MOTION` /
  `ultramotionofficial.com`, original stacked-images-with-arrow and hex-cube
  SVG glyphs, diamond brand badge, and a real network-speed readout measured
  from Resource Timing. One-tap presets switch between the reference look and
  `EDITORS HUB` / `editorshub.app`.
- **Effect Browser**: five original vector quick-pick tiles, teal separator
  dot, muted category plates, rounded top and corrected 77px status gap. Three
  new real effects back the quick picks — Fade In/Out, Crystal and Hot Color.
- **Guide card**: half-screen artwork panel with overlaid title/description and
  a fixed Standard Settings bar.
- **Presets**: wider card, centred close, taller transport illustration, real
  Import elements action.
- **Create sheet**: single Background control; **shape picker**: 5-column grid
  with 15 shapes; **FAB**: real X close state.
- `com.editorshub` package name unchanged, so this installs as an update.

## Verification

- `npm run typecheck`, `npm run build`: clean.
- `node tools/smoke.mjs --shots`: 95/95 checks, no console errors/warnings.
- `tools/capture-states.mjs` regenerates the comparison frames; measured
  mean per-pixel differences against the 28 reference JPGs: home 6.64,
  settings 10.97, create 19.86, blank editor 8.94, Add 10.09, Shape 14.36,
  Effect Browser 31.26, Presets 27.64 (0 = identical, 255 = maximally
  different). These are measurements, not a claim of pixel identity.
