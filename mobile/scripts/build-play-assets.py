#!/usr/bin/env python3
"""Export Play-ready raster graphics and real Android screenshots.

Usage:
  python3 scripts/build-play-assets.py --output play/output
  python3 scripts/build-play-assets.py --output play/output --screenshots androidApp/build/qa-screenshots

Requires pillow and cairosvg. The sources are deliberately reproducible SVGs,
while screenshots always originate from real emulator UI with dummy notes.
"""
import argparse
import io
from pathlib import Path

import cairosvg
from PIL import Image

BASE = Path(__file__).resolve().parents[1]
SVG = BASE / "play" / "assets"
SAMPLE_IMAGES = ("store-list-light.png", "store-editor-light.png",
                 "store-list-dark.png", "store-editor-dark.png")


def rasterize(name: str) -> Image.Image:
    content = (SVG / name).read_bytes()
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=content))).copy()


def build(output: Path, screenshot_dir: Path | None) -> None:
    output.mkdir(parents=True, exist_ok=True)
    icon = rasterize("stillnote-icon.svg").convert("RGBA")
    assert icon.size == (512, 512), f"Icon size: {icon.size}"
    icon_file = output / "stillnote-play-icon-512.png"
    icon.save(icon_file, optimize=True)
    assert icon_file.stat().st_size < 1_048_576, "Play icon must be at most 1MB"

    graphic = rasterize("stillnote-feature.svg").convert("RGB")
    assert graphic.size == (1024, 500), f"Feature graphic size: {graphic.size}"
    graphic.save(output / "stillnote-feature-1024x500.png", optimize=True)

    if screenshot_dir is not None:
        dest = output / "screenshots"
        dest.mkdir(exist_ok=True)
        for name in SAMPLE_IMAGES:
            source = screenshot_dir / name
            assert source.is_file(), f"Missing real screenshot: {source}"
            with Image.open(source) as original:
                width, height = original.size
                assert 320 <= min(width, height), f"Screenshot too small: {name}"
                assert max(width, height) <= 3840, f"Screenshot too large: {name}"
                canvas = original.convert("RGB")
                # Google Play requires max(long edge)/min(short edge) <= 2.
                # Pad the background if necessary; preserve every app pixel.
                if height > 2 * width:
                    new_width = (height + 1) // 2
                    background = "#241F1A" if "-dark" in name else "#F5EFE3"
                    padded = Image.new("RGB", (new_width, height), background)
                    padded.paste(canvas, ((new_width - width) // 2, 0))
                    canvas = padded
                elif width > 2 * height:
                    new_height = (width + 1) // 2
                    background = "#241F1A" if "-dark" in name else "#F5EFE3"
                    padded = Image.new("RGB", (width, new_height), background)
                    padded.paste(canvas, (0, (new_height - height) // 2))
                    canvas = padded
                assert max(canvas.size) <= 2 * min(canvas.size)
                canvas.save(dest / name, optimize=True)
                print(f"Screenshot {name}: original {width}x{height}, Play {canvas.width}x{canvas.height}")
    print(f"Generated Google Play icon and feature graphic in {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="play/output")
    parser.add_argument("--screenshots", default=None)
    args = parser.parse_args()
    out = Path(args.output)
    src = Path(args.screenshots) if args.screenshots else None
    build(out, src)
