<h1 align="center">EditorsHub</h1>

<p align="center">
  <img src="icons/editorshub-192.png" width="120" alt="EditorsHub logo" />
</p>

<p align="center">
  <strong>A browser-based motion graphics and video editor.</strong>
</p>

## Project status

**Prototype / work in progress.** EditorsHub is not production-ready. Features,
project formats and the interface may still change.

## Run it

EditorsHub is a static site. There is no build step.

- **Locally:** serve this folder with any static file server and open it in the
  browser, for example `python3 -m http.server 8080` and then
  `http://localhost:8080/`.
- **Hosted:** put the folder on any static host. With GitHub Pages
  (Settings → Pages → deploy from the `main` branch, root folder) the address is
  `https://spideycandy973-web.github.io/EditorsHub-/`.

Opening `index.html` straight from disk starts the editor, but installing the
app and offline use need `http://localhost` or HTTPS.

**Recommended browser:** Google Chrome, on desktop or Android. Other browsers
may lack some of the media APIs the editor uses.

## Features

- **Keyframe animation** with interpolation controls on a layer timeline.
- **Curve and easing editing** for fine control over timing.
- **Vector path editing** with anchors, Bézier handles and touch-friendly tools.
- **3D transforms and cameras**, including orbit and lens controls.
- **Layer-based compositing** with masks, blending, groups and effects.
- **Text animation** presets and per-character animators.
- **Export** to video, PNG frames, a project package (`*.editorshub.json`) and
  XML (`*.editorshub.xml`).
- **Mobile-first interface** that also works on desktop.
- **Installable** as a Progressive Web App, with an offline shell.

## Languages

The interface is in English. The home screen and the main inspector labels can
also be shown in Indonesian, Chinese, Japanese, Malay, Thai, Vietnamese,
Filipino, Burmese, Khmer, Lao, Tetum, Portuguese and Tamil. Choose the language
from the globe button on the home screen.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | The whole application: markup, styles and scripts. |
| `manifest.webmanifest` | Install metadata for the Progressive Web App. |
| `sw.js` | Service worker that keeps an offline copy of the app shell. |
| `icons/` | App icons. `editorshub-logo.png` is the full-size logo. |
| `THIRD_PARTY_NOTICES.txt` | License notices for the third-party code in the app. |
| `CHANGELOG.md` | History of changes. |

## Project file compatibility

Project packages and XML files written by earlier builds still open. The
identifiers inside saved files and browser storage were kept unchanged for that
reason, so some of them still start with `open-motion`.

## Technology

HTML, CSS and JavaScript with Canvas, WebGL, WebCodecs and other browser media
APIs. The app is one self-contained file plus the PWA assets.

Three optional components are downloaded when first used and are not part of
this repository: the Mediabunny media library, and the MODNet model with ONNX
Runtime Web for AI background removal. Google Fonts are loaded on demand from
the font library.

## Third-party notices

EditorsHub is built on open-source code. The license notices that code requires
are in [`THIRD_PARTY_NOTICES.txt`](THIRD_PARTY_NOTICES.txt) and in the app under
**About → Licenses**. Keep both when you redistribute the app.

## Feedback

Bug reports and ideas are welcome through the repository's Issues.
