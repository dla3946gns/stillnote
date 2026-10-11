#!/usr/bin/env python3
"""Verify that the offline Android notice and public website show identical content.

Run from anywhere: python3 mobile/scripts/verify-privacy-policy.py
"""
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP_POLICY = ROOT / "mobile/androidApp/src/main/assets/privacy-policy-ko.txt"
WEB_POLICY = ROOT / "docs/privacy/index.html"


class PrivacyTextParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.active = False
        self.matches = 0
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "pre" and dict(attrs).get("id") == "privacy-policy-text":
            assert not self.active, "Nested policy blocks are invalid"
            self.active = True
            self.matches += 1

    def handle_endtag(self, tag):
        if tag == "pre" and self.active:
            self.active = False

    def handle_data(self, data):
        if self.active:
            self.parts.append(data)


def main():
    source = APP_POLICY.read_text(encoding="utf-8").strip()
    page = WEB_POLICY.read_text(encoding="utf-8")
    parser = PrivacyTextParser()
    parser.feed(page)
    assert parser.matches == 1, "The public page must contain exactly one privacy policy block"
    assert not parser.active, "The public policy block is not closed"
    actual = "".join(parser.parts).strip()
    assert actual == source, "App and public-site privacy notices differ!"
    required = ("Anna Johnny Works", "dla3946gns@gmail.com", "2026년 10월 12일")
    for item in required:
        assert item in source, f"Missing verified operator information: {item}"
    assert "mailto:dla3946gns@gmail.com" in page, "Public site lacks support contact link"
    assert "개인정보처리방침" in page and 'lang="ko"' in page
    print("Privacy policy matches byte-for-byte as Unicode text across app asset and HTML page.")


if __name__ == "__main__":
    main()
