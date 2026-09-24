#!/usr/bin/env python3
"""Catch synthetic or under-diverse data before it poisons the training set.

Why this exists: AI-generated fabric images pass visual inspection and, measured here,
they also defeat texture heuristics - a synthetic kente set scored *higher* on the app's
own GLCM noise metric (9.5) than real photographs (2.9). Simple detectors do not work.

What does work is **diversity**. Measured on the same class:

    metric                                 generated      real photos
    mean feature cosine similarity            0.9363          0.7041
    feature-similarity std (diversity)        0.0152          0.0700

Generated images from one prompt are far more mutually similar than real photographs of
the same fabric. That collapses the effective sample size: 50,000 near-identical images
teach a model about as much as a few hundred. And because the same bias sits in both the
train and validation splits, **the validation set cannot detect the problem** - reported
accuracy stays high while the shipped model fails on real photos.

But the same signal has an innocent explanation: a class photographed on one market day
also looks homogeneous. So this tool reports, it does not accuse, and it says which
cause is more likely from the evidence it can see.

Usage:
    python3 tools/dataset/audit_diversity.py                       # audit raw/
    python3 tools/dataset/audit_diversity.py --data /path/to/raw
    python3 tools/dataset/audit_diversity.py --json
Exit: 0 clean, 1 nothing to audit, 2 suspicious classes found.
"""
import argparse
import glob
import itertools
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

TOOLS = Path(__file__).resolve().parent
DEFAULT_RAW = TOOLS / "raw"
EXTS = (".jpg", ".jpeg", ".png", ".webp")

# Thresholds. Calibrated against one real (n=5) and one generated (n=4) kente set, so
# they are indicative rather than tuned - the report always shows the raw numbers.
SUSPECT_SIM = 0.90        # mean pairwise cosine similarity above this is very homogeneous
SUSPECT_DIV_STD = 0.03    # feature-similarity std below this is very homogeneous
MIN_IMAGES = 8            # below this the statistic is too noisy to say anything


def features(path, size=128):
    im = Image.open(path).convert("RGB").resize((size, size), Image.LANCZOS)
    a = np.asarray(im, dtype=np.float32) / 255.0
    hist = np.concatenate([np.histogram(a[:, :, c], bins=16, range=(0, 1), density=True)[0]
                           for c in range(3)])
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    small = np.asarray(Image.fromarray((lum * 255).astype(np.uint8)).resize((32, 32)),
                       dtype=np.float32).ravel() / 255.0
    return np.concatenate([hist / (np.linalg.norm(hist) + 1e-9),
                           small / (np.linalg.norm(small) + 1e-9)])


def cosine(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-9))


def generator_signature(path):
    """Cheap artefacts that diffusion/upscale pipelines leave behind.

    None of these is proof on its own - a WhatsApp-forwarded photo also lacks EXIF.
    They are reported as corroboration, never as the finding.
    """
    sig = {}
    try:
        with Image.open(path) as im:
            sig["size"] = im.size
            sig["aspect"] = round(im.size[0] / im.size[1], 4)
            sig["exif"] = bool(im.getexif())
            q = im.quantization
            sig["quant_key"] = (tuple(sorted(q[0])) if q and 0 in q else None)
    except Exception:
        sig["size"] = None
    # frequency-domain: generators under-produce true high-frequency sensor noise
    try:
        lum = np.asarray(Image.open(path).convert("L").resize((256, 256)), dtype=np.float32)
        f = np.abs(np.fft.fftshift(np.fft.fft2(lum - lum.mean())))
        h, w = f.shape
        yy, xx = np.mgrid[0:h, 0:w]
        r = np.hypot(yy - h // 2, xx - w // 2)
        sig["hf_ratio"] = float(f[r >= 100].mean() / (f[(r > 8) & (r < 60)].mean() + 1e-9))
    except Exception:
        sig["hf_ratio"] = None
    return sig


def audit_class(cls_dir, verbose=False):
    files = sorted(p for p in cls_dir.rglob("*") if p.suffix.lower() in EXTS)
    n = len(files)
    out = {"class": cls_dir.name, "images": n}
    if n < MIN_IMAGES:
        out["status"] = "too_few"
        return out

    feats, sizes, aspects, quants, hf = [], [], [], [], []
    for p in files:
        try:
            feats.append(features(p))
        except Exception:
            continue
        s = generator_signature(p)
        if s.get("size"):
            sizes.append(s["size"])
            aspects.append(s["aspect"])
        if s.get("quant_key") is not None:
            quants.append(s["quant_key"])
        if s.get("hf_ratio") is not None:
            hf.append(s["hf_ratio"])

    if len(feats) < MIN_IMAGES:
        out["status"] = "too_few"
        return out

    # Cap the pairwise work on big classes so this stays fast enough for CI.
    idx = list(range(len(feats)))
    if len(idx) > 220:
        step = len(idx) // 220
        idx = idx[::step][:220]
    sims = np.array([cosine(feats[i], feats[j]) for i, j in itertools.combinations(idx, 2)])

    out.update(
        status="ok",
        mean_similarity=round(float(sims.mean()), 4),
        max_similarity=round(float(sims.max()), 4),
        diversity_std=round(float(sims.std()), 4),
        distinct_sizes=len(set(sizes)),
        distinct_aspects=len({round(a, 2) for a in aspects}),
        quant_table_variants=len(set(quants)),
        mean_hf_ratio=round(float(np.mean(hf)), 4) if hf else None,
    )

    # Verdict + the more likely innocent explanation.
    reasons = []
    if out["mean_similarity"] >= SUSPECT_SIM:
        reasons.append(f"mean pairwise similarity {out['mean_similarity']:.3f} "
                       f">= {SUSPECT_SIM}")
    if out["diversity_std"] <= SUSPECT_DIV_STD:
        reasons.append(f"diversity std {out['diversity_std']:.4f} "
                       f"<= {SUSPECT_DIV_STD}")
    if reasons and out["distinct_sizes"] <= 3:
        reasons.append(f"only {out['distinct_sizes']} distinct pixel dimensions "
                       f"across {n} images")
    if reasons and out["distinct_aspects"] <= 2:
        reasons.append(f"only {out['distinct_aspects']} distinct aspect ratios")

    out["suspicious"] = len(reasons) >= 2
    out["reasons"] = reasons
    if out["suspicious"]:
        out["likely_cause"] = (
            "generator output" if out["distinct_sizes"] <= 3 and out["distinct_aspects"] <= 2
            else "single capture session (or generator output at varied crops)"
        )
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=str(DEFAULT_RAW))
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    root = Path(args.data)
    if not root.is_dir():
        print(f"ERROR: no dataset root at {root}", file=sys.stderr)
        return 1

    dirs = sorted(d for d in root.iterdir() if d.is_dir() and not d.name.startswith("_"))
    if not dirs:
        print(f"ERROR: no class folders under {root}", file=sys.stderr)
        return 1

    results = [audit_class(d) for d in dirs]
    suspect = [r for r in results if r.get("suspicious")]

    if args.json:
        print(json.dumps({"root": str(root), "classes": results,
                          "suspicious": [r["class"] for r in suspect],
                          "thresholds": {"suspect_similarity": SUSPECT_SIM,
                                         "suspect_diversity_std": SUSPECT_DIV_STD,
                                         "min_images": MIN_IMAGES}}, indent=2))
        return 2 if suspect else 0

    print("=" * 82)
    print("Dataset diversity audit — is this class too uniform to be real photographs?")
    print("=" * 82)
    print(f"root: {root}")
    print(f"\n{'class':22s} {'n':>5s} {'meanSim':>8s} {'divStd':>8s} {'sizes':>6s} "
          f"{'aspects':>8s} {'quant':>6s}  verdict")
    for r in sorted(results, key=lambda x: x.get("mean_similarity", 0)):
        if r["status"] != "ok":
            print(f"{r['class']:22s} {r['images']:>5d} {'-':>8s} {'-':>8s} {'-':>6s} "
                  f"{'-':>8s} {'-':>6s}  too few images to judge")
            continue
        v = "SUSPICIOUS" if r["suspicious"] else "ok"
        print(f"{r['class']:22s} {r['images']:>5d} {r['mean_similarity']:>8.4f} "
              f"{r['diversity_std']:>8.4f} {r['distinct_sizes']:>6d} "
              f"{r['distinct_aspects']:>8d} {r['quant_table_variants']:>6d}  {v}")

    if suspect:
        print(f"\n{len(suspect)} suspicious class(es):")
        for r in suspect:
            print(f"\n  {r['class']}  (n={r['images']})")
            for why in r["reasons"]:
                print(f"    - {why}")
            print(f"    most likely cause: {r['likely_cause']}")
            print(f"    mean HF ratio {r['mean_hf_ratio']} (corroboration only; low values "
                  f"hint at synthesised or heavily-denoised images)")
        print("\n  This is a prompt to look, not a verdict. Open a sample. If the photos\n"
              "  are real but from one session, the fix is more sessions, not more images.")
        print("\n  Reference measured on one class:")
        print("    real photographs : mean similarity 0.7041, diversity std 0.0700")
        print("    generated images : mean similarity 0.9363, diversity std 0.0152")

    print("\n" + "=" * 82)
    print(f"{len(results)-len(suspect)}/{len(results)} classes look sufficiently diverse."
          if not suspect else
          f"{len(suspect)} of {len(results)} classes need a human look.")
    return 2 if suspect else 0


if __name__ == "__main__":
    sys.exit(main())
