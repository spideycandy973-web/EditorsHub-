#!/bin/sh
# Run instrumentation checks and launch the exact APK produced by the build job.
set -u
chmod +x EditorsHub/gradlew
if (cd EditorsHub && ./gradlew :app:connectedDebugAndroidTest --stacktrace) > android-test.log 2>&1; then
  tail -80 android-test.log
else
  cat android-test.log
  exit 1
fi
sh ci/smoke.sh
