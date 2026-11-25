"""Reconcile one App Asset Library screenshot group without deleting library images."""

import subprocess
import time


def placements(asc, locale_id, group):
    selector = ("--placement-group", group) if group else ()
    return asc("localizations", "placements", "list", "--localization-id", locale_id,
               "--placement-type", "APP_SCREENSHOT", *selector, "--include", "image",
               "--sort", "placementGroupPosition", "--paginate")["data"]


def group_policy(asc, group):
    data = asc("asset-library", "specs", "--feature", "APP_STORE_VERSIONS",
               "--placement-type", "APP_SCREENSHOT", "--placement-profile-group", group)["data"]
    for row in data:
        attrs = row["attributes"]
        specs = {spec for kind in attrs["placementTypes"] if kind["placementTypeId"] == "APP_SCREENSHOT"
                 for mapping in kind["specMappings"] if mapping["placementGroupId"] == group
                 for spec in mapping["specs"]}
        for feature in attrs["features"]:
            if feature["featureId"] == "APP_STORE_VERSIONS":
                for policy in feature["placementPolicies"]:
                    if policy["placementType"] == "APP_SCREENSHOT":
                        for limit in policy["groupLimits"]:
                            if group in limit["groupIds"] and specs:
                                return limit["maxCount"], specs
    raise ValueError(f"Apple returned no App Store screenshot policy for {group}")


def resolve_image(asc, library, key, path, specs):
    selector = ("--id", key) if path is None else ("--reference-name", key)
    rows = asc("asset-library", "images", "list", "--library-id", library,
               "--category", "APP_SCREENSHOTS_AND_PREVIEWS", *selector, "--paginate")["data"]
    for row in rows:
        attrs = row["attributes"]
        if attrs.get("specId") in specs and attrs.get("state") not in {"FAILED", "ARCHIVED"}:
            if path is None or (attrs.get("referenceName") == key and attrs.get("fileSize") == path.stat().st_size):
                return row["id"]
    if path is None:
        raise ValueError(f"Image {key} is unavailable or incompatible with this device group")
    if any(r["attributes"].get("state") == "UPLOAD_COMPLETE" for r in rows):
        raise ValueError(f"Apple is still processing {path.name}. Retry after processing.")
    result = asc("asset-library", "images", "upload", "--library-id", library, "--file", str(path),
                 "--category", "APP_SCREENSHOTS_AND_PREVIEWS", "--reference-name", key)
    if not result.get("ready") or result.get("specId") not in specs:
        raise ValueError(f"{path.name} did not match the device group's screenshot specifications")
    return result["imageId"]


def read_order(asc, locale_id, group):
    rows = placements(asc, locale_id, group)
    pairs = [(r["relationships"]["image"]["data"]["id"], r["id"]) for r in rows]
    if len(dict(pairs)) != len(pairs):
        raise ValueError("The group contains duplicate placements of one image. Resolve them in App Store Connect.")
    return dict(pairs)  # Image ID -> placement ID, in Apple's display order.


def reconcile(asc, locale_id, group, current, wanted, capacity):
    if len(wanted) > capacity:
        raise ValueError(f"{group} permits {capacity} screenshots; requested {len(wanted)}")
    if list(current) == wanted:
        return
    placement = ("localizations", "placements")
    target = ("--localization-id", locale_id, "--placement-group", group)

    def add(image):
        return asc(*placement, "create", *target, "--placement-type", "APP_SCREENSHOT",
                   "--image-id", image)["id"]

    def remove(image):
        asc(*placement, "delete", "--id", current[image], "--confirm")
        del current[image]

    def order(mapping, sequence):
        if len(sequence) > 1:
            asc(*placement, "reorder", *target, "--placement-ids",
                ",".join(mapping[image] for image in sequence))

    for image in wanted:
        if image in current:
            continue
        old = None
        if len(current) >= capacity:
            # A full group needs one free slot. Preserve its prior order for recovery.
            previous = list(current)
            old = next(key for key in reversed(previous) if key not in wanted)
            remove(old)
        try:
            current[image] = add(image)
        except subprocess.CalledProcessError:
            observed = read_order(asc, locale_id, group)
            if image in observed:
                current = observed
                continue
            if old and set(observed) == set(previous) - {old}:
                observed[old] = add(old)
                order(observed, previous)
            raise
    for obsolete in list(current):
        if obsolete not in wanted:
            remove(obsolete)
    if list(read_order(asc, locale_id, group)) != wanted:
        order(current, wanted)
    for attempt in range(5):
        if list(read_order(asc, locale_id, group)) == wanted:
            return
        if attempt < 4:
            time.sleep(2)
    raise ValueError("Apple has not confirmed the requested screenshot order. Inspect the group and rerun.")


def sync_screenshots(asc, app, locale_id, group, assets, replace):
    limit, specs = group_policy(asc, group)
    if len(assets) > limit:
        raise ValueError(f"{group} permits at most {limit} screenshots")
    library = asc("asset-library", "view", "--app", app)["data"]["id"]
    original = read_order(asc, locale_id, group)
    # Stage all media and verify its processed spec before touching any visible placements.
    images = [resolve_image(asc, library, key, path, specs) for key, path in assets]
    if len(set(images)) != len(images):
        raise ValueError("The configured files and IDs resolve to duplicate images")
    current = read_order(asc, locale_id, group)
    if list(current.items()) != list(original.items()):
        raise ValueError("The device group changed while uploading. Rerun with the current group.")
    wanted = images if replace else [*current, *(image for image in images if image not in current)]
    reconcile(asc, locale_id, group, current, wanted, limit)
    print(f"{group}: confirmed {len(wanted)} screenshots in display order.")
