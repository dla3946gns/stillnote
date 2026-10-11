#!/usr/bin/env python3
"""Local metadata preflight; not a substitute for Play Console policy review."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
data = json.loads((ROOT / "play" / "listing-ko.json").read_text(encoding="utf-8"))
expected = {
    "developerName": "Anna Johnny Works",
    "contactEmail": "dla3946gns@gmail.com",
    "website": "https://dla3946gns.github.io/stillnote/",
    "privacyPolicy": "https://dla3946gns.github.io/stillnote/privacy/",
    "appId": "com.stillnotes.app",
}
for field, value in expected.items():
    assert data[field] == value, f"Mismatch in {field}"
for field, limit in (("appName", 30), ("shortDescription", 80), ("fullDescription", 4000)):
    value = data[field].strip()
    assert value, f"{field} is blank"
    assert len(value) <= limit, f"{field} exceeds {limit} characters ({len(value)})"
assert data["language"] == "ko-KR"
assert re.fullmatch(r"[a-zA-Z0-9_.+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}", data["contactEmail"])
assert "자동 클라우드 동기화는 지원하지 않습니다" in data["fullDescription"]
assert "draft" in data["reviewStatus"]
print("Play listing metadata validates; operator/policy final review remains required.")
