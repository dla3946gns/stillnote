"""Offline tests for the Google Play release safety checks."""

import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from google_play_release import check_existing_track, verify_release

SHA = "a" * 40
MANIFEST = (
    b'<?xml version="1.0"?><manifest xmlns:android="http://schemas.android.com/apk/res/android"'
    b' package="com.stillnotes.app" android:versionCode="2" android:versionName="0.1.1">'
    b'<application android:allowBackup="false" /></manifest>'
)


class GooglePlayReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.aab = root / "androidApp-release.aab"
        self.aab.write_bytes(b"signed artifact test fixture")
        self.checksum = root / "androidApp-release.aab.sha256"
        self.checksum.write_text(hashlib.sha256(self.aab.read_bytes()).hexdigest() + "  output.aab\n")
        self.manifest = root / "release-manifest.xml"
        self.manifest.write_bytes(MANIFEST)
        self.summary = root / "release-privacy-summary.json"
        self.summary.write_text(json.dumps({
            "commit": SHA, "variant": "release", "manifests": [{
                "package": "com.stillnotes.app", "allowBackup": "false",
                "debuggable": None, "sha256": hashlib.sha256(MANIFEST).hexdigest(),
            }]
        }))

    def tearDown(self):
        self.tmp.cleanup()

    def verify(self):
        verify_release(self.aab, self.checksum, self.manifest, self.summary, SHA, 2, "0.1.1")

    def test_valid_release(self):
        self.verify()

    def test_checksum_mismatch(self):
        self.aab.write_bytes(b"tampered AAB")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            self.verify()

    def test_version_mismatch(self):
        with self.assertRaisesRegex(ValueError, "versionCode"):
            verify_release(self.aab, self.checksum, self.manifest, self.summary, SHA, 3, "0.1.1")

    def test_source_commit_mismatch(self):
        with self.assertRaisesRegex(ValueError, "source SHA"):
            verify_release(self.aab, self.checksum, self.manifest, self.summary, "b" * 40, 2, "0.1.1")

    def test_staged_or_halted_release_rejected(self):
        for status in ["draft", "inProgress", "halted"]:
            with self.subTest(status=status), self.assertRaises(ValueError):
                check_existing_track({"releases": [{"status": status}]})

    def test_completed_release_allowed(self):
        self.assertEqual(len(check_existing_track({"releases": [{"status": "completed"}]})), 1)


if __name__ == "__main__":
    unittest.main()
