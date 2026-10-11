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
# -r preserves package-private data. The candidate's versionCode is overridden
# to 2 at build time; the source-controlled version still remains 1.
adb install -r "$candidate_apk"
adb shell dumpsys package "$package" | grep -m 1 'versionCode=2' || {
  echo "::error::Candidate versionCode was not installed as version 2"
  exit 1
}
adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat files/stillnote-notes.json > "$workdir/after.json"
cmp "$workdir/before.json" "$workdir/after.json" || {
  echo "::error::Upgrade modified or erased existing note storage"
  exit 1
}
echo "::endgroup::"
echo "Android in-place update passed: old memo bytes intact after new app launched."
