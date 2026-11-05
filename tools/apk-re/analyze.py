#!/usr/bin/env python3
"""Produce compact, measurement-only reverse-engineering evidence from an APK.

This script never copies decompiled source or proprietary bitmap assets into the
output tree. It records structural facts (manifest, values, layout widget trees,
class names, UI strings, resource names, native function names) so the
independent EditorsHub implementation can be matched to the reference geometry.

Usage: python3 tools/apk-re/analyze.py /tmp/um.apk
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import zipfile
from collections import Counter
from pathlib import Path

OUT = Path("apk-analysis")
APKTOOL = Path("/tmp/apktool-out")
JADX = Path("/tmp/jadx-out")
GHIDRA_TXT = Path("/tmp/ghidra-functions.txt")

# Attribute names worth keeping from a layout tree; everything else is dropped.
KEEP_ATTRS = {
    "id", "layout_width", "layout_height", "layout_weight", "layout_gravity",
    "gravity", "orientation", "background", "src", "text", "textColor",
    "textSize", "textStyle", "hint", "padding", "paddingLeft", "paddingRight",
    "paddingTop", "paddingBottom", "layout_margin", "layout_marginLeft",
    "layout_marginTop", "layout_marginRight", "layout_marginBottom",
    "visibility", "alpha", "scaleType", "tint", "drawable", "color",
    "radius", "cornerRadius", "cardCornerRadius", "max", "progress",
    "contentDescription", "onClick", "clipToPadding", "elevation",
}
MAX_LAYOUT_NODES = 160
MAX_LAYOUTS = 250
MAX_CLASSES = 12000
MAX_STRINGS = 6000


def write(name: str, data) -> None:
    p = OUT / name
    p.parent.mkdir(parents=True, exist_ok=True)
    pretty = json.dumps(data, indent=2, ensure_ascii=False)
    text = pretty if len(pretty) < 4_000_000 else json.dumps(data, ensure_ascii=False, separators=(',', ':'))
    p.write_text(text + "\n")
    print(f"wrote {p} ({p.stat().st_size} bytes)", flush=True)


def short(v: str, limit: int = 160) -> str:
    return v if len(v) <= limit else v[:limit] + "…"


def parse_values_xml(path: Path) -> dict:
    """Flatten a decoded res/values/*.xml into simple name→value maps."""
    import xml.etree.ElementTree as ET
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return {}
    out: dict[str, dict[str, str]] = {}
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        name = next((v.split("}")[-1].split(":")[-1] for k, v in el.attrib.items()
                     if k.endswith("}name") or k == "name"), None)
        if not name:
            continue
        val = (el.text or "").strip()
        if not val:
            val = " ".join(
                (child.text or "").strip() for child in el if (child.text or "").strip()
            )
        if tag in {"color", "dimen", "string", "style", "integer", "bool", "fraction", "attr"}:
            out.setdefault(tag, {})[name] = short(val)
    return out


def layout_tree(path: Path) -> dict:
    import xml.etree.ElementTree as ET
    try:
        root = ET.parse(path).getroot()
    except Exception as e:
        return {"error": str(e)}
    nodes: list[dict] = []

    def walk(el, depth: int) -> None:
        if len(nodes) >= MAX_LAYOUT_NODES:
            return
        tag = el.tag.split("}")[-1]
        attrs: dict[str, str] = {}
        for k, v in el.attrib.items():
            key = k.split("}")[-1].split(":")[-1]
            if key in KEEP_ATTRS:
                attrs[key] = short(v, 80)
        nodes.append({"d": depth, "t": tag, **attrs})
        for child in el:
            walk(child, depth + 1)

    walk(root, 0)
    return {"root": root.tag.split("}")[-1], "nodes": nodes}


def main() -> None:
    apk = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    raw = apk.read_bytes()
    md5 = hashlib.md5(raw).hexdigest()
    sha = hashlib.sha256(raw).hexdigest()
    write("hashes.json", {"size": len(raw), "md5": md5, "sha256": sha})

    zf = zipfile.ZipFile(apk)
    names = zf.namelist()
    bins = Counter(n.rsplit(".", 1)[-1].lower() for n in names if "." in n)
    write("inventory.json", {
        "entry_count": len(names),
        "extensions": dict(bins.most_common(50)),
        "dex_files": [n for n in names if n.endswith(".dex")],
        "native_libs": [n for n in names if n.endswith(".so")][:200],
        "assets_sample": [n for n in names if n.startswith("assets/")][:400],
        "top_level": sorted({n.split("/")[0] for n in names})[:100],
    })

    # ---- manifest (apktool output is plain XML) ----
    man = APKTOOL / "AndroidManifest.xml"
    manifest_facts: dict = {"file_present": man.exists()}
    if man.exists():
        text = man.read_text(errors="ignore")
        manifest_facts.update({
            "package": (re.search(r'package="([^"]+)"', text) or [None, None])[1],
            "versionName": (re.search(r'versionName="([^"]+)"', text) or [None, None])[1],
            "versionCode": (re.search(r'versionCode="([^"]+)"', text) or [None, None])[1],
            "activities": re.findall(r'<activity[^>]*android:name="([^"]+)"', text)[:200],
            "services": re.findall(r'<service[^>]*android:name="([^"]+)"', text)[:100],
            "permissions": re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', text)[:100],
            "launcher_theme": (re.search(r'android:theme="([^"]+)"', text) or [None, None])[1],
            "sdk": {
                "min": (re.search(r'minSdkVersion="([^"]+)"', text) or [None, None])[1],
                "target": (re.search(r'targetSdkVersion="([^"]+)"', text) or [None, None])[1],
            },
        })
    write("manifest.json", manifest_facts)

    # ---- decoded values (colours, dimensions, strings, styles) ----
    values: dict[str, dict] = {}
    for p in sorted((APKTOOL / "res").glob("values*/**/*.xml"))[:600]:
        parsed = parse_values_xml(p)
        for kind, entries in parsed.items():
            values.setdefault(kind, {}).update(entries)
    write("values.json", values)

    # ---- layout widget trees (structure only, no raw XML) ----
    layouts: dict[str, dict] = {}
    for p in sorted(list((APKTOOL / "res").glob("layout*/**/*.xml")))[:MAX_LAYOUTS]:
        layouts[p.relative_to(APKTOOL / "res").as_posix()] = layout_tree(p)
    write("layouts.json", layouts)

    # ---- drawable names and small previews ----
    drawables = []
    if (APKTOOL / "res").exists():
        for p in sorted((APKTOOL / "res").glob("drawable*/**/*"))[:2500]:
            if p.is_file():
                drawables.append({
                    "name": p.relative_to(APKTOOL / "res").as_posix(),
                    "bytes": p.stat().st_size,
                    "type": p.suffix.lower().lstrip("."),
                })
    write("drawables.json", drawables[:2000])

    # ---- UI strings from values + decompiled Java string literals ----
    strings: set[str] = set()
    for v in values.get("string", {}).values():
        if v and len(v) < 200:
            strings.add(v)
    if JADX.exists():
        for p in JADX.rglob("*.java"):
            try:
                t = p.read_text(errors="ignore")
            except Exception:
                continue
            for m in re.findall(r'"((?:[^"\\]|\\.){2,120})"', t):
                s = m.strip()
                if (re.search(r"[A-Za-z]", s) and not s.startswith(("http", "/", "@", "com/", "android", "java/"))
                        and len(s) < 120):
                    strings.add(s)
            if len(strings) > MAX_STRINGS * 3:
                break
    ordered = sorted(strings)[:MAX_STRINGS]
    write("ui-strings.json", {"count": len(ordered), "strings": ordered})

    # ---- class-name inventory (names only, never source) ----
    classes: list[str] = []
    if JADX.exists():
        for p in JADX.rglob("*.java"):
            rel = p.relative_to(JADX).as_posix()[:-5].replace("/", ".")
            classes.append(rel)
            if len(classes) >= MAX_CLASSES:
                break
    if not classes:
        try:
            from androguard.core.apk import APK as AGAPK
            a = AGAPK(str(apk))
            classes = [d.get_class_names()[i] for d in a.get_all_dex()
                       for i in range(min(len(d.get_class_names()), MAX_CLASSES))][:MAX_CLASSES]
        except Exception as e:
            classes = [f"androguard unavailable: {e}"]
    write("classes.json", {"count": len(classes), "classes": sorted(set(classes))[:MAX_CLASSES]})

    # ---- Ghidra native function inventory ----
    native: dict = {"ghidra_functions": []}
    if GHIDRA_TXT.exists():
        native["ghidra_functions"] = GHIDRA_TXT.read_text(errors="ignore").splitlines()[:4000]
    write("native.json", native)

    # ---- tool versions / REA outputs ----
    tools: dict = {}
    for name, path in {
        "rea_doctor": Path("/tmp/rea-doctor.txt"),
        "rea_artifact": Path("/tmp/rea-artifact.json"),
        "rea_flutter": Path("/tmp/rea-flutter.json"),
        "ghidra_version": Path("/tmp/ghidra-version.txt"),
        "jadx_version": Path("/tmp/jadx-version.txt"),
        "apktool_version": Path("/tmp/apktool-version.txt"),
    }.items():
        tools[name] = path.read_text(errors="ignore")[:20000] if path.exists() else "unavailable"
    write("tools.json", tools)

    summary = [
        "# Reference APK reverse-engineering evidence",
        "",
        f"- Source: `UM Clone_8.0.1.apk` · {len(raw):,} bytes · md5 `{md5}` · sha256 `{sha}`",
        f"- Archive entries: {len(names)}",
        f"- Manifest package: `{manifest_facts.get('package')}`, version `{manifest_facts.get('versionName')}`",
        f"- Decoded value resources: " + ", ".join(f"{k}={len(v)}" for k, v in values.items()),
        f"- Layout widget trees: {len(layouts)}",
        f"- Drawable resources: {len(drawables)}",
        f"- UI strings: {len(ordered)}",
        f"- Java classes inventoried: {len(classes)}",
        f"- Ghidra native functions: {len(native['ghidra_functions'])}",
        "",
        "Tools: REA (`rea-agents`), Ghidra headless, JADX, APKTool, androguard.",
        "Only structural measurements are recorded here: no decompiled source,",
        "DEX bytecode, proprietary bitmap or APK bytes are committed.",
        "",
        "The EditorsHub implementation uses these facts to match geometry and",
        "wording with original code and original artwork.",
        "",
    ]
    (OUT / "SUMMARY.md").write_text("\n".join(summary) + "\n")
    print("analysis complete", flush=True)


if __name__ == "__main__":
    main()
