#!/usr/bin/env python3
"""Measured pixel audit against the 28 reference screenshots.

    python3 tools/visual-audit.py [--mine DIR]

The reference JPGs are 1220x2712. This script scales them to the 430x956
phone canvas, finds the exact bounding rows of the card colour (#1c1c1f),
the lavender button colour (#d8dcff) and the dominant palette, then reports
the same measurements for optional `--mine` screenshots and the deltas.

The numbers below are the values used by the v4.1.0 CSS alignment pass.
Only original code and original artwork are used in the app; this script
measures geometry/colour, it never copies an image into the build.
"""
from __future__ import annotations

import argparse
import glob
from pathlib import Path

import numpy as np
from PIL import Image

W, H = 430, 956
CARD = (28, 28, 30)
LAV = (216, 220, 255)

# Screen index -> (label, expected card rows in the 430x956 canvas)
SCREENS = {
    0: ("launcher", [(129, 226), (240, 475), (855, 921)]),
    2: ("settings", [(137, 745)]),
    3: ("create sheet", [(394, 840)]),
    7: ("add sheet", [(542, 861)]),
}


def row_groups(a: np.ndarray, target: tuple[int, int, int], tol: int, minpix: int) -> list[tuple[int, int]]:
    mask = (np.abs(a.astype(int) - np.array(target)).sum(axis=2) < tol)
    rows = np.where(mask.sum(axis=1) >= minpix)[0]
    out: list[tuple[int, int]] = []
    if not len(rows):
        return out
    start = prev = int(rows[0])
    for r in rows[1:]:
        r = int(r)
        if r - prev > 3:
            out.append((start, prev))
            start = r
        prev = r
    out.append((start, prev))
    return out[:5]


def measure(path: str) -> dict:
    img = Image.open(path).convert("RGB").resize((W, H))
    a = np.asarray(img)
    flat = a.reshape(-1, 3)
    # dominant colours (quantised to 8 levels to survive JPEG noise)
    q = (flat // 8 * 8)
    uniq, counts = np.unique(q, axis=0, return_counts=True)
    top = [tuple(int(v) for v in uniq[i]) for i in np.argsort(-counts)[:5]]
    return {
        "cards": row_groups(a, CARD, 10, 25),
        "lavender": row_groups(a, LAV, 22, 15),
        "dominant": top,
        "pixels": {
            "background": tuple(int(v) for v in a[600, 200]),
            "card": tuple(int(v) for v in a[145, 200]),
        },
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refs", default="/tmp/arena-coding-uploads")
    ap.add_argument("--mine", default="")
    args = ap.parse_args()

    refs = sorted(glob.glob(str(Path(args.refs) / "*.jpg")))
    print(f"Reference screenshots: {len(refs)} at {Image.open(refs[0]).size}")
    print(f"Measured on the {W}x{H} phone canvas\n")

    for idx, (label, expected) in SCREENS.items():
        if idx >= len(refs):
            continue
        m = measure(refs[idx])
        print(f"[{idx:02d}] {label}")
        print(f"      reference card rows : {expected}")
        print(f"      measured card rows  : {m['cards']}")
        print(f"      lavender rows       : {m['lavender']}")
        if args.mine:
            cand = Path(args.mine) / f"{label.replace(' ', '-')}.png"
            if cand.exists():
                mine = measure(str(cand))
                print(f"      our card rows       : {mine['cards']}")
                e = expected[0]
                if mine["cards"]:
                    g = mine["cards"][0]
                    print(f"      delta top/bottom    : {g[0]-e[0]:+d} / {g[1]-e[1]:+d} px")
        print()

    palette = measure(refs[0])
    print("Dominant launcher colours (8-level quantised):", palette["dominant"])
    print("Exact samples: background", palette["pixels"]["background"],
          "card", palette["pixels"]["card"], "lavender", LAV)
    print("\nCSS tokens used by v4.1.0:")
    for k, v in {
        "--um-home": "#121215", "--um-card": "#1c1c1f", "--um-card-2": "#262629",
        "--um-lav": "#d8dcff", "--um-teal": "#00ffa8", "--um-radius": "18px",
    }.items():
        print(f"  {k:14s} {v}")


if __name__ == "__main__":
    main()
