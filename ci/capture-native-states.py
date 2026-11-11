#!/usr/bin/env python3
"""Capture reference-matched native home, Projects, creation and editor states.

The Android CI smoke script already captures the launcher. This helper uses the
UI hierarchy (text/content-description, not hard-coded screen coordinates) to
open Projects, the creation sheet, a fresh project, the Add sheet, the property
inspector, Transform/keyframe states, and a real timeline clip.
"""

import subprocess
import time
import xml.etree.ElementTree as ET

STEPS = [
    ("native-projects.png", "content-desc", "Projects", 1.6),
    ("native-create.png", "content-desc", "Create project", 1.6),
    ("native-editor.png", "text", "Create Project", 6.0),
    ("native-editor-add.png", "content-desc", "Add layer", 1.6),
    ("native-editor-inspector.png", "content-desc", "Add Text layer", 2.0),
    ("native-editor-properties.png", "content-desc", "Back to properties", 1.6),
    ("native-editor-transform.png", "content-desc", "Move & Transform", 1.6),
    ("native-editor-keyframe.png", "content-desc", "Position X keyframe", 1.6),
]


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def dump_nodes():
    run(
        ["adb", "shell", "uiautomator", "dump", "/sdcard/window.xml"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    xml = subprocess.check_output(["adb", "shell", "cat", "/sdcard/window.xml"])
    return list(ET.fromstring(xml.decode("utf-8", "replace")).iter("node"))


def find_bounds(attribute, value):
    for node in dump_nodes():
        if node.attrib.get(attribute) != value:
            continue
        bounds = node.attrib.get("bounds", "")
        # Example: [210,2071][870,2251]
        parts = bounds.replace("][", ",").strip("[]").split(",")
        if len(parts) != 4:
            continue
        left, top, right, bottom = (int(item) for item in parts)
        if right > left and bottom > top:
            return (left + right) // 2, (top + bottom) // 2
    raise RuntimeError(f"Could not find a node with {attribute}={value!r}")


def wait_for_description(label, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if any(node.attrib.get("content-desc") == label for node in dump_nodes()):
                print(f"found {label!r}", flush=True)
                return
        except Exception as error:  # UI Automator can transiently fail while the UI settles.
            print(f"waiting for {label!r}: {error}", flush=True)
        time.sleep(1)
    raise RuntimeError(f"Timed out waiting for content-desc {label!r}")


def capture(path):
    with open(path, "wb") as out:
        out.write(subprocess.check_output(["adb", "exec-out", "screencap", "-p"]))
    print(f"captured {path}", flush=True)


def tap(attribute, value):
    x, y = find_bounds(attribute, value)
    run(["adb", "shell", "input", "tap", str(x), str(y)], stdout=subprocess.DEVNULL)
    print(f"tapped {attribute}={value!r} at {x},{y}", flush=True)


def main():
    for path, attribute, value, delay in STEPS:
        tap(attribute, value)
        if value == "Create Project":
            wait_for_description("Play / pause")
        elif value == "Add layer":
            wait_for_description("Add sheet")
        elif value == "Add Text layer":
            wait_for_description("Back to properties")
        elif value == "Move & Transform":
            wait_for_description("Position X keyframe")
        elif value == "Position X keyframe":
            wait_for_description("Keyframe at 0 ms")
        time.sleep(delay)
        capture(path)


if __name__ == "__main__":
    main()
