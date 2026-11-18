#!/usr/bin/env python3
"""Validate and submit AJ Notes main store listing edits to Google Play."""

import argparse
from dataclasses import dataclass
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote, urlsplit

from google_play_release import API, PACKAGE_NAME, SCOPE

PLAY_DIR = Path(__file__).resolve().parents[1] / "play"
LISTING_FIELDS = {"appName": ("title", 30), "shortDescription": ("shortDescription", 80),
                  "fullDescription": ("fullDescription", 4000)}
CONTACT_FIELDS = {"website": "contactWebsite", "contactEmail": "contactEmail",
                  "contactPhone": "contactPhone"}
IMAGE_LIMITS = {"icon": 1, "featureGraphic": 1, "phoneScreenshots": 8,
                "sevenInchScreenshots": 8, "tenInchScreenshots": 8}
IMAGE_FORMATS = {".png": "PNG", ".jpg": "JPEG", ".jpeg": "JPEG"}


@dataclass(frozen=True)
class Asset:
    path: Path
    content_type: str
    sha256: str


@dataclass(frozen=True)
class Listing:
    language: str
    fields: dict[str, str]
    contact: dict[str, str]
    images: dict[str, list[Asset]]


def read_metadata(source):
    fields, contact = {}, {}
    for name, (api_name, limit) in LISTING_FIELDS.items():
        value = source.get(name)
        if not isinstance(value, str) or not 1 <= len(value.strip()) <= limit:
            raise ValueError(f"{name} must be 1–{limit} characters.")
        fields[api_name] = value.strip()

    for name in ("video", *CONTACT_FIELDS):
        if name not in source:
            continue
        value = source[name]
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{name} must contain text when configured.")
        value = value.strip()
        if name in ("video", "website"):
            url = urlsplit(value)
            if url.scheme not in ("http", "https") or not url.hostname:
                raise ValueError(f"{name} must be a valid HTTP(S) URL.")
        if name == "contactEmail" and not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", value):
            raise ValueError("contactEmail must be a valid email address.")
        if name == "video":
            fields[name] = value
        else:
            contact[CONTACT_FIELDS[name]] = value
    return fields, contact


def read_asset(play_dir, name, image_type):
    from PIL import Image

    if not isinstance(name, str) or Path(name).is_absolute():
        raise ValueError("Image paths must be relative to mobile/play.")
    path = (play_dir / name).resolve()
    if not path.is_relative_to(play_dir.resolve() / "assets"):
        raise ValueError(f"{name}: images must be inside mobile/play/assets.")
    expected_format = IMAGE_FORMATS.get(path.suffix.lower())
    if expected_format is None:
        raise ValueError(f"{name}: use PNG or JPEG.")

    data = path.read_bytes()
    try:
        with Image.open(BytesIO(data)) as image:
            if image.format != expected_format or getattr(image, "is_animated", False):
                raise ValueError("expected a still image matching the file extension.")
            if image.format == "PNG" and data[24:25] != b"\x08":  # IHDR bit depth
                raise ValueError("PNG images must have 8 bits per channel.")
            if image_type == "icon":
                if image.format != "PNG" or image.mode != "RGBA" or image.size != (512, 512):
                    raise ValueError("icon must be a 512×512 RGBA PNG.")
                if len(data) > 1_048_576:
                    raise ValueError("icon exceeds 1024 KB.")
            else:
                if image.format == "PNG" and (image.mode != "RGB" or "transparency" in image.info):
                    raise ValueError("use JPEG or RGB PNG without transparency.")
                if image_type == "featureGraphic":
                    if image.size != (1024, 500):
                        raise ValueError("feature graphic must be 1024×500.")
                else:
                    short, long = sorted(image.size)
                    if not 320 <= short <= long <= 3840 or long > 2 * short:
                        raise ValueError("screenshot sides must be 320–3840 px with a ratio of 2:1 or less.")
            image.load()
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise ValueError(f"{name}: {exc}") from exc
    mime = "image/png" if expected_format == "PNG" else "image/jpeg"
    return Asset(path, mime, hashlib.sha256(data).hexdigest())


def read_images(play_dir, source):
    if not isinstance(source, dict):
        raise ValueError("images must be an object keyed by Play image type.")
    images = {}
    for image_type, names in source.items():
        if image_type not in IMAGE_LIMITS:
            raise ValueError(f"Unsupported image type: {image_type}.")
        limit = IMAGE_LIMITS[image_type]
        if not isinstance(names, list) or not 1 <= len(names) <= limit:
            raise ValueError(f"{image_type} must contain between 1 and {limit} image paths.")
        assets = [read_asset(play_dir, name, image_type) for name in names]
        if len({asset.sha256 for asset in assets}) != len(assets):
            raise ValueError(f"{image_type} contains duplicate images.")
        images[image_type] = assets
    return images


def load_listings(play_dir, language=None):
    listings, seen = [], set()
    for path in sorted(play_dir.glob("listing-*.json")):
        try:
            source = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(source, dict) or source.get("appId", PACKAGE_NAME) != PACKAGE_NAME:
                raise ValueError(f"Expected a listing object for {PACKAGE_NAME}.")
            locale = source.get("language")
            if not isinstance(locale, str) or not re.fullmatch(r"[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", locale):
                raise ValueError("language must be a Google Play locale such as ko-KR or en-US.")
            if locale.lower() in seen:
                raise ValueError(f"Duplicate locale {locale}.")
            seen.add(locale.lower())
            if language is not None and language.lower() != locale.lower():
                continue
            fields, contact = read_metadata(source)
            images = read_images(play_dir, source.get("images", {}))
            listings.append(Listing(locale, fields, contact, images))
        except (OSError, ValueError) as exc:
            raise ValueError(f"{path.name}: {exc}") from exc
    if not listings:
        raise ValueError(f"No listing file found for {language or 'any locale'} in {play_dir}.")
    return listings


def play_request(session, method, url, **kwargs):
    from google.auth.exceptions import GoogleAuthError
    from requests.exceptions import RequestException

    try:
        response = session.request(method, url, timeout=(20, 120), **kwargs)
    except (GoogleAuthError, RequestException) as exc:
        raise RuntimeError(f"Google Play API request failed ({type(exc).__name__}).") from None
    if not response.ok:
        try:
            message = response.json().get("error", {}).get("message", "unknown error")
        except ValueError:
            message = "non-JSON response"
        raise RuntimeError(f"Google Play API HTTP {response.status_code}: {message[:500]}")
    return response.json() if response.content else {}


def update_metadata(session, edit_url, listing):
    language = listing.language
    listings = play_request(session, "GET", f"{edit_url}/listings").get("listings", [])
    current = next((item for item in listings if item["language"] == language), None)
    url = f"{edit_url}/listings/{language}"
    changed = current is None
    if current is None:
        play_request(session, "PUT", url, json={"language": language, **listing.fields})
        print(f"Created store listing for {language} in the edit.")
    else:
        updates = {key: value for key, value in listing.fields.items() if current.get(key) != value}
        if updates:
            play_request(session, "PATCH", url, json=updates)
            print(f"Updated {language} listing fields in the edit: {', '.join(updates)}")
            changed = True

    if listing.contact:
        url = f"{edit_url}/details"
        current = play_request(session, "GET", url)
        updates = {key: value for key, value in listing.contact.items() if current.get(key) != value}
        if updates:
            play_request(session, "PATCH", url, json=updates)
            print(f"Updated app contact fields in the edit: {', '.join(updates)}")
            changed = True
    return changed


def image_hash(image):
    digest = image.get("sha256", "")
    if not isinstance(digest, str) or not re.fullmatch(r"[a-fA-F0-9]{64}", digest):
        raise RuntimeError("Google Play returned an image without a valid SHA-256 hash.")
    return digest.lower()


def update_images(session, edit_url, language, image_type, assets):
    url = f"{edit_url}/listings/{language}/{image_type}"
    images = play_request(session, "GET", url).get("images", [])
    desired = [asset.sha256 for asset in assets]
    retained = 0
    remove = []
    # Play appends uploads. Keep the longest desired prefix already in the right order.
    for image in images:
        digest = image_hash(image)
        if retained < len(desired) and digest == desired[retained]:
            retained += 1
        else:
            remove.append(image["id"])
    if not remove and retained == len(assets):
        print(f"{language} {image_type}: already up to date.")
        return False

    for image_id in remove:
        play_request(session, "DELETE", f"{url}/{quote(image_id, safe='')}")
    upload_url = url.replace("/androidpublisher/v3/", "/upload/androidpublisher/v3/", 1)
    for asset in assets[retained:]:
        with asset.path.open("rb") as stream:
            play_request(session, "POST", upload_url, params={"uploadType": "media"},
                         data=stream, headers={"Content-Type": asset.content_type})
    updated = play_request(session, "GET", url).get("images", [])
    if [image_hash(image) for image in updated] != desired:
        raise RuntimeError(f"{language} {image_type}: Google Play returned unexpected image contents or order.")
    print(f"{language} {image_type}: kept {retained}, removed {len(remove)}, uploaded {len(assets) - retained}.")
    return True


def submit_listing(session, listing, scope):
    if scope == "images" and not listing.images:
        print(f"{listing.language}: no image types configured.")
        return

    base = f"{API}/applications/{PACKAGE_NAME}/edits"
    edit_id = play_request(session, "POST", base, json={})["id"]
    edit_url = f"{base}/{quote(edit_id, safe='')}"
    committed = False
    try:
        changed = False
        if scope in ("listing", "all"):
            changed = update_metadata(session, edit_url, listing)
        if scope in ("images", "all"):
            for image_type, assets in listing.images.items():
                if update_images(session, edit_url, listing.language, image_type, assets):
                    changed = True
        if not changed:
            print("Google Play is already up to date.")
            return

        play_request(session, "POST", f"{edit_url}:validate")
        play_request(session, "POST", f"{edit_url}:commit", params={
            "changesNotSentForReview": "false",
            "changesInReviewBehavior": "ERROR_IF_IN_REVIEW",
        })
        committed = True
        print("Google Play listing edit committed. Check review and publishing in Play Console.")
    finally:
        if not committed:
            try:
                play_request(session, "DELETE", edit_url)
            except RuntimeError as exc:
                print(f"Could not discard edit: {exc}", file=sys.stderr)


def authorized_session():
    from google.auth.exceptions import GoogleAuthError
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    key = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "")
    if not key.strip():
        raise ValueError("Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON environment secret.")
    try:
        info = json.loads(key)
        if not isinstance(info, dict):
            raise ValueError
        credentials = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    except (ValueError, TypeError, GoogleAuthError):
        raise ValueError("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not a valid service account key.") from None
    return AuthorizedSession(credentials)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["validate", "submit"])
    parser.add_argument("--language", help="Locale from a mobile/play/listing-*.json file")
    parser.add_argument("--scope", choices=["listing", "images", "all"], default="all")
    args = parser.parse_args()
    if args.command == "submit" and not args.language:
        parser.error("submit requires --language")

    listings = load_listings(PLAY_DIR, args.language)
    for listing in listings:
        count = sum(len(assets) for assets in listing.images.values())
        print(f"Validated {listing.language} store metadata and {count} configured images.")
    if args.command == "submit":
        with authorized_session() as session:
            submit_listing(session, listings[0], args.scope)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError) as exc:
        sys.exit(f"Google Play listing rejected: {exc}")
