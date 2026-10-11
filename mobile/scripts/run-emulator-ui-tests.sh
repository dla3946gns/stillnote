#!/usr/bin/env bash
# Android emulator runner executes each 'script' line in a separate shell.
# Keep all operations in one Bash process so the working directory and exit codes persist.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Some hosted AVD images enable a hardware keyboard and therefore suppress the
# soft IME. The keyboard-overlap regression must run with a visible soft IME.
if ! adb shell settings put secure show_ime_with_hard_keyboard 1; then
    echo "::error::Cannot enable the software keyboard on the emulator"
    exit 1
fi
if [[ "$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')" != "1" ]]; then
    echo "::error::Emulator software-keyboard setting did not stick"
    exit 1
fi

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

# MediaStore screenshots survive test-runner uninstall/reinstall cycles.
mkdir -p androidApp/build/qa-screenshots
if ! adb pull /sdcard/Pictures/StillnoteQA/. androidApp/build/qa-screenshots/; then
    echo "::error::Cannot retrieve real Play screenshot captures from emulator"
    exit 1
fi
for shot in store-list-light.png store-editor-light.png store-list-dark.png store-editor-dark.png; do
    if [[ ! -s "androidApp/build/qa-screenshots/$shot" ]]; then
        echo "::error::Missing real Android Play screenshot: $shot"
        exit 1
    fi
done

if [[ "$light_exit" -ne 0 || "$dark_exit" -ne 0 ]]; then
    echo "::error::Android UI tests failed (light=$light_exit, dark=$dark_exit)"
    exit 1
fi

echo "Android UI tests passed in both light and dark modes."
