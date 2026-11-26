#!/usr/bin/env bash
# Android emulator runner executes each 'script' line in a separate shell.
# Keep all operations in one Bash process so the working directory and exit codes persist.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Headless emulator images may leave the IME service disabled or have no
# default selected. That causes InsetsController.show(ime()) to fail at
# PHASE_CLIENT_REQUEST_IME_SHOW even though the Compose text field has focus.
# Enable and explicitly select a real installed keyboard before both UI suites.
available_imes="$(adb shell ime list -s | tr -d '\r')"
echo "Installed emulator keyboard services:"
printf '%s\n' "$available_imes"
target_ime="$(printf '%s\n' "$available_imes" | grep -m1 '^com.google.android.inputmethod.latin/' || true)"
if [[ -z "$target_ime" ]]; then
    target_ime="$(printf '%s\n' "$available_imes" | grep -m1 '^com.android.inputmethod.latin/' || true)"
fi
if [[ -z "$target_ime" ]]; then
    target_ime="$(printf '%s\n' "$available_imes" | grep -m1 '/' || true)"
fi
if [[ -z "$target_ime" ]]; then
    echo "::error::The emulator has no installed input method service; keyboard QA is impossible"
    exit 1
fi
adb shell ime enable "$target_ime" || exit 1
adb shell ime set "$target_ime" || exit 1
adb shell settings put secure show_ime_with_hard_keyboard 1 || exit 1
current_ime="$(adb shell settings get secure default_input_method | tr -d '\r')"
show_with_hardware="$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')"
echo "Selected IME: $current_ime; show with hardware keyboard: $show_with_hardware"
if [[ "$current_ime" != "$target_ime" || "$show_with_hardware" != "1" ]]; then
    echo "::error::Emulator IME selection or visible software-keyboard setting did not stick"
    exit 1
fi

failure_capture_args=(
    -Pandroid.testInstrumentationRunnerArguments.androidx.compose.ui.test.failure.isScreenshotCaptureEnabled=true
    -Pandroid.testInstrumentationRunnerArguments.androidx.compose.ui.test.failure.isUiHierarchyCaptureEnabled=true
)

# The dark-mode Gradle run reuses the same instrumented test output directory.
save_failure_artifacts() {
    local theme="$1"
    local output="androidApp/build/outputs/connected_android_test_additional_output"
    local destination="androidApp/build/qa-failure-artifacts/$theme"
    if [[ -d "$output" ]]; then
        mkdir -p "$destination" && cp -a "$output/." "$destination/"
    fi
}

echo "::group::Android UI tests (light)"
adb shell cmd uimode night no
bash ./gradlew :androidApp:connectedDebugAndroidTest "${failure_capture_args[@]}" --stacktrace
light_exit=$?
if [[ "$light_exit" -ne 0 ]]; then save_failure_artifacts light || exit 1; fi
echo "::endgroup::"

echo "::group::Android UI tests (dark)"
adb shell cmd uimode night yes
bash ./gradlew :androidApp:connectedDebugAndroidTest "${failure_capture_args[@]}" --rerun-tasks --stacktrace
dark_exit=$?
if [[ "$dark_exit" -ne 0 ]]; then save_failure_artifacts dark || exit 1; fi
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
