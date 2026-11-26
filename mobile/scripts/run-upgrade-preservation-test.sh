#!/usr/bin/env bash
# Run on a fresh Android emulator after both APKs have been built on the same
# runner. This tests an ACTUAL adb install -r (no uninstall / pm clear).
set -euo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "usage: $0 <baseline-debug.apk> <baseline-androidTest.apk> <candidate-debug.apk> <expected-candidate-versionCode>" >&2
  exit 2
fi

baseline_apk="$(realpath "$1")"
baseline_tests="$(realpath "$2")"
candidate_apk="$(realpath "$3")"
expected_code="$4"
[[ "$expected_code" =~ ^[1-9][0-9]*$ ]] || { echo "::error::Invalid expected candidate versionCode"; exit 2; }
package="com.stillnotes.app"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

installed_version_code() {
  adb shell dumpsys package "$package" | awk '
    !found && /^[[:space:]]*versionCode=[0-9]+([[:space:]]|$)/ {
      split($1, fields, "="); print fields[2]; found=1
    }
    END { if (!found) exit 1 }
  '
}

adb install "$baseline_apk"
baseline_code="$(installed_version_code)"
[[ "$baseline_code" =~ ^[1-9][0-9]*$ ]] || { echo "::error::Invalid installed baseline versionCode"; exit 1; }
if (( expected_code <= baseline_code )); then
  echo "::error::Expected candidate versionCode $expected_code must exceed baseline $baseline_code"
  exit 1
fi
echo "Baseline versionCode: $baseline_code; expected upgrade: $expected_code"
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
# -r preserves package-private data. Verify the exact version selected by CI.
adb install -r "$candidate_apk"
installed_code="$(installed_version_code)"
if [[ "$installed_code" != "$expected_code" ]]; then
  echo "::error::Expected installed candidate versionCode $expected_code, got $installed_code"
  exit 1
fi
echo "Verified installed versionCode upgrade: $baseline_code -> $installed_code"
adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat files/stillnote-notes.json > "$workdir/after.json"
cmp "$workdir/before.json" "$workdir/after.json" || {
  echo "::error::Upgrade modified or erased existing note storage"
  exit 1
}
echo "::endgroup::"
echo "Android in-place update passed: old memo bytes intact after new app launched."
