#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
OUT="androidApp/build/startup-network-audit"
PACKAGE="com.stillnotes.app"
mkdir -p "$OUT"
stamp() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$1" >> "$OUT/phases.txt"; }
snapshot() {
    adb shell dumpsys netstats --poll > "$OUT/$1-netstats.txt" 2>&1
    adb shell dumpsys netstats --full >> "$OUT/$1-netstats.txt" 2>&1
}
observe() {
    for ((i=0; i<$2; i++)); do
        printf '%s phase=%s\n' "$(date -u +%FT%TZ)" "$1" >> "$OUT/uid-sockets.txt"
        adb shell "cat /proc/net/tcp /proc/net/tcp6 /proc/net/udp /proc/net/udp6" \
            | awk -v uid="$APP_UID" 'NR == 1 || $8 == uid' >> "$OUT/uid-sockets.txt"
        sleep 1
    done
}
adb root
adb wait-for-device
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
APP_UID="$(adb shell cmd package list packages -U "$PACKAGE" | tr -d '\r' | sed -n 's/^package:com\.stillnotes\.app uid:\([0-9]*\)$/\1/p')"
[[ "$APP_UID" =~ ^[0-9]+$ ]] || { echo 'Cannot resolve app UID'; exit 1; }
printf '%s\n' "$APP_UID" > "$OUT/app-uid.txt"
adb shell am force-stop "$PACKAGE"
adb logcat -c
stamp before_launch
snapshot before
observe before_launch 30
stamp first_launch
adb shell am start -W -n "$PACKAGE/.MainActivity" > "$OUT/launch.txt"
adb logcat -d -s FirebaseInitProvider:I AndroidRuntime:E '*:S' > "$OUT/firebase-init.txt"
observe first_launch 90
snapshot online
adb exec-out screencap -p > "$OUT/first-launch.png"
stamp offline
adb shell svc wifi disable
adb shell svc data disable
observe offline 30
snapshot offline
adb shell svc wifi enable
adb shell svc data enable
sleep 5
stamp positive_control_instrumentation
set +e
adb shell am instrument -w -r \
    -e class com.stillnotes.app.StartupNetworkAuditTest \
    "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrumentation.txt" 2>&1
TEST_EXIT=$?
set -e
adb exec-out run-as "$PACKAGE" cat files/startup-network-audit.json > "$OUT/uid-counters.json"
adb logcat -d -s FirebaseInitProvider:I AndroidRuntime:E '*:S' >> "$OUT/firebase-init.txt"
stamp complete
[[ "$TEST_EXIT" -eq 0 ]] && grep -q 'OK (1 test)' "$OUT/instrumentation.txt"
grep -q 'FirebaseApp initialization successful' "$OUT/firebase-init.txt"
python3 - "$OUT" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
report = json.loads((p / 'uid-counters.json').read_text())
assert report['uid'] == int((p / 'app-uid.txt').read_text())
assert report['positive_control_http_status'] == 204
assert report['after_positive_control_tx_bytes'] > report['before_positive_control_tx_bytes'] >= 0
assert report['after_positive_control_rx_bytes'] > report['before_positive_control_rx_bytes'] >= 0
print(json.dumps(report, indent=2))
PY
