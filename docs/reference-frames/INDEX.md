# Reference recording storyboard

- **32** labelled state contact sheets in `sheets/` (1 fps, 5x4 tiles). Read these first, in order.
- **8** timeline contact sheets in `timeline-sheets/` (1 fps, 10x8 tiles) for animation timing.
- **8** readable stills in `key-states/` at the screen timestamps listed in `docs/VIDEO_AUDIT.md`.
- `probe.json` holds the source video's ffprobe metadata; `manifest.json` records the extraction settings.
- Native CI captures belong in `docs/screenshots/`; `docs/NATIVE_VISUAL_ALIGNMENT.md` records the measured comparison method and current alignment pass.

The Android status strip and gesture pill are present in the frames but are excluded from the change-detection audit in `docs/VIDEO_AUDIT.md`. The raw 301,626,146-byte MP4 is NOT in the repository.
