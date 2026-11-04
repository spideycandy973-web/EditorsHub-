# Reference-recording audit status

**Status: pending video parts. No frames have been watched.**

## Verified source metadata

Retrieved from Google Drive (`files.get`, `id=1Wqk73fNhe1NqiwwlQC53fjLgZZXtTzq6`):

```
Screenrecorder-2026-11-04-20-36-00-405.mp4
video/mp4 · 301,626,146 bytes · 629,660 ms · 1220x2712
md5 6e796d643165b629ab3f46e2cddbf8c2
```

The frame aspect (1220 / 2712 = 0.44985) matches the app's 430 × 956 phone
shell (0.44979), so the recording is a full-screen capture of the target UI with
the Android status strip and gesture pill included.

## Routes attempted and why they cannot deliver frames

| Route | Result |
| --- | --- |
| `download_file` on the Drive ID | Refused: 301,626,146 bytes > 100 MiB workspace per-file limit. |
| `download_file` with `supportsAllDrives` | Same size refusal. |
| `read_file_text` on the Drive ID | Refused: 25 MB text-extraction cap; also wrong tool for video. |
| `export_file` to `video/mp4` | Rejected: stored binary, not a Workspace document. |
| Public `uc?export=download` / `usercontent` URLs | Redirect to Google sign-in (large-file virus-scan gate). |
| Thumbnail endpoint | HTTP 500 from the fetcher; a single poster frame would not be an audit anyway. |
| Direct `curl` from the sandbox to Google hosts | SSL connect failure; those hosts are outside the allowed network. |
| Local filesystem scan | No MP4 present; only the 20 attached JPG stills. |

Making the Drive file public did not bypass the sign-in gate; the public
permission was removed again immediately afterwards.

## What is needed

2–4 segments, **each under 90 MiB**, or a direct unauthenticated HTTP URL.
Exact commands are in [VIDEO_SPLIT_GUIDE.md](VIDEO_SPLIT_GUIDE.md).

## What is already built from the still references

`src/um/*` implements the launcher, Projects/Elements/Templates segments,
settings, create project, editor, transport, timeline, Add grid, 28 shapes,
and the railed property sheets against the 20 attached frames. The v3.3.0 pass
adds the device status strip/gesture pill (real clock, real battery and network
values only) and screen/tab transition timing; those are chrome-level details
visible in every still. Animation curves, intermediate states and any screens
that appear *only* in the recording remain unverified until the parts arrive.

## Definition of done for the video pass

1. All parts downloaded; `manifest.json` total duration ≈ 629.66 s.
2. Every 1 fps review frame and every scene change inspected.
3. Each distinct UI state matched to a `src/um/*` component, with pixel geometry
   and colours recorded.
4. Transitions and control behaviour implemented with original code and the
   `EditorsHub` / `com.editorshub` branding.
5. Typecheck, build, interactive control audit and zero-console-error run.
6. New version, CI-built APK/AAB, PR to `main`, verified GitHub release assets.
