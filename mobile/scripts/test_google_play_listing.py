"""Offline tests for store listing validation and Google Play edit behavior."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image

from google_play_listing import API, PACKAGE_NAME, load_listings, submit_listing

EDITS = f"{API}/applications/{PACKAGE_NAME}/edits"
EDIT = f"{EDITS}/test-edit"
SCREENSHOTS = f"{EDIT}/listings/ko-KR/phoneScreenshots"


class GooglePlayListingTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "assets").mkdir()

    def config(self, language="ko-KR", **fields):
        config = {"language": language, "appName": "AJ Notes", "shortDescription": "Short",
                  "fullDescription": "Full", **fields}
        (self.root / f"listing-{language}.json").write_text(json.dumps(config), encoding="utf-8")

    def image(self, name, size, mode="RGB", color=0):
        relative = f"assets/{name}"
        Image.new(mode, size, color).save(self.root / relative)
        return relative

    def test_selects_locale_and_maps_supported_fields(self):
        self.config(developerName="Operator", privacyPolicy="Console-only")
        self.config("en-US", contactEmail="support@example.com", website="https://example.com",
                    contactPhone="+1 555 0100", video="https://www.youtube.com/watch?v=abc")
        [listing] = load_listings(self.root, "en-US")
        self.assertEqual(listing.fields, {"title": "AJ Notes", "shortDescription": "Short",
                                         "fullDescription": "Full", "video": "https://www.youtube.com/watch?v=abc"})
        self.assertEqual(listing.contact, {"contactEmail": "support@example.com",
                                          "contactWebsite": "https://example.com", "contactPhone": "+1 555 0100"})
        [korean] = load_listings(self.root, "ko-KR")
        self.assertEqual(korean.contact, {})
        self.assertNotIn("developerName", korean.fields)
        self.assertNotIn("privacyPolicy", korean.fields)

    def test_reports_invalid_metadata_and_image_paths(self):
        cases = [
            ({"appName": "가" * 31}, "appName"),
            ({"contactEmail": "invalid"}, "contactEmail"),
            ({"images": {"icon": ["../external.png"]}}, "inside mobile/play/assets"),
            ({"images": {"phoneScreenshots": []}}, "between 1 and 8"),
        ]
        for fields, error in cases:
            with self.subTest(fields=fields):
                self.config(**fields)
                with self.assertRaisesRegex(ValueError, error):
                    load_listings(self.root)

    def test_validates_image_format_alpha_and_dimensions(self):
        icon = self.image("icon.png", (512, 512), "RGBA")
        graphic = self.image("feature.jpg", (1024, 500))
        phone = self.image("phone.png", (320, 640))
        tablet = self.image("tablet.jpg", (1600, 1200))
        self.config(images={"icon": [icon], "featureGraphic": [graphic], "phoneScreenshots": [phone],
                            "sevenInchScreenshots": [tablet], "tenInchScreenshots": [tablet]})
        [listing] = load_listings(self.root)
        self.assertEqual(listing.images["icon"][0].content_type, "image/png")
        self.assertEqual(listing.images["featureGraphic"][0].content_type, "image/jpeg")
        cases = [
            ("icon", self.image("opaque.png", (512, 512)), "RGBA"),
            ("featureGraphic", self.image("wrong-size.jpg", (500, 1024)), "1024×500"),
            ("tenInchScreenshots", self.image("wide.png", (640, 1281)), "2:1"),
            ("phoneScreenshots", self.image("alpha.png", (320, 640), "RGBA"), "transparency"),
        ]
        for kind, name, error in cases:
            with self.subTest(kind=kind, name=name):
                self.config(images={kind: [name]})
                with self.assertRaisesRegex(ValueError, error):
                    load_listings(self.root)

    def test_rejects_images_over_the_upload_size_limit(self):
        for kind, mib in (("icon", 1), ("phoneScreenshots", 15)):
            with self.subTest(kind=kind):
                name = "assets/oversize.png"
                with (self.root / name).open("wb") as stream:
                    stream.truncate(mib * 1_048_576 + 1)
                self.config(images={kind: [name]})
                with self.assertRaisesRegex(ValueError, f"{mib} MiB upload limit"):
                    load_listings(self.root)

    @patch("google_play_listing.play_request")
    def test_patches_only_changed_metadata(self, request):
        self.config(contactEmail="new@example.com", website="https://example.com")
        [listing] = load_listings(self.root)
        request.side_effect = [
            {"id": "test-edit"},
            {"listings": [{"language": "ko-KR", **listing.fields, "title": "Old name", "video": "keep"}]},
            {},
            {"defaultLanguage": "ko-KR", "contactEmail": "old@example.com", "contactWebsite": "https://example.com"},
            {}, {}, {},
        ]
        submit_listing(None, listing, "all")
        self.assertEqual([(call.args[1], call.args[2]) for call in request.call_args_list], [
            ("POST", EDITS), ("GET", f"{EDIT}/listings"), ("PATCH", f"{EDIT}/listings/ko-KR"),
            ("GET", f"{EDIT}/details"), ("PATCH", f"{EDIT}/details"),
            ("POST", f"{EDIT}:validate"), ("POST", f"{EDIT}:commit"),
        ])
        self.assertEqual(request.call_args_list[2].kwargs["json"], {"title": "AJ Notes"})
        self.assertEqual(request.call_args_list[4].kwargs["json"], {"contactEmail": "new@example.com"})
        self.assertEqual(request.call_args.kwargs["params"], {
            "changesNotSentForReview": "false", "changesInReviewBehavior": "ERROR_IF_IN_REVIEW",
        })

    @patch("google_play_listing.play_request")
    def test_creates_translation_and_discards_edit_when_commit_is_rejected(self, request):
        self.config("en-US")
        [listing] = load_listings(self.root)
        request.side_effect = [{"id": "test-edit"}, {}, {}, {}, RuntimeError("CHANGES_ALREADY_IN_REVIEW"), {}]
        with self.assertRaisesRegex(RuntimeError, "CHANGES_ALREADY_IN_REVIEW"):
            submit_listing(None, listing, "listing")
        request.assert_any_call(None, "PUT", f"{EDIT}/listings/en-US", json={"language": "en-US", **listing.fields})
        request.assert_called_with(None, "DELETE", EDIT)

    @patch("google_play_listing.play_request")
    def test_reconciles_only_changed_images_and_keeps_screenshot_order(self, request):
        files = [self.image(f"{color}.png", (320, 640), color=color) for color in ("red", "green", "blue")]
        self.config(images={"phoneScreenshots": files})
        [listing] = load_listings(self.root)
        red, green, blue = listing.images["phoneScreenshots"]
        current = {"images": [{"id": "red", "sha256": red.sha256}, {"id": "obsolete", "sha256": "a" * 64},
                              {"id": "green", "sha256": green.sha256}]}
        updated = {"images": [{"sha256": asset.sha256} for asset in (red, green, blue)]}
        request.side_effect = [{"id": "test-edit"}, current, {}, {}, updated, {}, {}]
        submit_listing(None, listing, "images")
        self.assertEqual([(call.args[1], call.args[2]) for call in request.call_args_list], [
            ("POST", EDITS), ("GET", SCREENSHOTS), ("DELETE", f"{SCREENSHOTS}/obsolete"),
            ("POST", SCREENSHOTS.replace("/androidpublisher/v3/", "/upload/androidpublisher/v3/")),
            ("GET", SCREENSHOTS), ("POST", f"{EDIT}:validate"), ("POST", f"{EDIT}:commit"),
        ])
        self.assertEqual(request.call_args_list[3].kwargs["data"].name, str(blue.path))

    @patch("google_play_listing.play_request")
    def test_unchanged_images_do_not_submit_or_replace_any_assets(self, request):
        self.config(images={"phoneScreenshots": [self.image("phone.png", (320, 640))]})
        [listing] = load_listings(self.root)
        [phone] = listing.images["phoneScreenshots"]
        request.side_effect = [{"id": "test-edit"}, {"images": [{"id": "phone", "sha256": phone.sha256}]}, {}]
        submit_listing(None, listing, "images")
        self.assertEqual(request.call_count, 3)
        request.assert_called_with(None, "DELETE", EDIT)

    @patch("google_play_listing.play_request")
    def test_upload_failure_discards_uncommitted_deletion(self, request):
        self.config(images={"icon": [self.image("icon.png", (512, 512), "RGBA")]})
        [listing] = load_listings(self.root)
        request.side_effect = [{"id": "test-edit"}, {"images": [{"id": "old", "sha256": "a" * 64}]},
                               {}, RuntimeError("upload failed"), {}]
        with self.assertRaisesRegex(RuntimeError, "upload failed"):
            submit_listing(None, listing, "images")
        request.assert_called_with(None, "DELETE", EDIT)
        self.assertFalse(any(call.args[2].endswith(":commit") for call in request.call_args_list))


if __name__ == "__main__":
    unittest.main()
