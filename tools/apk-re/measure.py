#!/usr/bin/env python3
"""Derive compact, directly usable UI measurements from decoded APK evidence.

This module consumes ONLY the structural output already produced by REA,
Ghidra, JADX and APKTool (``apk-analysis/*.json``). It never copies decompiled
source, DEX bytecode or proprietary bitmap/font/audio assets. The output is a
small fact table of exact colour values, dp dimensions, layout geometry and UI
labels that the independent implementation can match with original code.

It can run offline against committed evidence::

    python3 tools/apk-re/measure.py

``analyze.py`` also calls :func:`build` right after a fresh decode in CI.
"""
from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

OUT = Path("apk-analysis")

# Label-like strings that identify reference screens. Kept short and factual.
LABEL_HINTS = (
    "preset", "effect", "blur", "shape", "layer", "project", "export", "share",
    "camera", "color", "opacity", "border", "shadow", "stroke", "font", "audio",
    "media", "template", "element", "keyframe", "resolution", "frame rate",
    "background", "quality", "timeline", "duration", "transform", "mask", "blend",
    "upload", "import", "create", "settings", "about", "website", "telegram",
    "tiktok", "save", "delete", "undo", "redo", "play", "zoom", "full screen",
    "standard settings", "guide", "discover", "close", "cancel", "done", "apply",
)

# dp values that matter for CSS reproduction (everything else is noise).
DP_RE = re.compile(r"^(-?\d+(?:\.\d+)?)dp$")


def _rgb(hexcolor: str) -> tuple[int, int, int] | None:
    m = re.fullmatch(r"#([0-9a-fA-F]{6,8})", hexcolor.strip())
    if not m:
        return None
    h = m.group(1)
    if len(h) == 8:  # AARRGGBB
        h = h[2:]
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _is_ui_label(s: str) -> bool:
    if not isinstance(s, str) or not (1 < len(s) <= 90):
        return False
    if not re.search(r"[A-Za-z]", s):
        return False
    # Reject code fragments, paths, log lines, URLs and format strings.
    if re.search(r"[{}\[\];]|\s{3}|https?://|www\.|/|\\|@|\$|%[0-9sd]|<|>|\.java|\.kt|=>|==|!=|\bnull\b|\bvoid\b|\bfun\b|\bval\b|\bvar\b", s):
        return False
    letters = sum(c.isalpha() or c.isspace() for c in s)
    return letters / len(s) > 0.82


def build(values: dict, layouts: dict, strings: list[str], native: dict,
          manifest: dict, drawables: list) -> dict:
    """Return the measurement fact table (also written to measurements.json)."""
    # ---- exact decoded palette ----
    colors = values.get("color", {}) or {}
    palette: dict[str, dict] = {}
    for name, raw in colors.items():
        rgb = _rgb(str(raw))
        if rgb:
            palette[name] = {
                "value": str(raw),
                "rgb": list(rgb),
                "hex": "#%02x%02x%02x" % rgb,
            }
    # Colours actually referenced by layout backgrounds / textColor / tint.
    used: Counter[str] = Counter()
    for tree in layouts.values():
        for node in tree.get("nodes", []):
            for attr in ("background", "textColor", "tint", "color", "src"):
                v = node.get(attr)
                if isinstance(v, str) and v.startswith("@color/"):
                    used[v.split("/", 1)[1]] += 1
    palette_usage = [
        {"name": n, "count": c, **palette[n]}
        for n, c in used.most_common(40) if n in palette
    ]

    # ---- exact dimensions ----
    dimens = values.get("dimen", {}) or {}
    dp_values: list[dict] = []
    for name, raw in dimens.items():
        m = DP_RE.match(str(raw).strip())
        if m:
            dp_values.append({"name": name, "dp": float(m.group(1)), "raw": raw})
    dp_values.sort(key=lambda x: (x["dp"], x["name"]))
    common_dp = Counter(d["dp"] for d in dp_values).most_common(30)
    # Text sizes and heights are the values that map directly to CSS px.
    text_sizes = [d for d in dp_values if "text" in d["name"].lower() or "size" in d["name"].lower()]
    heights = [d for d in dp_values if "height" in d["name"].lower() or "bar" in d["name"].lower()]

    # ---- layout geometry ----
    heights_freq: Counter[str] = Counter()
    widths_freq: Counter[str] = Counter()
    textsize_freq: Counter[str] = Counter()
    gravity_freq: Counter[str] = Counter()
    widget_freq: Counter[str] = Counter()
    for tree in layouts.values():
        for node in tree.get("nodes", []):
            widget_freq[node.get("t", "?")] += 1
            for attr, freq in (("layout_height", heights_freq), ("layout_width", widths_freq),
                               ("textSize", textsize_freq), ("layout_gravity", gravity_freq)):
                v = node.get(attr)
                if isinstance(v, str):
                    freq[v] += 1
    geometry = {
        "layout_height": heights_freq.most_common(28),
        "layout_width": widths_freq.most_common(20),
        "textSize": textsize_freq.most_common(20),
        "gravity": gravity_freq.most_common(12),
        "widgets": widget_freq.most_common(20),
    }

    # ---- representative layout trees (small ones with text or ids) ----
    samples = []
    for name, tree in layouts.items():
        nodes = tree.get("nodes", [])
        if not (4 <= len(nodes) <= 40):
            continue
        score = sum(1 for n in nodes if any(k in n for k in ("text", "id", "textSize", "background")))
        samples.append((score, name, len(nodes)))
    samples.sort(reverse=True)

    # ---- UI labels (dedup, English-looking) ----
    pool = list(strings)
    for v in (values.get("string", {}) or {}).values():
        pool.append(str(v))
    labels: set[str] = set()
    for s in pool:
        if not _is_ui_label(s):
            continue
        low = s.lower()
        if any(h in low for h in LABEL_HINTS):
            labels.add(s.strip())
    ordered_labels = sorted(labels)[:600]

    # ---- Ghidra native facts ----
    funcs = native.get("ghidra_functions", []) or []
    native_facts = {
        "function_count": len([f for f in funcs if f and not f.startswith("#")]),
        "entry_points": [f.split(" ")[0] for f in funcs[:20] if re.match(r"^[0-9a-fA-F]{8} ", f)],
        "notes": [f for f in funcs if f.startswith("#")][:5],
    }
    details = native.get("ghidra_details", {}) or {}
    if details:
        native_facts["details"] = details

    result = {
        "source": "REA + Ghidra + JADX + APKTool structural evidence only",
        "package": manifest.get("package"),
        "activity_count": len(manifest.get("activities", []) or []),
        "color_count": len(palette),
        "dimen_count": len(dimens),
        "layout_count": len(layouts),
        "drawable_count": len(drawables),
        "palette": dict(list(palette.items())[:140]),
        "palette_usage": palette_usage,
        "dimens": {
            "count": len(dp_values),
            "common_dp": [{"dp": d, "count": c} for d, c in common_dp],
            "text_sizes": text_sizes[:60],
            "heights": heights[:60],
            "all": dp_values[:400],
        },
        "geometry": geometry,
        "screen_layout_candidates": [
            {"name": n, "nodes": c, "score": s} for s, n, c in samples[:24]
        ],
        "labels": ordered_labels,
        "ghidra": native_facts,
    }
    return result


def main() -> None:
    def load(name, default):
        p = OUT / name
        return json.loads(p.read_text()) if p.exists() else default

    values = load("values.json", {})
    layouts = load("layouts.json", {})
    strings = load("ui-strings.json", {}).get("strings", [])
    native = load("native.json", {})
    manifest = load("manifest.json", {})
    drawables = load("drawables.json", [])
    data = build(values, layouts, strings, native, manifest, drawables)
    (OUT / "measurements.json").write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {OUT/'measurements.json'} "
          f"({len(data['palette'])} colours, {len(data['labels'])} labels)", flush=True)


if __name__ == "__main__":
    main()
