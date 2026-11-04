# Reference recording storyboard

- **747 distinct UI states** at 4 fps sampling (mean-abs-diff threshold 3.2, forced sample every 3.0s).
- **747** readable state images in `states/` (610 px wide).
- **38** labelled contact sheets in `sheets/` (20 states each, 5x4). Read these first, in order.
- **630** one-second timeline frames in `timeline/` and **8** contact sheets in `timeline-sheets/` for animation timing.
- `states.json` holds the exact timestamp and change score of every state;
  `probe.json` holds the source video's ffprobe metadata.

The Android status strip and gesture pill are excluded from change detection.
The raw 301,626,146-byte MP4 is NOT in the repository.
