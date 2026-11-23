#!/usr/bin/env bash
# Run on a fresh Android emulator after both APKs have been built on the same
# runner. This tests an ACTUAL adb install -r (no uninstall / pm clear).
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "usage: $0 <baseline-debug.apk> <baseline-androidTest.apk> <candidate-debug.apk>" >&2
  exit 2
fi

baseline_apk="$(realpath "$1")"
baseline_tests="$(realpath "$2")"
candidate_apk="$(realpath "$3")"
package="com.stillnotes.app"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

adb install "$baseline_apk"
baseline_dump="$(adb shell dumpsys package "$package")"
if [[ "$baseline_dump" =~ versionCode=([0-9]+) ]]; then
  baseline_code="${BASH_REMATCH[1]}"
else
  echo "::error::Could not read installed baseline versionCode"
  exit 1
fi
adb install "$baseline_tests"
echo "::group::Create a saved note with the previous main APK"
adb shell am instrument -w \
  -e class 'com.stillnotes.app.NotesUiTest#explicitSaveSurvivesClosingAndReopeningTheActivity' \
  com.stillnotes.app.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$workdir/baseline-test.log"
grep -Eq 'OK \(1 test\)' "$workdir/baseline-test.log" || {
  echo "::error::Baseline APK did not save the note successfully"
  exit 1
}
echo "::endgroup::"

# Capture exact app-private bytes from the old installation, using run-as only
# because these APKs are debug builds. Do not use a public storage path.
adb exec-out run-as "$package" cat files/stillnote-notes.json > "$workdir/before.json"
python3 - "$workdir/before.json" <<'PY'
import json
import pathlib
import sys

notes = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert len(notes) == 1, notes
note = notes[0]
assert note["title"] == "재실행 테스트", note
assert note["body"] == "다시 열어도 남는 본문", note
assert note["id"] and note["createdAt"] and note["updatedAt"]
print("Baseline saved note and timestamps confirmed.")
PY

echo "::group::Upgrade the same package without uninstalling"
# -r preserves package-private data. The candidate's versionCode is increased
# only in CI, without changing the production versionCode in gradle.properties.
adb install -r "$candidate_apk"
candidate_dump="$(adb shell dumpsys package "$package")"
if [[ "$candidate_dump" =~ versionCode=([0-9]+) ]]; then
  candidate_code="${BASH_REMATCH[1]}"
else
  echo "::error::Could not read installed candidate versionCode"
  exit 1
fi
if (( candidate_code != baseline_code + 1 )); then
  echo "::error::Upgrade must increase versionCode by exactly one: $baseline_code -> $candidate_code"
  exit 1
fi
echo "Validated in-place upgrade versionCode: $baseline_code -> $candidate_code"
adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat files/stillnote-notes.json > "$workdir/after.json"
cmp "$workdir/before.json" "$workdir/after.json" || {
  echo "::error::Upgrade modified or erased existing note storage"
  exit 1
}
echo "::endgroup::"
echo "Android in-place update passed: old memo bytes intact after new app launched."
