#!/usr/bin/env python3
"""Preflight validation and manual Google Play production submission for AJ Notes."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

PACKAGE_NAME = "com.stillnotes.app"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def verify_release(aab: Path, checksum: Path, manifest: Path, summary: Path,
                   expected_sha: str, version_code: int, version_name: str):
    if not re.fullmatch(r"[0-9a-f]{40}", expected_sha):
        raise ValueError("Expected source commit must be a full lowercase 40-character SHA.")
    if not aab.is_file() or aab.stat().st_size == 0:
        raise ValueError("Signed AAB is missing or empty.")
    digest = hashlib.sha256(aab.read_bytes()).hexdigest()
    recorded = checksum.read_text(encoding="utf-8").split()[0].lower()
    if not re.fullmatch(r"[0-9a-f]{64}", recorded) or digest != recorded:
        raise ValueError("Signed AAB SHA-256 does not match the build artifact checksum.")

    root = ET.parse(manifest).getroot()
    if root.tag != "manifest" or root.get("package") != PACKAGE_NAME:
        raise ValueError("Unexpected AJ Notes release package.")
    if root.get(ANDROID_NS + "versionCode") != str(version_code):
        raise ValueError("Release manifest versionCode mismatch.")
    if root.get(ANDROID_NS + "versionName") != version_name:
        raise ValueError("Release manifest versionName mismatch.")
    application = root.find("application")
    if application is None or application.get(ANDROID_NS + "debuggable") == "true":
        raise ValueError("Release application is missing or debuggable.")
    if application.get(ANDROID_NS + "allowBackup") != "false":
        raise ValueError("Unexpected release backup policy.")

    evidence = json.loads(summary.read_text(encoding="utf-8"))
    if evidence.get("commit") != expected_sha or evidence.get("variant") != "release":
        raise ValueError("Release evidence source SHA or build variant mismatch.")
    manifest_digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
    if not any(
        item.get("sha256") == manifest_digest
        and item.get("package") == PACKAGE_NAME
        and item.get("allowBackup") == "false"
        and item.get("debuggable") in (None, "false")
        for item in evidence.get("manifests", [])
    ):
        raise ValueError("Release manifest does not match the privacy verification.")
    print(f"Verified AJ Notes {version_name} (versionCode {version_code}), SHA256: {digest}")


def check_existing_track(track):
    """Never replace a staged, halted, or draft production rollout."""
    releases = track.get("releases", [])
    if any(release.get("status") != "completed" for release in releases):
        raise ValueError("Production track contains a non-completed release; refusing overwrite.")
    return releases


def submit_release(aab: Path, version_code: int, version_name: str, notes: str):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    json_key = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "")
    if not json_key.strip():
        raise ValueError("Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON environment Secret.")
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(json_key), scopes=[SCOPE]
    )
    session = AuthorizedSession(credentials)
    base = f"{API}/applications/{PACKAGE_NAME}/edits"

    def call(method, url, **kwargs):
        response = session.request(method, url, timeout=(20, 180), **kwargs)
        if not response.ok:
            try:
                message = response.json().get("error", {}).get("message", "unknown error")
            except ValueError:
                message = "non-JSON response"
            raise RuntimeError(f"Google Play API HTTP {response.status_code}: {message[:500]}")
        return response.json()

    edit_id = call("POST", base, json={})["id"]
    edit_base = f"{base}/{edit_id}"
    track_response = session.get(f"{edit_base}/tracks/production", timeout=(20, 60))
    if track_response.status_code == 404:
        releases = []
    elif track_response.ok:
        releases = check_existing_track(track_response.json())
    else:
        raise RuntimeError(f"Unable to check current production track: HTTP {track_response.status_code}.")
    if any(str(version_code) in map(str, release.get("versionCodes", [])) for release in releases):
        raise ValueError("This versionCode is already on the production track.")

    with aab.open("rb") as stream:
        bundle = call(
            "POST",
            f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/"
            f"{PACKAGE_NAME}/edits/{edit_id}/bundles?uploadType=media",
            data=stream,
            headers={"Content-Type": "application/octet-stream"},
        )
    if str(bundle.get("versionCode")) != str(version_code):
        raise RuntimeError("Google Play returned an unexpected versionCode; no edit was committed.")

    call("PUT", f"{edit_base}/tracks/production", json={
        "track": "production",
        "releases": [{
            "name": version_name,
            "versionCodes": [str(version_code)],
            "status": "completed",
            "releaseNotes": [{"language": "ko-KR", "text": notes}],
        }],
    })
    # Default behavior cancels previously in-review changes; never permit that.
    call("POST", f"{edit_base}:commit", params={
        "changesNotSentForReview": "false",
        "changesInReviewBehavior": "ERROR_IF_IN_REVIEW",
    })
    print("Google Play edit committed. Check review, managed publishing, and rollout in Play Console.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "submit"])
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--checksum", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--notes", type=Path, required=True)
    args = parser.parse_args()
    verify_release(args.aab, args.checksum, args.manifest, args.summary,
                   args.expected_sha, args.version_code, args.version_name)
    notes = args.notes.read_text(encoding="utf-8").strip()
    if not notes or len(notes) > 500 or args.version_name not in notes:
        raise ValueError("Release notes must be 1-500 characters and include the version name.")
    if args.command == "submit":
        submit_release(args.aab, args.version_code, args.version_name, notes)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, ET.ParseError, KeyError, IndexError) as exc:
        print(f"Release rejected: {exc}", file=sys.stderr)
        sys.exit(1)
