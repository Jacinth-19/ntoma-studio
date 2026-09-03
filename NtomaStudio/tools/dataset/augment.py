#!/usr/bin/env python3
"""Expand raw fabric photos into an augmented training set.

Reads raw/<CLASS>/*.jpg (see label_schema.json) and writes
augmented/<CLASS>/<n>_<variant>.jpg with photorealistic, label-preserving
transforms: random crop/scale, rotation, brightness/contrast/saturation
jitter, mild perspective, JPEG re-compression.

Pure Pillow — no deep-learning dependency, runs anywhere.

Usage: python3 tools/dataset/augment.py [--per-image 10] [--size 224]
"""
import argparse
import random
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT = ROOT / "augmented"


def jitter(img: Image.Image, rng: random.Random) -> Image.Image:
    for cls, lo, hi in ((ImageEnhance.Brightness, 0.7, 1.3),
                        (ImageEnhance.Contrast, 0.75, 1.25),
                        (ImageEnhance.Color, 0.7, 1.3)):
        img = cls(img).enhance(rng.uniform(lo, hi))
    return img


def perspective(img: Image.Image, rng: random.Random) -> Image.Image:
    w, h = img.size
    d = int(0.06 * min(w, h))
    coeffs = [rng.randint(-d, d) for _ in range(8)]
    # find_coeffs-style approximation is overkill; small affine via quad warp
    try:
        return img.transform(img.size, Image.QUAD,
                             data=_quad_coeffs(w, h, coeffs), resample=Image.BILINEAR)
    except Exception:
        return img


def _quad_coeffs(w, h, c):
    # map unit-ish quad corners with small jitter; PIL QUAD wants 8 source coords
    (x0, y0, x1, y1, x2, y2, x3, y3) = c
    return (x0, y0, w + x1, y1, w + x2, h + y2, x3, h + y3)


def variant(img: Image.Image, size: int, rng: random.Random) -> Image.Image:
    w, h = img.size
    side = int(min(w, h) * rng.uniform(0.55, 1.0))
    x = rng.randint(0, max(0, w - side))
    y = rng.randint(0, max(0, h - side))
    img = img.crop((x, y, x + side, y + side))
    img = img.rotate(rng.uniform(-20, 20), expand=False, fillcolor=(128, 128, 128))
    img = perspective(img, rng)
    img = img.resize((size, size), Image.BILINEAR)
    img = jitter(img, rng)
    if rng.random() < 0.5:
        img = ImageOps.mirror(img)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image", type=int, default=10)
    ap.add_argument("--size", type=int, default=224)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    rng = random.Random(args.seed)

    if not RAW.exists():
        raise SystemExit(f"no raw/ dir at {RAW} — see label_schema.json for layout")
    total = 0
    OUT.mkdir(exist_ok=True)
    for cls_dir in sorted(RAW.iterdir()):
        if not cls_dir.is_dir():
            continue
        out_dir = OUT / cls_dir.name
        out_dir.mkdir(exist_ok=True)
        for i, src in enumerate(sorted(cls_dir.glob("*.jpg")) + sorted(cls_dir.glob("*.png"))):
            base = Image.open(src).convert("RGB")
            for v in range(args.per_image):
                out = out_dir / f"{i:03d}_{v:02d}.jpg"
                variant(base, args.size, rng).save(out, "JPEG", quality=88)
                total += 1
    print(f"wrote {total} augmented images to {OUT}")


if __name__ == "__main__":
    main()
