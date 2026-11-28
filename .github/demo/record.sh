#!/usr/bin/env bash
set -euo pipefail
demo="$(cd "$(dirname "$0")" && pwd)"
build="${RUNNER_TEMP:?}/aj-notes-build"
out="$RUNNER_TEMP/aj-notes-demo"
mkdir -p "$out"
app=com.stillnotes.app

case "${1:-}" in
  android)
    apk_dir="$demo/../../mobile/androidApp/build/outputs/apk"
    adb install "$apk_dir/debug/androidApp-debug.apk"
    adb install "$apk_dir/androidTest/debug/androidApp-debug-androidTest.apk"
    adb shell cmd uimode night no
    remote=/sdcard/aj-notes-demo.mp4
    adb shell settings put system show_touches 1
    adb shell screenrecord --time-limit 100 "$remote" &
    recorder=$!
    stop_recording() {
      adb shell pkill -INT screenrecord || true
      wait "$recorder" || true
    }
    video="$out/AJ-Notes-Android.mp4"
    ;;
  ios)
    simulator="$(xcrun simctl list devices available --json | python3 -c '
import json, re, sys
devices = json.load(sys.stdin)["devices"]
def version(runtime):
    match = re.search(r"iOS-([\d-]+)$", runtime)
    return tuple(map(int, match[1].split("-"))) if match else ()
for runtime in sorted(devices, key=version, reverse=True):
    if version(runtime) >= (17,):
        for device in devices[runtime]:
            if device.get("isAvailable") and device["name"].startswith("iPhone"):
                print(device["udid"])
                sys.exit(0)
sys.exit("No available iPhone simulator")
')"
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    xcrun simctl status_bar "$simulator" override --time '9:41'
    xcrun simctl install "$simulator" "$build/Stillnote.app"
    container="$(xcrun simctl get_app_container "$simulator" "$app" data)/Library/Application Support/Stillnote"
    mkdir -p "$container"
    cp "$demo/notes.json" "$container/stillnote-notes.json"
    xcrun simctl launch "$simulator" "$app"
    video="$out/AJ-Notes-iOS.mp4"
    xcrun simctl io "$simulator" recordVideo --codec=h264 "$video" &
    recorder=$!
    stop_recording() {
      kill -INT "$recorder" 2>/dev/null || true
      wait "$recorder" || true
      xcrun simctl shutdown "$simulator" || true
    }
    ;;
  *) exit 2 ;;
esac

trap stop_recording EXIT
sleep 1
if [[ "$1" == android ]]; then
  adb shell am instrument -w -r -e class "$app.RecordedNotesUiTest" \
    "$app.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out/debug-instrumentation.txt"
  grep -Fq 'OK (1 test)' "$out/debug-instrumentation.txt"
elif ! maestro test "$demo/flow.yaml"; then
  xcrun simctl io "$simulator" screenshot "$out/debug-screen.png"
  exit 1
fi
sleep 1
stop_recording
trap - EXIT
if [[ "$1" == android ]]; then
  adb pull "$remote" "$video"
fi
test -s "$video"
