#!/usr/bin/env bash
set -euo pipefail
OUT="androidApp/build/auth-emulator-qa"
mkdir -p "$OUT"
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell am instrument -w -r \
    -e class com.stillnotes.app.AndroidEmailAuthEmulatorTest,com.stillnotes.app.AccountNavigationTest \
    com.stillnotes.app.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumentation.txt" 2>&1
cat "$OUT/instrumentation.txt"
grep -q 'OK (2 tests)' "$OUT/instrumentation.txt"
