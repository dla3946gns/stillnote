#!/usr/bin/env python3
"""Static parity, Firebase project, and memo preservation checks for web auth."""
from html.parser import HTMLParser
from pathlib import Path
import json
import plistlib
import re

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "dist/account"
DOCS = ROOT / "docs/account"

class HtmlInspector(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if "id" in attributes:
            assert attributes["id"] not in self.ids, "Duplicate HTML ID: " + attributes["id"]
            self.ids.add(attributes["id"])

for filename in ("index.html", "app.js", "rules.js", "config.js"):
    assert (DIST / filename).read_bytes() == (DOCS / filename).read_bytes(), (
        f"Web and Pages account sources diverged: {filename}"
    )

page = (DIST / "index.html").read_text(encoding="utf-8")
inspector = HtmlInspector()
inspector.feed(page)
required_ids = {"loginPanel", "userPanel", "loginForm", "accountEmailInput",
    "accountPasswordInput", "accountStatus", "deleteForm", "deletePassword",
    "deletionConsent", "switchLogin", "switchRegister", "switchReset",
    "signOutButton", "deleteToggle", "userEmail"}
assert required_ids <= inspector.ids, "Missing account UI controls: " + str(required_ids - inspector.ids)
assert 'src="./app.js"' in page and 'type="password"' in page
assert "회원가입" in page and "계정 영구 삭제" in page

notes = (ROOT / "dist/index.html").read_text(encoding="utf-8")
assert "simple-notes-v1" in notes, "Existing local note storage must remain"
assert 'href="./account/"' in notes, "The web notes UI must link to account management"
assert "disableTelemetry: true" in (ROOT / "src/sidebar-shader.js").read_text(encoding="utf-8")

sdk = (DIST / "app.js").read_text(encoding="utf-8")
assert "https://www.gstatic.com/firebasejs/13.1.0/firebase-auth.js" in sdk
assert "sdk.reauthenticateWithCredential" in sdk
assert "sdk.deleteUser(user)" in sdk
assert "auth.currentUser.uid !== uid" in sdk
assert "password.value = \"\"" in sdk and "deletePassword.value = \"\"" in sdk
assert "console.log" not in sdk and "localStorage" not in sdk
config = (DIST / "config.js").read_text(encoding="utf-8")
fields = dict(re.findall(r'^  (\w+): "([^"]+)"', config, re.MULTILINE))
assert set(fields) == {"apiKey", "authDomain", "projectId", "appId"}
assert re.fullmatch(r"AIza[\w-]{35}", fields["apiKey"]), "Invalid public Firebase API key"
assert fields["authDomain"] == "stilenote.firebaseapp.com"
assert fields["projectId"] == "stilenote"
assert fields["appId"] == "1:218352371308:web:eb4af67b7577182268ba49"
android = json.loads((ROOT / "mobile/androidApp/google-services.json").read_text(encoding="utf-8"))
ios = plistlib.loads((ROOT / "mobile/iosApp/Stillnote/GoogleService-Info.plist").read_bytes())
assert android["project_info"]["project_id"] == ios["PROJECT_ID"] == fields["projectId"]
assert android["project_info"]["project_number"] == ios["GCM_SENDER_ID"] == "218352371308"
delete = (ROOT / "docs/delete-account/index.html").read_text(encoding="utf-8")
assert '../account/?mode=delete' in delete
assert "Stillnote 계정 삭제 요청" in delete
assert "비밀번호·메모·백업 파일은 보내지 마세요" in delete
print("Stillnote web auth parity, shared Firebase project, memo preservation, and deletion access checks passed.")
