#!/usr/bin/env python3
"""Generate AJ Notes icons from the owner-approved original PNG.

Run from any directory: python3 mobile/scripts/generate-aj-notes-icons.py
Requires Pillow 11.3.0. No store API calls.
"""
import hashlib
import json
from pathlib import Path

from PIL import Image

MOBILE = Path(__file__).resolve().parents[1]
ASSETS = MOBILE / "play" / "assets"
SOURCE = ASSETS / "aj-notes-mascot.png"
SOURCE_SHA256 = "826bdf53430580a9b2d4bfd0efb7fbd8ede1908bac04c522d89b8fd96876955f"
BACKGROUND = (255, 249, 239, 255)
RES = MOBILE / "androidApp/src/main/res"


def canvas(subject, size, height, background):
    width = round(subject.width * height / subject.height)
    resized = subject.resize((width, height), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (size, size), background)
    result.alpha_composite(resized, ((size - width) // 2, (size - height) // 2))
    return result


def save(image, path, mode="RGBA"):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.convert(mode).save(path, format="PNG", optimize=True)
    with Image.open(path) as saved:
        assert saved.size == image.size and saved.mode == mode
        saved.verify()


def replace(path, before, after):
    text = path.read_text(encoding="utf-8")
    if before in text:
        path.write_text(text.replace(before, after, 1), encoding="utf-8")
    elif after not in text:
        raise RuntimeError(f"Unexpected content in {path}")


def main():
    if hashlib.sha256(SOURCE.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise RuntimeError("Original mascot SHA-256 differs from the approved upload")
    with Image.open(SOURCE) as source:
        assert source.format == "PNG" and source.mode == "RGBA" and source.size == (512, 512)
        bounds = source.getchannel("A").getbbox()
        assert bounds
        subject = source.crop(bounds)
    manifest = MOBILE / "androidApp/src/main/AndroidManifest.xml"
    listing_path = MOBILE / "play/listing-ko.json"
    exporter = MOBILE / "scripts/build-play-assets.py"
    ios = MOBILE / "iosApp/Stillnote/Assets.xcassets/AppIcon.appiconset"
    config = json.loads((ios / "Contents.json").read_text(encoding="utf-8"))
    assert any(x.get("filename") == "AppIcon.png" and x.get("size") == "1024x1024"
               for x in config["images"])
    listing = json.loads(listing_path.read_text(encoding="utf-8"))
    assert listing["appId"] == "com.stillnotes.app"
    play_icon = ASSETS / "aj-notes-play-icon-512.png"
    save(canvas(subject, 512, round(512 * .8), BACKGROUND), play_icon)
    assert play_icon.stat().st_size <= 1048576
    save(canvas(subject, 1024, round(1024 * .8), BACKGROUND), ios / "AppIcon.png", "RGB")

    for density, scale in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2),
                           ("xxhdpi", 3), ("xxxhdpi", 4)):
        directory = RES / f"mipmap-{density}"
        size = round(48 * scale)
        save(canvas(subject, size, round(size * .8), BACKGROUND),
             directory / "ic_aj_notes.png")
        save(canvas(subject, round(108 * scale), round(60 * scale), (0, 0, 0, 0)),
             directory / "ic_aj_notes_foreground.png")

    adaptive = RES / "mipmap-anydpi-v26/ic_aj_notes.xml"
    adaptive.parent.mkdir(parents=True, exist_ok=True)
    adaptive.write_text("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/aj_notes_icon_background" />
    <foreground android:drawable="@mipmap/ic_aj_notes_foreground" />
</adaptive-icon>
""", encoding="utf-8")
    (RES / "values/aj_notes_icon.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="aj_notes_icon_background">#FFF9EF</color>
</resources>
""", encoding="utf-8")

    replace(manifest, 'android:icon="@drawable/ic_stillnote"', 'android:icon="@mipmap/ic_aj_notes"')
    listing.setdefault("images", {})["icon"] = ["assets/aj-notes-play-icon-512.png"]
    listing_path.write_text(json.dumps(listing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    replace(exporter, 'icon = rasterize("stillnote-icon.svg").convert("RGBA")',
            'with Image.open(SVG / "aj-notes-play-icon-512.png") as source_icon:\n        icon = source_icon.convert("RGBA")')
    print("Verified approved source and generated Android, iOS and Play icons.")


if __name__ == "__main__":
    main()
