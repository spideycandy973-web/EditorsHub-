# Open Motion (Android)

Native Kotlin & Jetpack Compose rewrite of **Open Motion**, a mobile-first motion graphics, keyframe animation, vector editing, and 3D compositing studio.

## Implemented Features

- **Home & Project Workspace**:
  - 4-tab mobile studio shell (`Beranda`, `Tutorial`, `Proyek`, `Templat`) with bilingual Indonesian / English UI toggle (`ID` / `EN`).
  - Local project persistence powered by **Room Database** (`ProjectEntity`, `ProjectDao`, `ProjectRepository`).
  - Project creation modal with Aspect Ratio presets (`9:16`, `16:9`, `4:5`, `1:1`, `4:3`, `Custom`), Resolution (`720p`, `1080p`, `1440p`), FPS (`24`, `30`, `60`), Duration, and Background Color.
  - Pre-built animated templates (`Title Motion`, `3D Parallax Scene`, `Cinematic Camera`, `Neon Pulse Intro`) and interactive workflow tutorials.
  - Project JSON import/export, rename, duplicate, and delete.

- **Composition Editor & Timeline**:
  - Live interactive Composition Viewport with zoom, tap-to-select, and drag-to-move layer positioning.
  - Multi-track Timeline with time ruler scrubbing, timeline zoom, play/pause/loop playback, keyframe jump, split at playhead, trim edges, marker pins, layer visibility/lock toggles, and clip shifting.
  - Undo / Redo snapshot history (`History` stack).

- **Layer Types & Creative Tools**:
  - **27 Parametric & Geometric Shapes** + **Shape Studio** (corner radius, star points, inner radius, polygon sides, stroke width/color).
  - **Interactive Vector Pen Tool** for drawing custom Bezier node paths directly on the canvas.
  - **Text Typography Studio** with 7 bundled Google Fonts (`Space Grotesk`, `Inter`, `Montserrat`, `Oswald`, `Playfair Display`, `Roboto Mono`, `Pacifico`), bold/italic, tracking, line height, and alignment.
  - **Media & Audio**: Zero-permission Android Photo Picker (`PickVisualMedia`) for images & videos, plus `OpenDocument` for audio tracks.
  - **3D Space & Elements**: Perspective `Camera 3D` (FOV, Z-depth, rotation), `3D Cube` & `3D Sphere` meshes, `Null / Controller`, and `Group` parenting/hierarchy.
  - **Visual Effects Stack & Browser**: Gaussian Blur, Directional Motion Blur, Brightness & Contrast, Hue & Saturation, Neon Glow, Chromatic Aberration, Vignette, Oscillate/Swing, Pulse Scale, Wave Warp, Particle Emitter, Audio Spectrum Visualizer, and Drop Shadow.
  - **Cubic-Bezier Curve Editor** with draggable control handles and 8 easing presets (`Linear`, `Ease Out`, `Ease In`, `In-Out`, `Expo Out`, `Overshoot`, `Snap`, `Elastic`).

- **Export & Share**:
  - Native H.264 **MP4 Video** export via `MediaCodec` + `MediaMuxer`.
  - **PNG** active frame export, **JSON** portable project package, and **XML Interchange** (Alight Motion-compatible structure with Open Motion 3D extensions).

## License

MIT License — see [LICENSE](LICENSE).
