"""Offline signing helper tests. Test keys are ephemeral; GitHub calls are mocked."""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "stillnote_signing_helper", ROOT / "setup_stillnote_signing.py"
)
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


@unittest.skipUnless(shutil.which("keytool"), "Java keytool is unavailable")
class SigningHelperTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="stillnote-signing-test-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.key = self.root / "temporary-test.jks"
        self.alias = "test-only"
        self.store_pw = "TestOnlyStorePassword_2026"
        self.key_pw = "TestOnlyPrivatePassword_2026"
        subprocess.run(
            ["keytool", "-genkeypair", "-storetype", "JKS",
             "-keystore", str(self.key), "-alias", self.alias,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
             "-dname", "CN=Ephemeral Test Only",
             "-storepass:env", "STILLNOTE_LOCAL_STORE_PASSWORD",
             "-keypass:env", "STILLNOTE_LOCAL_KEY_PASSWORD", "-noprompt"],
            env=setup.keytool_env(self.store_pw, self.key_pw),
            capture_output=True, check=True
        )

    def test_valid_private_key_and_sha256(self):
        value = setup.public_certificate_fingerprint(
            self.key, self.alias, self.store_pw, self.key_pw
        )
        self.assertRegex(value, r"^([A-F0-9]{2}:){31}[A-F0-9]{2}$")

    def test_wrong_store_password_fails(self):
        with self.assertRaises(RuntimeError):
            setup.public_certificate_fingerprint(
                self.key, self.alias, "WRONG-STORE-PASSWORD", self.key_pw
            )

    def test_wrong_private_key_password_fails(self):
        with self.assertRaises(RuntimeError):
            setup.public_certificate_fingerprint(
                self.key, self.alias, self.store_pw, "WRONG-KEY-PASSWORD"
            )

    def test_existing_unrelated_backup_is_not_overwritten(self):
        backup = self.root / "backup"
        backup.mkdir()
        other = backup / self.key.name
        other.write_bytes(b"unrelated key")
        with patch("builtins.input", return_value=str(backup)):
            with self.assertRaises(RuntimeError):
                setup.verified_backup(self.key)
        self.assertEqual(other.read_bytes(), b"unrelated key")

    @unittest.skipIf(os.name == "nt", "POSIX fake-gh test")
    def test_registration_and_dispatch_use_mock_github_only(self):
        fakebin = self.root / "bin"
        fakebin.mkdir()
        gh = fakebin / "gh"
        gh.write_text("""#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
state = pathlib.Path(os.environ['STILLNOTE_FAKE_GH_STATE'])
if args[:2] == ['secret', 'list']:
    names = state.read_text().splitlines() if state.exists() else []
    print(json.dumps([{'name': n} for n in names if n != 'WORKFLOW_RUN']))
elif args[:2] == ['secret', 'set']:
    _ = sys.stdin.read()  # Discard secret bytes; never write them.
    names = state.read_text().splitlines() if state.exists() else []
    if args[2] not in names:
        names.append(args[2])
    state.write_text('\\n'.join(names))
elif args[:2] == ['workflow', 'run']:
    names = state.read_text().splitlines() if state.exists() else []
    names.append('WORKFLOW_RUN')
    state.write_text('\\n'.join(names))
elif args[:2] in (
    ['auth', 'status'],
    ['api', 'repos/dla3946gns/stillnote/environments/android-release'],
):
    pass
else:
    sys.exit(2)
""", encoding="utf-8")
        gh.chmod(0o700)
        state = self.root / "state.txt"
        backup = self.root / "backup"
        backup.mkdir()
        inputs = iter(["e", str(self.key), self.alias, str(backup)])
        printed = []

        def capture(*args, **kwargs):
            printed.append(" ".join(str(item) for item in args))

        env = os.environ.copy()
        env["PATH"] = str(fakebin) + os.pathsep + env.get("PATH", "")
        env["STILLNOTE_FAKE_GH_STATE"] = str(state)
        with patch.dict(os.environ, env, clear=True), \
             patch("builtins.input", side_effect=lambda *args: next(inputs)), \
             patch("getpass.getpass", side_effect=[self.store_pw, self.key_pw]), \
             patch.object(setup, "confirm_phrase", return_value=True), \
             patch("builtins.print", side_effect=capture):
            setup.main()
        self.assertEqual(
            set(state.read_text().splitlines()),
            set(setup.SECRETS) | {"WORKFLOW_RUN"},
        )
        self.assertEqual((backup / self.key.name).read_bytes(),
                         self.key.read_bytes())
        self.assertNotIn(self.store_pw, "\n".join(printed))
        self.assertNotIn(self.key_pw, "\n".join(printed))


if __name__ == "__main__":
    unittest.main(verbosity=2)
