#!/usr/bin/env python3
"""Local metadata preflight; not a substitute for Play Console policy review."""
import json
from pathlib import Path

from google_play_listing import read_metadata

ROOT = Path(__file__).resolve().parents[1]
data = json.loads((ROOT / "play" / "listing-ko.json").read_text(encoding="utf-8"))
expected = {
    "developerName": "Anna Johnny Works",
    "privacyPolicy": "https://dla3946gns.github.io/stillnote/privacy/",
    "appId": "com.stillnotes.app",
}
for field, value in expected.items():
    assert data[field] == value, f"Mismatch in {field}"
_, contact = read_metadata(data)
assert contact.get("contactEmail"), "Missing contactEmail"
assert contact.get("contactWebsite"), "Missing website"
assert data["language"] == "ko-KR"
assert "자동 클라우드 동기화" in data["fullDescription"] and "지원하지 않습니다" in data["fullDescription"], "Full description must not claim sync support"
assert "draft" in data["reviewStatus"]
print("Play listing metadata validates; operator/policy final review remains required.")
