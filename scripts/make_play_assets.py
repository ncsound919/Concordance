#!/usr/bin/env python3
"""Generate the Google Play icon and feature graphic from the app's own logo.

Run from the repository root:

    python scripts/make_play_assets.py

Outputs (fastlane layout):
    fastlane/metadata/android/en-US/images/icon.png          512x512
    fastlane/metadata/android/en-US/images/featureGraphic.png 1024x500

The logo is the 1024x1024 asset already shipped in the app; nothing here invents
new branding. Phone screenshots are captured separately from a device.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LOGO = ROOT / "app/src/main/res/drawable-nodpi/ic_concordance_logo.jpg"
OUT = ROOT / "fastlane/metadata/android/en-US/images"

CYAN = (0, 229, 255)
TEXT = (241, 245, 249)
MUTED = (148, 163, 184)


def font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("seguisb.ttf", "segoeuib.ttf", "arialbd.ttf"):
        path = Path("C:/Windows/Fonts") / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def make_icon(logo: Image.Image) -> None:
    icon = logo.convert("RGB").resize((512, 512), Image.LANCZOS)
    icon.save(OUT / "icon.png", optimize=True)


def make_feature(logo: Image.Image) -> None:
    bg = logo.getpixel((4, 4))
    canvas = Image.new("RGB", (1024, 500), bg)
    draw = ImageDraw.Draw(canvas)

    mark = logo.resize((420, 420), Image.LANCZOS)
    canvas.paste(mark, (28, 40))

    draw.text((500, 132), "CONCORDANCE", font=font(66), fill=TEXT)
    draw.text(
        (504, 222),
        "Detection-corrected adjudication",
        font=font(30),
        fill=CYAN,
    )
    draw.text(
        (504, 268),
        "for organotropism claims.",
        font=font(30),
        fill=CYAN,
    )
    draw.text(
        (504, 340),
        "Measured, derived, configured — or withheld.",
        font=font(22),
        fill=MUTED,
    )
    canvas.save(OUT / "featureGraphic.png", optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    logo = Image.open(LOGO)
    make_icon(logo)
    make_feature(logo)
    print(f"wrote {OUT/'icon.png'} 512x512")
    print(f"wrote {OUT/'featureGraphic.png'} 1024x500")


if __name__ == "__main__":
    main()
