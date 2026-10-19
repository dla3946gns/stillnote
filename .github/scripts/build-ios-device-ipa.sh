#!/usr/bin/env bash
set -euo pipefail

# Build a real iPhone binary and package it for later local signing (for example,
# with Sideloadly on Windows). This never signs, provisions, installs or uploads
# an app to Apple. The resulting IPA cannot launch until it has been signed.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${RUNNER_TEMP:?Run this build on the GitHub macOS runner}"
ARTIFACTS="$RUNNER_TEMP/stillnote-ios-device"
DERIVED_DATA="$RUNNER_TEMP/stillnote-ios-device-derived"
PACKAGE="$RUNNER_TEMP/stillnote-ios-device-package"
APP="$DERIVED_DATA/Build/Products/Release-iphoneos/Stillnote.app"
IPA="$ARTIFACTS/Stillnote-iPhone-unsigned.ipa"
mkdir -p "$ARTIFACTS" "$PACKAGE/Payload"
cd "$ROOT"

{
  sw_vers
  uname -m
  xcodebuild -version
  xcodebuild -showsdks
  java -version 2>&1
  git rev-parse HEAD
} | tee "$ARTIFACTS/environment.txt"

chmod +x mobile/gradlew
xcodebuild build \
  -project mobile/iosApp/Stillnote.xcodeproj \
  -scheme Stillnote \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED_DATA" \
  -resultBundlePath "$ARTIFACTS/build.xcresult" \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES DEVELOPMENT_TEAM='' \
  CODE_SIGN_IDENTITY='' CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
  2>&1 | tee "$ARTIFACTS/build.log"

test -x "$APP/Stillnote"
test ! -e "$APP/embedded.mobileprovision"
file "$APP/Stillnote" | tee "$ARTIFACTS/app-binary.txt"
[[ "$(lipo -archs "$APP/Stillnote")" == arm64 ]]
xcrun vtool -show-build "$APP/Stillnote" | tee "$ARTIFACTS/binary-platform.txt"
grep -Eq '^[[:space:]]*platform[[:space:]]+IOS[[:space:]]*$' "$ARTIFACTS/binary-platform.txt"
otool -L "$APP/Stillnote" > "$ARTIFACTS/linked-libraries.txt"

ditto "$APP" "$PACKAGE/Payload/Stillnote.app"
(
  cd "$PACKAGE"
  /usr/bin/zip -qry "$IPA" Payload
)

python3 - "$IPA" "$ARTIFACTS/package-info.json" <<'PY'
import hashlib
import json
import plistlib
import sys
import zipfile
from pathlib import Path

ipa = Path(sys.argv[1])
with zipfile.ZipFile(ipa) as archive:
    assert archive.testzip() is None, "IPA ZIP integrity check failed"
    names = archive.namelist()
    assert all(name.startswith("Payload/") and ".." not in Path(name).parts for name in names)
    info = plistlib.loads(archive.read("Payload/Stillnote.app/Info.plist"))
    assert info["CFBundleIdentifier"] == "com.stillnotes.app"
    assert info["CFBundleSupportedPlatforms"] == ["iPhoneOS"], "Simulator package is not installable on iPhone"
    assert info["CFBundleExecutable"] == "Stillnote"
    assert "Payload/Stillnote.app/Stillnote" in names
    assert not any(name.endswith("embedded.mobileprovision") for name in names)

metadata = {
    "file": ipa.name,
    "sizeBytes": ipa.stat().st_size,
    "sha256": hashlib.sha256(ipa.read_bytes()).hexdigest(),
    "bundleId": info["CFBundleIdentifier"],
    "version": info["CFBundleShortVersionString"],
    "build": info["CFBundleVersion"],
    "minimumOSVersion": info["MinimumOSVersion"],
    "platform": "iPhoneOS",
    "architecture": "arm64",
    "configuration": "Release",
    "signing": "Requires local signing and provisioning before installation",
    "physicalDeviceTested": False,
}
Path(sys.argv[2]).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata, indent=2))
PY

(
  cd "$ARTIFACTS"
  shasum -a 256 "$(basename "$IPA")" > Stillnote-iPhone-unsigned.ipa.sha256
)
cat > "$ARTIFACTS/README.txt" <<'TXT'
Stillnote iPhone build for local signing

Stillnote-iPhone-unsigned.ipa contains a Release arm64 iPhoneOS app.
It is not the simulator app, an Ad Hoc signed distribution or an App Store upload.
This build has no Apple account, certificate or device provisioning profile.

On Windows, use a signing/install tool such as Sideloadly with your own Apple
account to sign and install this IPA on a connected iPhone. Free Apple-account
signatures require renewal after 7 days. Enable Developer Mode when requested.
Opening this IPA in the iPhone Files app alone does not install it.

The minimum supported iOS version and SHA-256 are in package-info.json.
No physical iPhone installation or runtime test was performed by this job.
TXT

{
  echo '### iPhone IPA build'
  echo '- Release KMP framework and SwiftUI app compiled for arm64 iPhoneOS.'
  echo '- Binary platform, bundle ID and Payload ZIP structure verified.'
  echo '- Stillnote-iPhone-unsigned.ipa is ready for later local signing.'
  echo '- Apple account signing and physical iPhone installation remain separate steps.'
} >> "$GITHUB_STEP_SUMMARY"
