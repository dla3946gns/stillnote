#!/usr/bin/env python3
"""Fail a signed Android release if its packaged manifest violates the reviewed data-flow baseline."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT = Path("androidApp/build/intermediates")
OUT = Path("androidApp/build/outputs/bundle/release")
PACKAGE = "com.stillnotes.app"
ALLOWED = {PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
ANDROID = "{http://schemas.android.com/apk/res/android}"

paths = sorted(
    path for path in ROOT.rglob("AndroidManifest.xml")
    if "release" in path.parts
    and {"merged_manifest", "merged_manifests", "packaged_manifests"}.intersection(path.parts)
)
if not paths or not any("packaged_manifests" in path.parts for path in paths):
    raise SystemExit("Missing packaged release AndroidManifest.xml; cannot assess privacy settings")

records = []
for path in paths:
    root = ET.parse(path).getroot()
    app = root.find("application")
    permissions = sorted(
        item.get(ANDROID + "name") or "" for item in root
        if item.tag.startswith("uses-permission")
    )
    if root.get("package") != PACKAGE:
        raise SystemExit(f"Unexpected package in {path}: {root.get('package')}")
    if app is None or app.get(ANDROID + "allowBackup") != "false":
        raise SystemExit(f"Backup must be explicitly disabled in {path}")
    if app.get(ANDROID + "debuggable", "false") != "false":
        raise SystemExit(f"Release must not be debuggable: {path}")
    unexpected = sorted(set(permissions) - ALLOWED)
    if unexpected:
        raise SystemExit(f"New Android permission(s) require Data safety review: {unexpected}")
    records.append({
        "path": str(path),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "package": root.get("package"),
        "permissions": permissions,
        "allowBackup": app.get(ANDROID + "allowBackup"),
        "debuggable": app.get(ANDROID + "debuggable"),
    })

OUT.mkdir(parents=True, exist_ok=True)
packaged = next(path for path in paths if "packaged_manifests" in path.parts)
shutil.copyfile(packaged, OUT / "release-manifest.xml")
report = {
    "commit": os.environ.get("GITHUB_SHA"),
    "variant": "release",
    "manifests": records,
    "limitations": [
        "This is static Android manifest inspection, not an audit of runtime network traffic.",
        "The owner-signed AAB must also pass Play's upload-certificate verification.",
        "A new dependency or data flow requires a manual Data safety review.",
    ],
}
(OUT / "release-privacy-summary.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(report, ensure_ascii=False, indent=2))
