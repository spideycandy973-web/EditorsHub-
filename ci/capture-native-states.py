#!/usr/bin/env python3
"""Capture the native home, Projects and creation-sheet states from the emulator.

The Android CI smoke script already captures the launcher. This helper uses the
UI hierarchy (not hard-coded screen coordinates) to tap the Projects tab and the
create FAB, so the captures keep working after the reference layout is aligned.
"""

import subprocess
import time
import xml.etree.ElementTree as ET

STEPS = [
    ("native-projects.png", "Projects", 1.6),
    ("native-create.png", "Create project", 1.6),
]


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def dump_nodes():
    run(["adb", "shell", "uiautomator", "dump", "/sdcard/window.xml"],
        stdout=subprocess.DEVNULL)
    xml = subprocess.check_output(["adb", "shell", "cat", "/sdcard/window.xml"])
    return ET.fromstring(xml.decode("utf-8", "replace")).iter("node")


def find_bounds(content_desc):
    for node in dump_nodes():
        if node.attrib.get("content-desc") != content_desc:
            continue
        bounds = node.attrib.get("bounds", "")
        # Example: [210,2071][870,2251]
        parts = bounds.replace("][", ",").strip("[]").split(",")
        if len(parts) != 4:
            continue
        left, top, right, bottom = (int(value) for value in parts)
        if right > left and bottom > top:
            return (left + right) // 2, (top + bottom) // 2
    raise RuntimeError(f"Could not find a clickable node with content-desc {content_desc!r}")


def capture(path):
    with open(path, "wb") as out:
        out.write(subprocess.check_output(["adb", "exec-out", "screencap", "-p"]))
    print(f"captured {path}", flush=True)


def tap(content_desc):
    x, y = find_bounds(content_desc)
    run(["adb", "shell", "input", "tap", str(x), str(y)], stdout=subprocess.DEVNULL)
    print(f"tapped {content_desc!r} at {x},{y}", flush=True)


def main():
    for path, content_desc, delay in STEPS:
        tap(content_desc)
        time.sleep(delay)
        capture(path)
    # Return to the Projects screen so logcat/process diagnostics reflect a
    # normal foreground state rather than a modal sheet.
    tap("Close create project")
    time.sleep(1.0)


if __name__ == "__main__":
    main()
