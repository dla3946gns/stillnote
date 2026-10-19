#!/usr/bin/env bash
set -euo pipefail

# This validates the simulator build only. No Apple account, signing identity,
# device provisioning, App Store upload, or production archive is involved.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${RUNNER_TEMP:?Run this check on the GitHub macOS runner}"
ARTIFACTS="$RUNNER_TEMP/stillnote-ios-qa"
DERIVED_DATA="$RUNNER_TEMP/stillnote-ios-derived"
APP="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/Stillnote.app"
BUNDLE_ID="com.stillnotes.app"
mkdir -p "$ARTIFACTS"
cd "$ROOT"

case "${1:-}" in
  build)
    {
      sw_vers
      uname -m
      xcodebuild -version
      xcodebuild -showsdks
      java -version 2>&1
    } | tee "$ARTIFACTS/environment.txt"
    if [[ "$(uname -m)" != arm64 ]]; then
      echo 'Stillnote currently defines the iosSimulatorArm64 target; an Apple silicon runner is required.' >&2
      exit 1
    fi
    chmod +x mobile/gradlew
    xcodebuild build \
      -project mobile/iosApp/Stillnote.xcodeproj \
      -scheme Stillnote \
      -configuration Debug \
      -sdk iphonesimulator \
      -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath "$DERIVED_DATA" \
      -resultBundlePath "$ARTIFACTS/build.xcresult" \
      ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
      CODE_SIGN_IDENTITY='' CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
      2>&1 | tee "$ARTIFACTS/build.log"
    test -x "$APP/Stillnote"
    file "$APP/Stillnote" | tee "$ARTIFACTS/app-binary.txt"
    lipo -archs "$APP/Stillnote" | grep -qw arm64
    ditto -c -k --sequesterRsrc --keepParent "$APP" "$ARTIFACTS/Stillnote-iOS-Simulator.zip"
    ;;
  launch)
    test -x "$APP/Stillnote"
    xcrun simctl list devices available --json > "$ARTIFACTS/simulators.json"
    SIMULATOR_ID="$(python3 - "$ARTIFACTS/simulators.json" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    devices = json.load(source)["devices"]

def version(runtime):
    match = re.search(r"iOS-([\d-]+)$", runtime)
    return tuple(map(int, match.group(1).split("-"))) if match else ()

for runtime in sorted(devices, key=version, reverse=True):
    if not version(runtime) or version(runtime) < (17,):
        continue
    for device in devices[runtime]:
        if device.get("isAvailable") and device["name"].startswith("iPhone"):
            print(device["udid"])
            raise SystemExit(0)
raise SystemExit("No available iPhone simulator running iOS 17 or later")
PY
)"
    printf '%s\n' "$SIMULATOR_ID" > "$ARTIFACTS/simulator-id.txt"
    # The hosted runner is fresh. Shut down the selected simulator on exit.
    trap 'xcrun simctl shutdown "$SIMULATOR_ID" >/dev/null 2>&1 || true' EXIT
    xcrun simctl boot "$SIMULATOR_ID"
    xcrun simctl bootstatus "$SIMULATOR_ID" -b
    xcrun simctl install "$SIMULATOR_ID" "$APP"
    xcrun simctl launch --terminate-running-process "$SIMULATOR_ID" "$BUNDLE_ID" \
      | tee "$ARTIFACTS/launch.txt"
    sleep 5
    xcrun simctl spawn "$SIMULATOR_ID" launchctl list > "$ARTIFACTS/launchctl.txt"
    awk -v bundle="$BUNDLE_ID" \
      'index($0, bundle) && $1 ~ /^[0-9]+$/ { found=1 } END { exit !found }' \
      "$ARTIFACTS/launchctl.txt"
    xcrun simctl io "$SIMULATOR_ID" screenshot "$ARTIFACTS/launch.png"
    {
      echo '### iOS simulator check'
      echo '- KMP framework and SwiftUI app compiled for arm64 iOS Simulator.'
      echo '- Installed and launched com.stillnotes.app; process remained alive for the startup check.'
      echo '- Artifact contains the simulator-only app, environment, build log and launch screenshot.'
      echo '- Physical iPhone installation and TestFlight signing still require a separate release workflow.'
    } >> "$GITHUB_STEP_SUMMARY"
    ;;
  *)
    echo 'Usage: check-ios-simulator.sh build|launch' >&2
    exit 2
    ;;
esac
