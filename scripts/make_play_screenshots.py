#!/usr/bin/env python3
"""Compose Play phone screenshots (1080x1920, 9:16) from device captures.

The phone is 720x1560 (9:19.5), which is outside Play's 9:16 requirement. Each
capture is scaled to full height and centred on a canvas filled with the app's
own background colour, so nothing is cropped and the aspect ratio is exact.

Usage (from the repository root):

    python scripts/make_play_screenshots.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "screenshots/play"
OUT = ROOT / "fastlane/metadata/android/en-US/images/phoneScreenshots"
TARGET = (1080, 1920)

SHOTS = [
    ("raw-01.png", "01-adjudicate.png"),
    ("raw-12.png", "02-capsules.png"),
    ("raw-05.png", "03-registry.png"),
    ("raw-02.png", "04-agent.png"),
    ("raw-06.png", "05-live-work.png"),
]


def compose(source: Path, destination: Path) -> None:
    image = Image.open(source).convert("RGB")
    scale = TARGET[1] / image.height
    width = round(image.width * scale)
    resized = image.resize((width, TARGET[1]), Image.LANCZOS)
    # The capture's own edge colour keeps the canvas from looking like a frame.
    background = image.getpixel((6, 320))
    canvas = Image.new("RGB", TARGET, background)
    canvas.paste(resized, ((TARGET[0] - width) // 2, 0))
    canvas.save(destination, optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for source, name in SHOTS:
        compose(RAW / source, OUT / name)
        print(f"wrote {OUT/name}")


if __name__ == "__main__":
    main()
