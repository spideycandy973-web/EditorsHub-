# Splitting the reference recording so every frame can be analysed

## The blocker

The recording you shared is a single file in Google Drive:

| Property | Value |
| --- | --- |
| Name | `Screenrecorder-2026-11-04-20-36-00-405.mp4` |
| Drive ID | `1Wqk73fNhe1NqiwwlQC53fjLgZZXtTzq6` |
| Size | **301,626,146 bytes** (287.7 MiB) |
| Duration | 629,660 ms = **10:29.66** |
| Resolution | 1220 × 2712 |
| MD5 | `6e796d643165b629ab3f46e2cddbf8c2` |

The agent workspace accepts **at most 100 MiB (104,857,600 bytes) per file**. A
single 287.7 MiB download is refused before any bytes arrive, so no frame can be
decoded. The unauthenticated Drive download URL redirects to Google sign-in, and
the text-extraction endpoint has a 25 MB cap, so those routes cannot be used
either.

Splitting into three parts brings each part to roughly **95.9 MiB**, comfortably
below the limit. Four parts (~72 MiB each) is even safer. **No frames have been
watched yet** — the pixel-accurate pass starts as soon as the parts arrive.

## Option A — lossless split (fastest, no re-encode)

Run this on a phone or computer with [ffmpeg](https://ffmpeg.org/) installed.
Three parts of 210 seconds, stream-copied, so every pixel is unchanged:

```sh
ffmpeg -i Screenrecorder-2026-11-04-20-36-00-405.mp4 \
  -c copy -map 0 -f segment -segment_time 210 -reset_timestamps 1 \
  part-%d.mp4
```

That produces `part-0.mp4`, `part-1.mp4`, `part-2.mp4` (the last one is ~209 s).
Check the sizes:

```sh
ls -lh part-*.mp4
```

If any part is above **95 MiB**, use four shorter segments instead:

```sh
ffmpeg -i Screenrecorder-2026-11-04-20-36-00-405.mp4 \
  -c copy -map 0 -f segment -segment_time 160 -reset_timestamps 1 \
  part-%d.mp4
```

> `-c copy` cuts at keyframes, so part sizes can be uneven. If one part is still
> too big, use Option B.

## Option B — smaller re-encode (guaranteed under 90 MiB each)

Three parts, ~2.0 Mbit/s H.264, keeping the original 1220 × 2712 resolution:

```sh
ffmpeg -i Screenrecorder-2026-11-04-20-36-00-405.mp4 \
  -c:v libx264 -preset veryfast -crf 26 -b:v 2M -maxrate 2.4M -bufsize 4M \
  -c:a aac -b:a 96k -movflags +faststart \
  -f segment -segment_time 210 -reset_timestamps 1 part-%d.mp4
```

Each part then lands near 55 MiB. UI colours and layout remain readable at this
bitrate; do **not** downscale, because the geometry is what the clone is built
from.

## Option C — phone-only (no computer)

Use any "video splitter / compress" app and export **3 clips of ~3½ minutes
each** at the original resolution. Keep them under 90 MB apiece. Avoid apps that
add a watermark over the UI.

## How to hand the parts over

Any one of these works:

1. **Attach the 3 files directly to this chat** (preferred — no Drive hop).
2. Upload the 3 parts to Google Drive and paste the three `view?usp=drivesdk`
   links; make sure link sharing is on.
3. Put the 3 parts on any host that serves a direct, unauthenticated
   `https://…/part-0.mp4` download and paste the URLs.

Please keep the original order in the filenames (`part-0`, `part-1`, `part-2`).

## What happens next

Once the parts are in the workspace:

```sh
# one-time tooling
npm ci
npm install --no-save --package-lock=false ffmpeg-static

# full frame pass
FFMPEG=$(node -p "require('ffmpeg-static')") \
FFPROBE=$(command -v ffprobe || echo ffprobe) \
  node tools/video-frames.mjs --fps 1 --out analysis part-0.mp4 part-1.mp4 part-2.mp4
```

`tools/video-frames.mjs` rebuilds one continuous 10:29 timeline, extracts one
review frame per second (plus every scene change), and writes
`analysis/contact-sheet.html`, `analysis/manifest.json`, `analysis/scenes.json`
and `analysis/frames.json`. Every distinct UI state is then compared against the
20 attached stills and against `src/um/*`, and the differences are implemented in
one release.

## Secondary references already available

The 20 still frames you attached earlier (1220 × 2712, `ULTRA MOTION`) are
already in the workspace and are used as the fallback target: launcher, settings,
create project, editor, Add grid, Shape picker, and the property sheets.
