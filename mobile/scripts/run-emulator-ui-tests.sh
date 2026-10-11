#!/usr/bin/env bash
# Android emulator runner executes each 'script' line in a separate shell.
# Keep all operations in one Bash process so the working directory and exit codes persist.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "::group::Android UI tests (light)"
adb shell cmd uimode night no
bash ./gradlew :androidApp:connectedDebugAndroidTest --stacktrace
light_exit=$?
echo "::endgroup::"

echo "::group::Android UI tests (dark)"
adb shell cmd uimode night yes
bash ./gradlew :androidApp:connectedDebugAndroidTest --rerun-tasks --stacktrace
dark_exit=$?
echo "::endgroup::"

mkdir -p androidApp/build/qa-screenshots
adb pull /sdcard/Android/data/com.stillnotes.app/files/qa/. androidApp/build/qa-screenshots/ || true

if [[ "$light_exit" -ne 0 || "$dark_exit" -ne 0 ]]; then
    echo "::error::Android UI tests failed (light=$light_exit, dark=$dark_exit)"
    exit 1
fi

echo "Android UI tests passed in both light and dark modes."
