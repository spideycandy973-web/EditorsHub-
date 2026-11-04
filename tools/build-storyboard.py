#!/usr/bin/env python3
"""Turn the reference screen recording into a compact, reviewable storyboard.

Runs inside GitHub Actions (the raw MP4 never enters the agent workspace):

  analysis/probe.json             ffprobe metadata
  analysis/states.json            timestamp + change score of every distinct UI state
  analysis/states/s-0001.jpg      each distinct state at 610 px wide
  analysis/sheets/sheet-001.jpg   20 states per sheet, labelled with timestamps
  analysis/timeline/t-00001.jpg   1 fps timeline frames (animation timing)
  analysis/timeline-sheets/*.jpg  80 timeline frames per sheet
  analysis/INDEX.md               how to read the storyboard

A "distinct state" is a 4 fps sample whose app content differs from the last
kept state by more than DIFF_THRESHOLD (mean absolute 0-255 difference), with a
forced sample at least every 3 seconds so slow animations are never skipped.
The Android status strip and gesture pill are masked out of the comparison so
the rolling clock / network counter does not generate false states.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

VIDEO = sys.argv[1] if len(sys.argv) > 1 else "/tmp/reference.mp4"
OUT = Path("analysis")
SAMPLE_FPS = 4
W, H = 120, 266                 # tiny comparison frame, forced by ffmpeg scale
TOP_MASK, BOTTOM_MASK = 14, 8   # status strip + gesture pill rows to ignore
PIXELS = W * H * 3
THRESHOLD = float(os.environ.get("DIFF_THRESHOLD", "3.2"))
MAX_GAP = float(os.environ.get("MAX_GAP_SECONDS", "3.0"))
MAX_STATES = int(os.environ.get("MAX_STATES", "900"))
RAW = "/tmp/um-tiny.raw"


def run(cmd: list[str], *, check: bool = True) -> subprocess.CompletedProcess:
    print("+", " ".join(cmd[:14]), "..." if len(cmd) > 14 else "", flush=True)
    return subprocess.run(cmd, check=check, capture_output=True, text=True)


def fmt(t: float) -> str:
    return f"{int(t // 60):02d}:{t % 60:05.2f}"


def main() -> None:
    for d in ("states", "sheets", "timeline", "timeline-sheets"):
        (OUT / d).mkdir(parents=True, exist_ok=True)

    # 1. Tiny raw frames for change detection.
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", VIDEO,
         "-vf", f"fps={SAMPLE_FPS},scale={W}:{H}", "-pix_fmt", "rgb24",
         "-f", "rawvideo", RAW])

    raw = np.fromfile(RAW, dtype=np.uint8)
    total = raw.size // PIXELS
    raw = raw[: total * PIXELS].reshape(total, H, W, 3)
    print(f"sampled {total} frames at {SAMPLE_FPS} fps ({total / SAMPLE_FPS:.2f}s)", flush=True)

    def content(frame: np.ndarray) -> np.ndarray:
        return frame[TOP_MASK:H - BOTTOM_MASK, :, :].astype(np.int16)

    kept: list[dict] = []
    last: np.ndarray | None = None
    for i in range(total):
        t = i / SAMPLE_FPS
        c = content(raw[i])
        if last is None:
            score, keep = 0.0, True
        else:
            score = float(np.abs(c - last).mean())
            keep = score > THRESHOLD or (t - kept[-1]["time"]) >= MAX_GAP
        if keep:
            kept.append({"index": i, "time": round(t, 3), "score": round(score, 3)})
            last = c
    print(f"{len(kept)} distinct states before capping", flush=True)

    if len(kept) > MAX_STATES:
        # Keep the strongest changes plus evenly spaced anchors.
        strong = sorted(kept, key=lambda k: -k["score"])[: MAX_STATES // 2]
        step = len(kept) / (MAX_STATES - len(strong))
        anchors = [kept[min(len(kept) - 1, int(j * step))] for j in range(MAX_STATES - len(strong))]
        kept = sorted({k["index"]: k for k in strong + anchors}.values(), key=lambda k: k["index"])
    print(f"{len(kept)} distinct states retained", flush=True)

    # 2. Re-extract just those frames at a readable resolution.
    expr = "+".join(f"eq(n\\,{k['index']})" for k in kept)
    vf = f"fps={SAMPLE_FPS},select='{expr}',scale=610:-2"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", VIDEO,
         "-vf", vf, "-fps_mode", "vfr", "-q:v", "3",
         str(OUT / "states" / "s-%04d.jpg")])
    state_files = sorted((OUT / "states").glob("s-*.jpg"))
    print(f"extracted {len(state_files)} state images", flush=True)

    # 3. 1 fps timeline for animation timing.
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", VIDEO,
         "-vf", "fps=1,scale=240:-2", "-q:v", "5",
         str(OUT / "timeline" / "t-%05d.jpg")])
    timeline_files = sorted((OUT / "timeline").glob("t-*.jpg"))

    # 4. Labelled contact sheets.
    def sheets(files: list[Path], folder: str, cols: int, rows: int,
               tile_w: int, tile_h: int, prefix: str, time_of) -> list[str]:
        made: list[str] = []
        per = cols * rows
        label_h = 22
        for page in range(0, len(files), per):
            batch = files[page: page + per]
            sheet = Image.new("RGB", (cols * tile_w, rows * (tile_h + label_h)), (14, 14, 17))
            draw = ImageDraw.Draw(sheet)
            for j, f in enumerate(batch):
                im = Image.open(f).convert("RGB")
                im.thumbnail((tile_w, tile_h), Image.LANCZOS)
                x = (j % cols) * tile_w
                y = (j // cols) * (tile_h + label_h)
                sheet.paste(im, (x + (tile_w - im.width) // 2, y))
                draw.text((x + 4, y + tile_h + 3),
                          f"{time_of(page + j)}  {f.name}", fill=(220, 220, 230))
            name = f"{prefix}-{page // per + 1:03d}.jpg"
            sheet.save(OUT / folder / name, quality=80, optimize=True)
            made.append(name)
        return made

    state_sheets = sheets(state_files, "sheets", 5, 4, 240, 533, "sheet",
                          lambda n: fmt(kept[n]["time"]) if n < len(kept) else "")
    timeline_sheets = sheets(timeline_files, "timeline-sheets", 10, 8, 120, 266, "tl",
                             lambda n: fmt(float(n)))

    (OUT / "states.json").write_text(json.dumps({
        "sample_fps": SAMPLE_FPS, "threshold": THRESHOLD, "max_gap": MAX_GAP,
        "count": len(kept), "states": kept,
    }, indent=2) + "\n")

    probe = json.loads((OUT / "probe.json").read_text()) if (OUT / "probe.json").exists() else {}
    (OUT / "manifest.json").write_text(json.dumps({
        "source": "Screenrecorder-2026-11-04-20-36-00-405.mp4",
        "drive_id": "1Wqk73fNhe1NqiwwlQC53fjLgZZXtTzq6",
        "probe": probe, "sample_fps": SAMPLE_FPS,
        "distinct_states": len(kept), "state_images": len(state_files),
        "state_sheets": len(state_sheets), "timeline_frames": len(timeline_files),
        "timeline_sheets": len(timeline_sheets),
    }, indent=2) + "\n")

    (OUT / "INDEX.md").write_text(
        "# Reference recording storyboard\n\n"
        f"- **{len(kept)} distinct UI states** at 4 fps sampling "
        f"(mean-abs-diff threshold {THRESHOLD}, forced sample every {MAX_GAP}s).\n"
        f"- **{len(state_files)}** readable state images in `states/` (610 px wide).\n"
        f"- **{len(state_sheets)}** labelled contact sheets in `sheets/` "
        "(20 states each, 5x4). Read these first, in order.\n"
        f"- **{len(timeline_files)}** one-second timeline frames in `timeline/` and "
        f"**{len(timeline_sheets)}** contact sheets in `timeline-sheets/` for animation timing.\n"
        "- `states.json` holds the exact timestamp and change score of every state;\n"
        "  `probe.json` holds the source video's ffprobe metadata.\n\n"
        "The Android status strip and gesture pill are excluded from change detection.\n"
        "The raw 301,626,146-byte MP4 is NOT in the repository.\n"
    )
    print(f"done: {len(state_sheets)} state sheets, {len(timeline_sheets)} timeline sheets", flush=True)


if __name__ == "__main__":
    main()
