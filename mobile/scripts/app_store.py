#!/usr/bin/env python3
"""Operate on an explicitly selected AJ Notes iOS version in App Store Connect."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import urlencode, urlsplit

from app_store_screenshots import placements, sync_screenshots

APP = "6835123279"
ROOT = Path(__file__).resolve().parents[1] / "app-store"
MODES = ("status", "metadata", "append-screenshots", "replace-screenshots", "submit-review")
VERSION = r"[0-9]+(?:\.[0-9]+){0,2}"
LOCALE = r"[a-z]{2,3}(?:-[A-Za-z0-9]+)*"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "INVALID_BINARY", "REJECTED", "METADATA_REJECTED", "DEVELOPER_REJECTED"}
APP_FIELDS = {"name": 30, "subtitle": 30, "privacyPolicyUrl": None}
VERSION_FIELDS = {"description": 4000, "keywords": 100, "whatsNew": 4000, "promotionalText": 170,
                  "supportUrl": None, "marketingUrl": None}


def asc(*args, body=None):
    cmd = ["asc", *args]
    if body is not None:
        cmd += ["--body-file", "-"]
    result = subprocess.run(cmd, input=json.dumps(body) if body is not None else None,
                            stdout=subprocess.PIPE, text=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}


def fields(values, limits):
    if not isinstance(values, dict) or values.keys() - limits.keys():
        raise ValueError(f"Expected an object with fields from {', '.join(limits)}")
    for key, value in values.items():
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{key} must contain text")
        if key == "keywords" and len(value.encode("utf-8")) > 100:
            raise ValueError("keywords exceeds Apple's 100-byte limit")
        if limits[key] and len(value) > limits[key]:
            raise ValueError(f"{key} exceeds {limits[key]} characters")
        if key.endswith("Url") and (urlsplit(value).scheme not in {"http", "https"} or not urlsplit(value).hostname):
            raise ValueError(f"{key} must be an HTTP(S) URL")
    return values


def read_config(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.keys() - {"appInfo", "versionInfo", "screenshots"}:
        raise ValueError(f"Unknown listing section in {path}")
    app = fields(data.get("appInfo", {}), APP_FIELDS)
    version = fields(data.get("versionInfo", {}), VERSION_FIELDS)
    groups = data.get("screenshots", {})
    if not isinstance(groups, dict):
        raise ValueError("screenshots must map device groups to ordered image lists")
    images = {}
    for group, entries in groups.items():
        if not re.fullmatch(r"[A-Z0-9_]+", group) or not isinstance(entries, list) or not entries:
            raise ValueError(f"Invalid or empty screenshot group: {group}")
        assets = []
        for entry in entries:
            if isinstance(entry, dict) and entry.keys() == {"assetId"} and isinstance(entry["assetId"], str):
                if not re.fullmatch(r"[A-Za-z0-9-]+", entry["assetId"]):
                    raise ValueError("Invalid Asset Library image ID")
                assets.append((entry["assetId"], None))
                continue
            if not isinstance(entry, str) or Path(entry).is_absolute():
                raise ValueError("Expected a relative image path or an assetId object")
            image = (path.parent / entry).resolve()
            if not image.is_relative_to(path.parent.resolve()) or image.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
                raise ValueError(f"Expected a PNG or JPEG inside the locale directory: {entry}")
            with image.open("rb") as source:
                digest = hashlib.file_digest(source, "sha256").hexdigest()
            assets.append(("AJ-" + digest, image))
        if len({key for key, _ in assets}) != len(assets):
            raise ValueError(f"Duplicate screenshot in {group}")
        images[group] = assets
    return app, version, images


def localization(version_id, locale, app_info=False):
    selector = ("--type", "app-info") if app_info else ("--version", version_id)
    rows = asc("localizations", "list", "--app", APP, *selector, "--locale", locale, "--paginate")["data"]
    found = [row for row in rows if row["attributes"]["locale"] == locale]
    if len(found) != 1:
        raise ValueError(f"Expected one existing localization for {locale}; found {len(found)}")
    return found[0]


def patch_metadata(target, values):
    patch = {k: v for k, v in values.items() if target["attributes"].get(k) != v}
    if patch:
        kind, identifier = target["type"], target["id"]
        result = asc("api", "PATCH", f"/v1/{kind}/{identifier}", "--confirm",
                     body={"data": {"type": kind, "id": identifier, "attributes": patch}})["data"]
        if any(result["attributes"].get(k) != v for k, v in patch.items()):
            raise ValueError("Apple returned different metadata. Read the version before retrying.")
        print("Updated " + ", ".join(patch))


def version_info(version):
    return asc("versions", "view", "--app", APP, "--version", version, "--platform", "IOS",
               "--include", "build")


def ensure_review_version(version):
    # Only create after a successful, exact filtered lookup. Authentication or
    # network errors must not be mistaken for a missing version.
    query = urlencode({"filter[platform]": "IOS", "filter[versionString]": version, "limit": 2})
    response = asc("api", "GET", f"/v1/apps/{APP}/appStoreVersions?{query}")
    rows = response["data"]
    if response.get("links", {}).get("next") or len(rows) > 1:
        raise ValueError("Ambiguous App Store version lookup")
    if rows:
        row = rows[0]
        if row["attributes"]["platform"] != "IOS" or row["attributes"]["versionString"] != version:
            raise ValueError("Apple returned a different version")
        return version_info(version)
    # New versions are manually released after review; existing settings are not changed.
    created = asc("api", "POST", "/v1/appStoreVersions", "--confirm", body={
        "data": {"type": "appStoreVersions",
                 "attributes": {"platform": "IOS", "versionString": version, "releaseType": "MANUAL"},
                 "relationships": {"app": {"data": {"type": "apps", "id": APP}}}}
    })["data"]
    print(f"Created iOS {version} for manual release", flush=True)
    record = version_info(version)
    if record["data"]["id"] != created["id"]:
        raise ValueError("New version ID does not match Apple's lookup")
    return record


def prepare_review_notes(version, locale, record, state):
    # Never edit a version that is already in review or has been released.
    path = ROOT / version / locale / "listing.json"
    if state not in EDITABLE or not path.is_file():
        return
    _, info, _ = read_config(path)
    if "whatsNew" in info:
        patch_metadata(localization(record["id"], locale), {"whatsNew": info["whatsNew"]})


def ensure_export_compliance(build_id, number):
    """Preserve existing declarations; write only an explicitly confirmed exemption."""
    policy = os.environ.get("STORE_EXPORT_COMPLIANCE", "preserve")
    if policy not in {"preserve", "confirm-no-non-exempt"}:
        raise ValueError("Unknown export compliance policy")
    if not isinstance(build_id, str) or not re.fullmatch(r"[A-Za-z0-9-]+", build_id):
        raise ValueError("Review preview did not identify an exact build")
    endpoint = f"/v1/builds/{build_id}"
    query = urlencode({"fields[builds]": "version,usesNonExemptEncryption,processingState"})
    def read():
        row = asc("api", "GET", f"{endpoint}?{query}")["data"]
        if row["id"] != build_id or row["type"] != "builds" or row["attributes"]["version"] != number:
            raise ValueError("Export compliance lookup returned a different build")
        if row["attributes"]["processingState"] != "VALID":
            raise ValueError("Build is not valid for export compliance")
        return row["attributes"]["usesNonExemptEncryption"]

    current = read()
    if current is True:
        if policy == "confirm-no-non-exempt":
            raise ValueError("Existing non-exempt encryption declaration conflicts with confirmation; review in App Store Connect")
        print("Existing non-exempt encryption declaration preserved; Apple may require supporting documents")
        return
    if current is False:
        print("Export compliance verified: no non-exempt encryption")
        return
    if current is not None:
        raise ValueError("Unexpected export compliance value")
    if policy != "confirm-no-non-exempt":
        raise ValueError("Export compliance missing. Confirm the build's exemption in App Store Connect or explicitly select confirm-no-non-exempt after checking all dependencies.")
    asc("api", "PATCH", endpoint, "--confirm",
        body={"data": {"type": "builds", "id": build_id,
                       "attributes": {"usesNonExemptEncryption": False}}})
    if read() is not False:
        raise ValueError("Apple did not confirm the export compliance update")
    print("Export compliance updated and verified: no non-exempt encryption")


def submit_review(version, number, version_id):
    if not re.fullmatch(VERSION, number):
        raise ValueError("Enter the exact TestFlight build number (CFBundleVersion)")
    asc("builds", "wait", "--app", APP, "--version", version, "--platform", "IOS",
        "--build-number", number, "--fail-on-invalid", "--timeout", "40m", "--poll-interval", "30s")
    args = ("review", "submit", "--app", APP, "--version-id", version_id, "--platform", "IOS",
            "--build-number", number)
    preview = asc(*args, "--dry-run")
    if not preview.get("alreadySubmitted"):
        items = [(i["type"], i["id"]) for i in preview.get("reviewItems", [])]
        if items != [("appStoreVersions", version_id)]:
            raise ValueError("The review draft includes other items or does not identify this version; inspect it in App Store Connect.")
        ensure_export_compliance(preview["buildId"], number)
        receipt = asc(*args, "--confirm")
    else:
        receipt = preview
    if not receipt.get("submissionId"):
        raise ValueError("Apple did not return a review submission ID")
    # An existing submission can refer to an earlier build of the same version.
    for attempt in range(4):
        actual = version_info(version)["data"]["relationships"]["build"].get("data")
        if actual and actual["id"] == receipt["buildId"] == preview["buildId"]:
            print(f"App Review submission {receipt['submissionId']}, iOS {version} ({number})")
            return
        if attempt < 3:
            time.sleep(2)
    raise ValueError("The version's attached build differs from the requested build; check the existing submission.")


def run(mode, version, locale, group, number):
    if not re.fullmatch(VERSION, version) or not re.fullmatch(LOCALE, locale):
        raise ValueError("Enter a numeric iOS version and an App Store locale (e.g. ko)")
    if mode == "submit-review" and not re.fullmatch(VERSION, number):
        raise ValueError("Enter the exact TestFlight build number (CFBundleVersion)")
    response = ensure_review_version(version) if mode == "submit-review" else version_info(version)
    record = response["data"]
    attributes = record["attributes"]
    if attributes["platform"] != "IOS" or attributes["versionString"] != version:
        raise ValueError("App Store Connect returned a different version")
    state = attributes.get("appVersionState") or attributes["appStoreState"]
    print(f"AJ Notes iOS {version}: {state}", flush=True)
    if mode == "submit-review":
        prepare_review_notes(version, locale, record, state)
        return submit_review(version, number, record["id"])
    if mode == "status":
        build = next((r for r in response.get("included", []) if r["type"] == "builds"), None)
        print("Attached build:", build["attributes"]["version"] if build else "none")
        loc = localization(record["id"], locale)
        for item in placements(asc, loc["id"], group):
            print(f"{item['attributes']['placementGroup']}: image {item['relationships']['image']['data']['id']} (placement {item['id']})")
        return
    app, info, images = read_config(ROOT / version / locale / "listing.json")
    if mode == "metadata":
        if not (app or info):
            raise ValueError("No metadata fields configured")
        # Apple enforces field-specific editability, including promotionalText on live versions.
        targets = []
        if app:
            app_target = localization(record["id"], locale, app_info=True)
            if state not in EDITABLE and any(app_target["attributes"].get(k) != v for k, v in app.items()):
                raise ValueError(f"Choose the editable version for app-level metadata changes (selected {state})")
            targets.append((app_target, app))
        if info:
            targets.append((localization(record["id"], locale), info))
        for target, values in targets:
            patch_metadata(target, values)
        return
    if state not in EDITABLE:
        raise ValueError(f"Screenshot edits are unavailable in state {state}")
    if group not in images:
        raise ValueError("Specify a device group configured in the locale's listing.json")
    loc = localization(record["id"], locale)
    sync_screenshots(asc, APP, loc["id"], group, images[group], replace=mode == "replace-screenshots")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=(*MODES, "check-files"))
    parser.add_argument("--version", default="1.0")
    parser.add_argument("--locale", default="ko")
    parser.add_argument("--group", default="")
    parser.add_argument("--build-number", default="")
    args = parser.parse_args()
    if args.mode == "check-files":
        for path in sorted(ROOT.glob("*/*/listing.json")):
            read_config(path)
            print("Validated", path.relative_to(ROOT))
    else:
        run(args.mode, args.version, args.locale, args.group, args.build_number)


if __name__ == "__main__":
    try:
        main()
    except (KeyError, OSError, ValueError, subprocess.CalledProcessError) as exc:
        sys.exit(f"App Store operation failed: {exc}")
