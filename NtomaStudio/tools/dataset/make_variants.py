#!/usr/bin/env python3
"""Expand collected fabric photos into lighting/size/angle/sensor VARIATIONS.

This is the tool for "make the dataset bigger by varying the photos". What it produces
is a **training expansion**, never a dataset. Read this next part carefully, because the
distinction decides whether the model works.

What a variant IS
-----------------
One more view of a photo you already have - same cloth, different camera condition.
Legitimate and useful: real capture conditions vary, and teaching the model that
matters. Applying these to *real* photographs is standard practice.

What a variant IS NOT
---------------------
New information. Every variant is derived from a source photo that is already in the
corpus, so a set of 2,000 real photos expanded 10x to 20,000 variants contains the
information of 2,000 photos. It makes the model robust to *condition*; it cannot teach it
a fabric it has never seen. `track_collection.py` therefore counts `raw/` only and
reports `variants/` separately.

The trap this tool is built to avoid
------------------------------------
If you expand first and split later, variants of the SAME source photo land in both
train and validation. Validation accuracy then measures memorisation of one cloth, not
generalisation. That is why:

  * every variant filename preserves its SOURCE STEM and, when known, the capture DATE
  * `train_kaggle.py` splits by session, and the session is carried into the variant name
  * so all variants of one source photo always land in the same split

Never rename variant outputs. The name is the mechanism that prevents leakage.

Usage:
    python3 tools/dataset/make_variants.py                          # plan only, writes nothing
    python3 tools/dataset/make_variants.py --per-image 12 --write
    python3 tools/dataset/make_variants.py --data raw --out variants --per-image 12 --write
    python3 tools/dataset/make_variants.py --catalog-demo --write    # exercise on app assets
"""
import argparse
import json
import math
import random
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

HERE = Path(__file__).resolve().parent
EXTS = (".jpg", ".jpeg", ".png", ".webp")

# App exports carry a capture date; variants must inherit it so the session split holds.
DATE_RE = re.compile(r"(\d{8})(?:_?\d{6})?")


# ------------------------------------------------------------------ lighting

def warm_cool(img, rng):
    """Colour temperature. Market shade is blue, late afternoon is orange."""
    k = rng.uniform(-0.22, 0.22)          # <0 cool, >0 warm
    a = np.asarray(img, dtype=np.float32) / 255.0
    a[..., 0] *= 1.0 + k * 0.9
    a[..., 2] *= 1.0 - k * 0.9
    return Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8))


def exposure(img, rng):
    """A single bounded global gain.

    There is exactly ONE global exposure control in the pipeline. An earlier version
    also had unbounded gamma, shadow and vignette, which compose multiplicatively:
    0.55 x 0.55 x 0.60 x 0.70 removed 87% of the light and produced near-black
    images. Measured mean luminance fell to 15.7 against a 109.4 source. Real capture
    variation is roughly 0.7-1.35x, so that is the range.
    """
    return ImageEnhance.Brightness(img).enhance(rng.uniform(0.70, 1.35))


def contrast(img, rng):
    return ImageEnhance.Contrast(img).enhance(rng.uniform(0.75, 1.30))


def saturation(img, rng):
    return ImageEnhance.Color(img).enhance(rng.uniform(0.72, 1.35))


def gamma(img, rng):
    """Bounded tone curve. Kept narrow because it multiplies the exposure gain."""
    g = rng.uniform(0.88, 1.15)
    lut = (np.linspace(0, 1, 256) ** g * 255).astype(np.uint8)
    return Image.fromarray(lut[np.asarray(img)])


def directional_shadow(img, rng):
    """Light from one side - the common case in a market aisle or under a canopy.

    Amplitude deliberately small: this is a *shape* on the light, not an exposure cut.
    """
    w, h = img.size
    ang = rng.uniform(0, 2 * math.pi)
    strength = rng.uniform(0.05, 0.22)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ramp = (np.cos(ang) * (xx / w) + np.sin(ang) * (yy / h))
    ramp = (ramp - ramp.min()) / (ramp.max() - ramp.min() + 1e-6)
    mask = 1.0 - strength * ramp
    a = np.asarray(img, dtype=np.float32) * mask[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def vignette(img, rng):
    w, h = img.size
    strength = rng.uniform(0.05, 0.18)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.hypot((xx - w / 2) / (w / 2), (yy - h / 2) / (h / 2))
    mask = 1.0 - strength * np.clip(r - 0.4, 0, None) ** 1.5
    a = np.asarray(img, dtype=np.float32) * mask[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def normalize_exposure(out, src, lo=0.60, hi=1.70):
    """Safety floor: keep the fabric legible in every variant.

    Guarantees the variant's mean luminance stays within [lo, hi] of the source's, so
    no combination of transforms can produce an unreadable near-black image. A photo
    nobody can read is not a useful training sample - it teaches the model that this
    class looks black.
    """
    a = np.asarray(out, dtype=np.float32)
    b = np.asarray(src, dtype=np.float32)
    mean_a = float(a.mean())
    mean_b = float(b.mean())
    if mean_b < 1.0 or mean_a < 1.0:
        return out
    rel = mean_a / mean_b
    if lo <= rel <= hi:
        return out
    target = mean_b * (lo if rel < lo else hi)
    gain = target / mean_a
    return Image.fromarray(np.clip(a * gain, 0, 255).astype(np.uint8))


def specular(img, rng):
    """A soft sheen band - satin, silk and wax print all throw one under shop light."""
    if rng.random() > 0.45:
        return img
    w, h = img.size
    cx, cy = rng.uniform(0.15, 0.85) * w, rng.uniform(0.15, 0.85) * h
    rad = rng.uniform(0.25, 0.6) * max(w, h)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.hypot(xx - cx, yy - cy) / rad
    glow = np.clip(1.0 - d, 0, None) ** 2 * rng.uniform(20, 55)
    a = np.asarray(img, dtype=np.float32) + glow[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


# ------------------------------------------------------------------ geometry

def _src_quad_jitter(img, rng, amount=0.035):
    """Build a perspective mapping that can NEVER sample outside the source.

    An earlier version passed 8 arbitrary random integers to Image.PERSPECTIVE. PIL's
    convention treats those as the output->input matrix, so random g and h values blew
    up the denominator (g*x + h*y + 1) and mapped most output pixels outside the input,
    which PIL fills with black. Combined with the other darkening ops it produced
    essentially black images.

    The correct approach is to jitter the SOURCE quad and map it onto the full output
    rect, clamping every corner inside the image. Then every output pixel samples a real
    source pixel and no black band can appear.
    """
    w, h = img.size
    d = max(1, int(amount * min(w, h)))

    def jit(x, y):
        return (min(max(x + rng.randint(-d, d), 0), w - 1),
                min(max(y + rng.randint(-d, d), 0), h - 1))

    # corners of the sampled source region, nudged but always inside the image
    return [jit(0, 0), jit(w, 0), jit(w, h), jit(0, h)]


def _find_coeffs(pa, pb):
    """8 perspective coefficients mapping quad pb -> quad pa (PIL output->input)."""
    matrix = []
    for (x, y), (u, v) in zip(pa, pb):
        matrix.append([u, v, 1, 0, 0, 0, -x * u, -x * v])
        matrix.append([0, 0, 0, u, v, 1, -y * u, -y * v])
    A = np.asarray(matrix, dtype=np.float64)
    B = np.asarray(pa, dtype=np.float64).reshape(8)
    res = np.linalg.solve(A, B)
    return res.tolist()


def edge_fill(img, box=8):
    """Median border colour - a plausible fill so rotation never leaves black corners."""
    a = np.asarray(img.convert("RGB"), dtype=np.uint8)
    b = np.concatenate([a[:box].reshape(-1, 3), a[-box:].reshape(-1, 3),
                        a[:, :box].reshape(-1, 3), a[:, -box:].reshape(-1, 3)])
    med = np.median(b, axis=0).astype(np.uint8)
    return tuple(int(v) for v in med)


def rotate(img, rng):
    return img.rotate(rng.uniform(-12, 12), resample=Image.BICUBIC,
                      expand=False, fillcolor=edge_fill(img))


def zoom_crop(img, rng):
    w, h = img.size
    s = rng.uniform(0.80, 1.0)
    cw, ch = max(16, int(w * s)), max(16, int(h * s))
    x = rng.randint(0, max(0, w - cw))
    y = rng.randint(0, max(0, h - ch))
    return img.crop((x, y, x + cw, y + ch))


def perspective(img, rng):
    """Mild keystone - the phone was not perfectly perpendicular to the cloth."""
    w, h = img.size
    src_quad = _src_quad_jitter(img, rng)
    dst_quad = [(0, 0), (w - 1, 0), (w - 1, h - 1), (0, h - 1)]
    try:
        coeffs = _find_coeffs(dst_quad, src_quad)
    except np.linalg.LinAlgError:
        return img
    return img.transform((w, h), Image.PERSPECTIVE, coeffs, Image.BICUBIC,
                         fillcolor=edge_fill(img))


# ------------------------------------------------------------------ sensor

def sensor_noise(img, rng):
    a = np.asarray(img, dtype=np.float32)
    sigma = rng.uniform(2.0, 14.0)
    a = a + np.random.normal(0, sigma, a.shape)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def motion_blur(img, rng):
    if rng.random() > 0.25:
        return img
    k = rng.choice([3, 5])
    return img.filter(ImageFilter.GaussianBlur(rng.uniform(0.4, 1.2)))


def resize_to(img, size, rng, jpeg_quality):
    """Output size varies on purpose: the app must work on cheap phones and flagships."""
    w, h = img.size
    scale = size / max(w, h)
    return img.resize((max(16, int(w * scale)), max(16, int(h * scale))), Image.LANCZOS)


# ------------------------------------------------------------------ pipeline

def apply_condition(img, rng, size):
    """One random capture condition. Order roughly follows a camera pipeline."""
    src = img
    ops = [zoom_crop, perspective, rotate]
    rng.shuffle(ops)
    for op in ops:
        img = op(img, rng)
    for op in (warm_cool, exposure, contrast, gamma, directional_shadow, vignette,
               specular, saturation):
        img = op(img, rng)
    img = motion_blur(img, rng)
    img = sensor_noise(img, rng)
    img = normalize_exposure(img, src)      # never return an unreadable photo
    return resize_to(img, size, rng, rng.randint(72, 96))


def variant_name(cls, src_stem, index):
    """Preserve the capture date so the session split in train_kaggle.py still holds."""
    m = DATE_RE.search(src_stem)
    date = m.group(1) if m else "nodate"
    safe = re.sub(r"[^A-Za-z0-9]+", "", src_stem)[:40]
    return f"{cls}_{date}_{safe}_v{index:03d}.jpg"


def process_dir(src_root, out_root, per_image, sizes, seed, write, classes_filter=None):
    src_root, out_root = Path(src_root), Path(out_root)
    if not src_root.is_dir():
        return None
    rng = random.Random(seed)
    np.random.seed(seed)
    plan, written, skipped = {}, 0, 0

    for cls_dir in sorted(p for p in src_root.iterdir() if p.is_dir()):
        if cls_dir.name.startswith("_"):
            continue
        if classes_filter and cls_dir.name not in classes_filter:
            continue
        srcs = sorted(p for p in cls_dir.rglob("*") if p.suffix.lower() in EXTS)
        if not srcs:
            continue
        plan[cls_dir.name] = len(srcs)
        for src in srcs:
            try:
                base = Image.open(src).convert("RGB")
            except Exception:
                skipped += 1
                continue
            if max(base.size) < 120:
                skipped += 1
                continue
            for i in range(per_image):
                size = sizes[i % len(sizes)]
                out = apply_condition(base, rng, size)
                if not write:
                    written += 1
                    continue
                d = out_root / cls_dir.name
                d.mkdir(parents=True, exist_ok=True)
                p = d / variant_name(cls_dir.name, src.stem, i)
                out.save(p, "JPEG", quality=rng.randint(72, 96))
                (out_root / "manifest.jsonl").open("a").write(json.dumps({
                    "variant": str(p.relative_to(out_root)),
                    "source": str(src),
                    "category": cls_dir.name,
                    "transform_seed": seed,
                }) + "\n")
                written += 1
    return {"sources": plan, "variants": written, "skipped": skipped,
            "total_sources": sum(plan.values())}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=str(HERE / "raw"))
    ap.add_argument("--out", default=str(HERE / "variants"))
    ap.add_argument("--per-image", type=int, default=12,
                    help="variants per source photo (default 12)")
    ap.add_argument("--sizes", default="224,320,512",
                    help="output long-edge sizes to cycle through")
    ap.add_argument("--seed", type=int, default=20260924)
    ap.add_argument("--write", action="store_true", help="actually write (default: plan only)")
    ap.add_argument("--catalog-demo", action="store_true",
                    help="run against the app's 15 catalog swatches to exercise the pipeline")
    args = ap.parse_args()

    sizes = [int(s) for s in args.sizes.split(",") if s.strip()]

    if args.catalog_demo:
        catalog = HERE.parent.parent / "app/src/main/assets/catalog/fabrics"
        if not catalog.is_dir():
            print(f"ERROR: catalog not found at {catalog}", file=sys.stderr)
            return 2
        # stage the swatches as if they were raw/<CLASS>/ so the pipeline is exercised
        tmp = Path("/tmp/_ntoma_catalog_demo/raw")
        if tmp.exists():
            import shutil
            shutil.rmtree(tmp)
        for f in sorted(catalog.glob("*.jpg")):
            d = tmp / f.stem.upper()
            d.mkdir(parents=True, exist_ok=True)
            import shutil
            shutil.copy(f, d / f"DEMO_{f.stem}_20260101_120000.jpg")
        args.data, args.out = str(tmp), "/tmp/_ntoma_catalog_demo/variants"
        print("CATALOG DEMO MODE")
        print("  Source: the 15 app catalog swatches (unlicensed, 1 per class).")
        print("  Purpose: prove the generator runs. These are NOT training data.")
        print("  Output goes to /tmp and is never counted toward the dataset.\n")

    src = Path(args.data)
    if not src.is_dir():
        print(f"ERROR: source root not found: {src}", file=sys.stderr)
        print("Collect or ingest photos into raw/<CLASS>/ first, or pass --data <path>.\n"
              "To exercise the generator without any data, use --catalog-demo.", file=sys.stderr)
        return 2

    print("=" * 78)
    print("Variant generator — lighting / size / angle / sensor")
    print("=" * 78)
    print(f"source: {src}")
    print(f"output: {args.out}")
    print(f"per source: {args.per_image}   sizes: {sizes}   seed: {args.seed}\n")

    res = process_dir(src, Path(args.out), args.per_image, sizes, args.seed, args.write)
    if not res or not res["sources"]:
        print("ERROR: no class folders with images found.", file=sys.stderr)
        return 2

    print(f"{'class':22s} {'source photos':>14s} {'variants':>10s}")
    for c, n in sorted(res["sources"].items()):
        print(f"{c:22s} {n:>14d} {n * args.per_image:>10d}")
    print(f"{'-'*48}")
    print(f"{'TOTAL':22s} {res['total_sources']:>14d} {res['variants']:>10d}")
    if res["skipped"]:
        print(f"  ({res['skipped']} source file(s) skipped: unreadable or under 120px)")

    print("\n-- what this does and does not change --")
    print(f"  REAL photos gained:     0   (nothing new was photographed)")
    print(f"  variants produced:      {res['variants']:,}")
    print(f"  information content:    still {res['total_sources']:,} distinct cloths")
    print("  variants make the model robust to CONDITION, not to new fabrics.")

    if args.write:
        print(f"\nwrote {res['variants']:,} variants to {args.out}")
        print(f"wrote {Path(args.out)/'manifest.jsonl'} (provenance: variant -> source)")
        print("\nIMPORTANT: variant filenames carry the source capture date on purpose.")
        print("train_kaggle.py splits by session; the date in the name keeps every")
        print("variant of one source photo inside the same split. Do not rename them.")
        readme = Path(args.out) / "README_DO_NOT_COUNT.txt"
        readme.write_text(
            "These are AUGMENTED VARIANTS, not photographs.\n"
            f"{res['variants']:,} files derived from {res['total_sources']:,} real sources.\n\n"
            "They must NEVER be counted toward the dataset target. Adding them to the\n"
            "count inflates the dataset with information already in it, and a target met\n"
            "this way is a target not met. track_collection.py counts raw/ only for this\n"
            "reason and reports this folder separately.\n\n"
            "Use them as a training expansion, applied AFTER the session split.\n")
        print(f"wrote {readme}")
    else:
        print(f"\nplan only — pass --write to produce {res['variants']:,} variants.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
