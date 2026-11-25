"""Offline checks for protected deployment credential handling."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("app_store_ci.sh")


class AuthWrapperTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "calls"
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}",
                        RUNNER_TEMP=str(self.root), ASC_KEY_ID="test-id",
                        ASC_ISSUER_ID="test-issuer", ASC_PRIVATE_KEY="dummy-key",
                        STORE_MODE="submit-review", STORE_VERSION="1.0.1",
                        STORE_BUILD="5.1", TEST_LOG=str(self.log))
        self.stub("asc", """#!/bin/bash
set -eu
[[ "$*" == "api GET /v1/apps/6835123279" ]]
[[ "$ASC_KEY_TYPE" == team && "$ASC_BYPASS_KEYCHAIN" == 1 ]]
[[ -z "${ASC_PRIVATE_KEY:-}" ]]
[[ "$(stat -c %a "$ASC_PRIVATE_KEY_PATH")" == 600 ]]
[[ "$(cat "$ASC_PRIVATE_KEY_PATH")" == dummy-key ]]
echo auth >> "$TEST_LOG"
exit "${TEST_AUTH_EXIT:-0}"
""")
        self.stub("python3", """#!/bin/bash
set -eu
[[ -f "$ASC_PRIVATE_KEY_PATH" ]]
[[ "$*" == "mobile/scripts/app_store.py submit-review --version 1.0.1 --locale ko --group  --build-number 5.1" ]]
echo submit >> "$TEST_LOG"
""")

    def stub(self, name, content):
        p = self.bin / name
        p.write_text(content)
        p.chmod(0o700)

    def run_wrapper(self):
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, text=True, capture_output=True)
        self.assertEqual(list(self.root.glob("asc-auth.*")), [])
        self.assertNotIn("dummy-key", result.stdout + result.stderr)
        return result

    def test_authentication_before_submission_and_cleanup(self):
        self.assertEqual(self.run_wrapper().returncode, 0)
        self.assertEqual(self.log.read_text().splitlines(), ["auth", "submit"])

    def test_missing_secret_fails_before_cli(self):
        self.env["ASC_PRIVATE_KEY"] = ""
        result = self.run_wrapper()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing ASC_PRIVATE_KEY", result.stderr)
        self.assertFalse(self.log.exists())

    def test_authentication_failure_does_not_submit(self):
        self.env["TEST_AUTH_EXIT"] = "3"
        self.assertNotEqual(self.run_wrapper().returncode, 0)
        self.assertEqual(self.log.read_text().splitlines(), ["auth"])


if __name__ == "__main__":
    unittest.main()
