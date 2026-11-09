#!/bin/sh
# EditorsHub launch smoke test.
#
# reactivecircus/android-emulator-runner splits `script:` into one `sh -c` call
# per line, so this file is invoked as a single command: `sh ci/smoke.sh`.
# Every command's output is captured in smoke-diagnostics.txt for the CI job.
set -u

OUT=smoke-diagnostics.txt
: > "$OUT"

log() { echo "$@" | tee -a "$OUT"; }
fail() { log "::error::$1"; exit 1; }

log "== install =="
adb install -r artifacts/EditorsHub-debug.apk >> "$OUT" 2>&1 || {
  tail -80 "$OUT"
  fail "adb install failed"
}
adb logcat -c >> "$OUT" 2>&1 || true

log "== start =="
adb shell am force-stop com.editorshub >> "$OUT" 2>&1 || true
adb shell pm clear com.editorshub >> "$OUT" 2>&1 || true
adb shell am start -W -n com.editorshub/.MainActivity >> "$OUT" 2>&1 ||
  log "am start returned nonzero (continuing to inspect process/logcat)"

sleep 12
adb exec-out screencap -p > native-home.png 2>> "$OUT" || log "Could not capture native home screenshot"

PROC=$(adb shell pidof com.editorshub 2>> "$OUT" | tr -d '\r' || true)
log "PROC=[$PROC]"

log "== logcat tail =="
adb logcat -d -v brief >> "$OUT" 2>&1 || true
tail -140 "$OUT"

if [ -z "$PROC" ]; then
  fail "EditorsHub process is not running after launch"
fi
# AndroidRuntime prints "FATAL EXCEPTION" followed by "Process: <package>".
# During emulator boot, unrelated system processes (GmsCore, Phone, launcher,
# ...) can crash without affecting the app. Only a crash whose process is
# com.editorshub is a real EditorsHub launch failure.
CRASH=$(grep -A4 "FATAL EXCEPTION" "$OUT" | grep "Process: com.editorshub" || true)
if [ -n "$CRASH" ]; then
  log "== crash context =="
  grep -A12 "FATAL EXCEPTION" "$OUT" | tail -60
  fail "EditorsHub crash detected in logcat: $CRASH"
fi

log "Launch smoke test passed (pid $PROC)"
exit 0
