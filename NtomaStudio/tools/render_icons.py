#!/usr/bin/env python3
"""Renders the Ntoma launcher mark to legacy PNG densities (API 24-25) plus a store icon.

The vector layers in res/drawable cover API 26+; these PNGs keep the same geometry so the
icon looks identical on older devices.
"""
from PIL import Image, ImageDraw

SHEET = [(30, 26), (66, 26), (80, 40), (80, 82), (30, 82)]
FOLD = [(66, 26), (80, 40), (66, 40)]
STRIPE_1 = (36, 52, 74, 59)
STRIPE_2 = (36, 64, 62, 71)

INK = (20, 17, 15, 255)
CREAM = (253, 248, 242, 255)
GOLD = (200, 149, 43, 255)
MADDER = (140, 47, 57, 255)
TEAL = (31, 84, 80, 255)


def render(size: int, fill_ratio: float = 0.72, rounded: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if rounded:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.18), fill=INK)
    else:
        d.rectangle([0, 0, size - 1, size - 1], fill=INK)

    # Mark bounding box in vector space: x 30..80, y 26..82
    mark_h = 82 - 26
    scale = (size * fill_ratio) / mark_h
    offset_x = (size - (80 - 30) * scale) / 2 - 30 * scale
    offset_y = (size - mark_h * scale) / 2 - 26 * scale

    def t(point):
        return (point[0] * scale + offset_x, point[1] * scale + offset_y)

    def tbox(box):
        return [box[0] * scale + offset_x, box[1] * scale + offset_y,
                box[2] * scale + offset_x, box[3] * scale + offset_y]

    d.polygon([t(p) for p in SHEET], fill=CREAM)
    d.polygon([t(p) for p in FOLD], fill=GOLD)
    d.rectangle(tbox(STRIPE_1), fill=MADDER)
    d.rectangle(tbox(STRIPE_2), fill=TEAL)
    return img


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

if __name__ == "__main__":
    import os
    import sys

    root = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"
    for density, px in DENSITIES.items():
        folder = os.path.join(root, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        render(px).save(os.path.join(folder, "ic_launcher.png"))
        render(px).save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"wrote {folder}/ic_launcher.png ({px}px)")

    brand_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "brand")
    os.makedirs(brand_dir, exist_ok=True)
    render(512, fill_ratio=0.66, rounded=True).save(os.path.join(brand_dir, "ntoma_icon_512.png"))
    print(f"wrote {brand_dir}/ntoma_icon_512.png")
