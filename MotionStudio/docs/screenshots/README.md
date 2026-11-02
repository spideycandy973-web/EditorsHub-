# Screenshots

Screenshots are captured from a running build rather than committed as mock images.

```bash
# With an emulator/device connected:
adb exec-out screencap -p > docs/screenshots/01-home.png
# Then open the app, create a project, and capture the editor:
adb exec-out screencap -p > docs/screenshots/02-editor.png
adb exec-out screencap -p > docs/screenshots/03-keyframes.png
adb exec-out screencap -p > docs/screenshots/04-export.png
```

Instrumented Compose UI tests in `app/src/androidTest` can also produce screenshots with
`ComposeScreenCapture`. No placeholder/mock screenshots are shipped.
