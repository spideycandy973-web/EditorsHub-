# Mobile motion-editor research and product target

Research reviewed on 2026-11-02. This document records public product claims and the
clean-room feature target for EditorsHub; it is not a promise of pixel-for-pixel or
binary compatibility.

## What the sources support

### Alight Motion

Alight Motion's own product page describes a layered motion-design editor for video,
graphics and audio, with vector/bitmap media, keyframes on settings, easing curves,
grouping and masks, color adjustment, velocity-based motion blur, custom fonts,
reusable elements, project packages and MP4/GIF/PNG-sequence export. It is the strongest
first-party source for the core workflow: [Alight Motion product page](https://alightmotion.com/).
Its official help center also documents animated camera objects and effect behavior:
[Camera Objects](https://support.alightmotion.com/hc/en-us/articles/10536993608977-Camera-Objects-Guide),
[Effect Guide](https://guide.alightmotion.com/effects/).

### Ultra Motion

Ultra Motion's own site identifies the app as a modified/rebuilt Alight Motion app,
not an independent official Alight Creative product. It advertises a live timeline,
project/XML import/export, effect search/categories, offline editing and a dark theme.
Those are the site's claims, not independently verified benchmarks:
[About Ultra Motion](https://ultramotionofficial.com/about),
[Ultra Motion feature list](https://ultramotionofficial.com/download).

### Gold Motion and After Motion Z

Toji Labs lists Gold Motion as an Android motion-graphics app, but its public listing
provides little technical detail: [Toji Labs apps](https://tojilabs.github.io/tojimotion/).
Detailed feature lists found for Gold Motion and After Motion Z were on third-party APK
pages rather than first-party product documentation; those claims are not treated as
verified requirements. In particular, EditorsHub will not reproduce premium-unlock,
watermark-removal, account-bypass or repackaging behavior.

## Clean-room product target

The shared, defensible product model is a real mobile motion editor—not an import-only
launcher:

1. **Start and resume editing:** create a composition with aspect ratio, resolution,
   frame rate and duration; show recent projects and import media/project packages.
2. **Build a composition:** independent video, image, audio, text, shape and vector
   layers; reorder, trim, split, duplicate, group and hide/lock them.
3. **Animate:** keyframe transforms, opacity, color and effect parameters; edit easing
   with presets and a curve editor; preserve frame-accurate timeline timing.
4. **Compose and style:** stack real effects, masks, blend modes, color controls, strokes,
   shadows and transitions; preview the result on a canvas while scrubbing and playing.
5. **Finish and deliver:** autosave, undo/redo, project interchange and honest export
   settings with progress, cancellation and clear unsupported-codec fallbacks.
6. **Feel responsive:** consistent directional screen transitions, animated tab/inspector
   changes, press feedback, expandable controls, and reduced-motion support.

Reference code, private assets, fonts, sounds, trademarks and paywall behavior are not
copied. Existing EditorsHub engine code is kept, and the interface is built as an
original, accessible Compose app with working controls.
