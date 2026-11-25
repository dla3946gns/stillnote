import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import app_store as store
import app_store_screenshots as shots


class PlacementAPI:
    def __init__(self, images):
        self.rows = {image: "p-" + image for image in images}
        self.writes = []
        self.fail_image = None

    def __call__(self, *args):
        operation = args[2]
        if operation == "list":
            return {"data": [{"id": pid, "relationships": {"image": {"data": {"id": img}}}}
                             for img, pid in self.rows.items()]}
        self.writes.append(operation)
        if operation == "create":
            image = args[args.index("--image-id") + 1]
            if image == self.fail_image:
                raise subprocess.CalledProcessError(1, ["asc"])
            self.rows[image] = "p-" + image
            return {"id": self.rows[image]}
        if operation == "delete":
            pid = args[args.index("--id") + 1]
            del self.rows[next(img for img, value in self.rows.items() if value == pid)]
        elif operation == "reorder":
            ids = args[args.index("--placement-ids") + 1].split(",")
            self.rows = dict(sorted(self.rows.items(), key=lambda item: ids.index(item[1])))
        else:
            raise AssertionError(args)


class ScreenshotTests(unittest.TestCase):
    def test_replace_at_capacity_preserves_retained_placements_and_is_repeatable(self):
        api = PlacementAPI(["a", "b", "c"])
        for _ in range(2):
            shots.reconcile(api, "loc", "group", dict(api.rows), ["c", "d", "a"], 3)
        self.assertEqual(list(api.rows), ["c", "d", "a"])
        self.assertEqual(api.writes, ["delete", "create", "reorder"])

    def test_full_group_recovers_prior_order_if_new_placement_fails(self):
        api = PlacementAPI(["a", "b"])
        api.fail_image = "new"
        with self.assertRaises(subprocess.CalledProcessError):
            shots.reconcile(api, "loc", "group", dict(api.rows), ["new", "b"], 2)
        self.assertEqual(list(api.rows), ["a", "b"])

    def test_upload_or_spec_failure_preserves_current_placements(self):
        api = Mock(side_effect=[{"data": {"id": "library"}}, {"data": []},
                                {"data": []}, {"ready": True, "specId": "wrong-spec"}])
        with patch.object(shots, "group_policy", return_value=(10, {"ipad"})):
            with self.assertRaisesRegex(ValueError, "specifications"):
                shots.sync_screenshots(api, "app", "loc", "group", [("hash", Path("new.png"))], True)
        self.assertFalse(any(c.args[:3] == ("localizations", "placements", "delete") for c in api.call_args_list))

    def test_append_keeps_existing_order_and_skips_existing_image(self):
        api = PlacementAPI(["old", "managed"])
        def call(*args):
            if args[0] == "asset-library":
                return {"data": {"id": "lib"}}
            return api(*args)
        with patch.object(shots, "group_policy", return_value=(4, {"spec"})), \
             patch.object(shots, "resolve_image", side_effect=lambda _, __, key, ___, ____: key):
            for _ in range(2):
                shots.sync_screenshots(call, "app", "loc", "group", [("managed", None), ("new", None)], False)
        self.assertEqual(list(api.rows), ["old", "managed", "new"])
        self.assertEqual(api.writes, ["create"])

    def test_over_limit_does_not_remove_images(self):
        api = PlacementAPI(["a", "b"])
        with self.assertRaisesRegex(ValueError, "permits 2"):
            shots.reconcile(api, "loc", "group", dict(api.rows), ["a", "b", "c"], 2)
        self.assertFalse(api.writes)

    def test_group_spec_from_apple_reference_data(self):
        row = {"placementTypes": [{"placementTypeId": "APP_SCREENSHOT", "specMappings": [
            {"placementGroupId": "IPAD", "specs": ["ipad-13"]}]}],
            "features": [{"featureId": "APP_STORE_VERSIONS", "placementPolicies": [
                {"placementType": "APP_SCREENSHOT", "groupLimits": [{"groupIds": ["IPAD"], "maxCount": 10}]}]}]}
        self.assertEqual(shots.group_policy(Mock(return_value={"data": [{"attributes": row}]}), "IPAD"),
                         (10, {"ipad-13"}))


class StoreTests(unittest.TestCase):
    def test_korean_keywords_use_apples_byte_limit(self):
        store.fields({"keywords": "가" * 33}, store.VERSION_FIELDS)
        with self.assertRaisesRegex(ValueError, "100-byte"):
            store.fields({"keywords": "가" * 34}, store.VERSION_FIELDS)

    def test_manifest_disallows_path_escape_and_empty_replacement(self):
        with TemporaryDirectory() as directory:
            path = Path(directory, "listing.json")
            for entries in [["../elsewhere.png"], []]:
                path.write_text(json.dumps({"screenshots": {"IPHONE": entries}}))
                with self.assertRaises(ValueError):
                    store.read_config(path)

    def test_patch_updates_only_changed_fields_including_multiline_release_notes(self):
        target = {"id": "v-ko", "type": "appStoreVersionLocalizations",
                  "attributes": {"description": "유지", "whatsNew": "기존"}}
        values = {"description": "유지", "whatsNew": "저장 개선\n검색 개선"}
        with patch.object(store, "asc", return_value={"data": {"attributes": values}}) as api:
            store.patch_metadata(target, values)
        self.assertEqual(api.call_args.kwargs["body"]["data"]["attributes"], {"whatsNew": values["whatsNew"]})

    def test_waiting_for_review_rejects_screenshot_update(self):
        response = {"data": {"id": "v1", "attributes": {"appVersionState": "WAITING_FOR_REVIEW",
                                                        "platform": "IOS", "versionString": "1.0"}}}
        with patch.object(store, "version_info", return_value=response), \
             patch.object(store, "sync_screenshots") as sync:
            with self.assertRaisesRegex(ValueError, "WAITING_FOR_REVIEW"):
                store.run("append-screenshots", "1.0", "ko", "IPHONE", "")
            sync.assert_not_called()

    def test_missing_review_version_is_created_with_manual_release(self):
        record = {"data": {"id": "new"}}
        with patch.object(store, "asc", side_effect=[{"data": []}, record]) as api, \
             patch.object(store, "version_info", return_value=record):
            self.assertEqual(store.ensure_review_version("1.0.1"), record)
        self.assertIn("filter%5BversionString%5D=1.0.1", api.call_args_list[0].args[2])
        self.assertEqual(api.call_args.args[:3], ("api", "POST", "/v1/appStoreVersions"))
        self.assertEqual(api.call_args.kwargs["body"]["data"]["attributes"]["releaseType"], "MANUAL")

    def test_existing_review_version_is_not_created_again(self):
        row = {"id": "v1", "attributes": {"platform": "IOS", "versionString": "1.0.1"}}
        with patch.object(store, "asc", return_value={"data": [row]}) as api, \
             patch.object(store, "version_info", return_value={"data": row}):
            store.ensure_review_version("1.0.1")
        api.assert_called_once()

    def test_version_lookup_error_does_not_create_version(self):
        with patch.object(store, "asc", side_effect=subprocess.CalledProcessError(1, ["asc"])) as api:
            with self.assertRaises(subprocess.CalledProcessError):
                store.ensure_review_version("1.0.1")
        api.assert_called_once()

    def test_invalid_build_number_does_not_create_version(self):
        with patch.object(store, "ensure_review_version") as ensure:
            with self.assertRaisesRegex(ValueError, "exact TestFlight"):
                store.run("submit-review", "1.0.1", "ko", "", "")
        ensure.assert_not_called()

    def test_review_notes_only_patch_whats_new_on_editable_version(self):
        with TemporaryDirectory() as directory:
            path = Path(directory, "1.0.1", "ko", "listing.json")
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"versionInfo": {"description": "keep", "whatsNew": "new icon"}}))
            with patch.object(store, "ROOT", Path(directory)), \
                 patch.object(store, "localization", return_value={"id": "ko"}), \
                 patch.object(store, "patch_metadata") as update:
                store.prepare_review_notes("1.0.1", "ko", {"id": "v1"}, "PREPARE_FOR_SUBMISSION")
                store.prepare_review_notes("1.0.1", "ko", {"id": "v1"}, "WAITING_FOR_REVIEW")
            update.assert_called_once_with({"id": "ko"}, {"whatsNew": "new icon"})

    def test_review_uses_same_version_and_build_after_processing(self):
        preview = {"buildId": "build-4", "reviewItems": [{"type": "appStoreVersions", "id": "v1"}]}
        attached = {"data": {"relationships": {"build": {"data": {"id": "build-4"}}}}}
        with patch.object(store, "asc", side_effect=[{}, preview, {"submissionId": "s", "buildId": "build-4"}]) as api, \
             patch.object(store, "version_info", return_value=attached):
            store.submit_review("1.0", "4.1", "v1")
        wait, dry, submit = [c.args for c in api.call_args_list]
        self.assertIn("--fail-on-invalid", wait)
        self.assertEqual(wait[wait.index("--version") + 1], "1.0")
        for args in (wait, dry, submit):
            self.assertEqual(args[args.index("--build-number") + 1], "4.1")
        self.assertEqual(dry[:-1], submit[:-1])
        self.assertEqual((dry[-1], submit[-1]), ("--dry-run", "--confirm"))

    def test_review_stops_if_build_fails_processing(self):
        with patch.object(store, "asc", side_effect=subprocess.CalledProcessError(1, ["asc"])) as api:
            with self.assertRaises(subprocess.CalledProcessError):
                store.submit_review("1.0", "4.1", "v1")
        self.assertEqual(api.call_count, 1)

    def test_review_stops_for_unrequested_companion(self):
        items = [{"type": "appStoreVersions", "id": "v1"}, {"type": "subscriptions", "id": "extra"}]
        with patch.object(store, "asc", side_effect=[{}, {"reviewItems": items}]) as api:
            with self.assertRaisesRegex(ValueError, "other items"):
                store.submit_review("1.0", "4.1", "v1")
        self.assertEqual(api.call_count, 2)

    def test_already_submitted_different_build_is_not_reported_as_success(self):
        result = {"alreadySubmitted": True, "submissionId": "s", "buildId": "requested"}
        attached = {"data": {"relationships": {"build": {"data": {"id": "other-build"}}}}}
        with patch.object(store, "asc", side_effect=[{}, result]) as api, \
             patch.object(store, "version_info", return_value=attached), patch.object(store.time, "sleep"):
            with self.assertRaisesRegex(ValueError, "differs"):
                store.submit_review("1.0", "4.1", "v1")
        self.assertEqual(api.call_count, 2)


if __name__ == "__main__":
    unittest.main()
