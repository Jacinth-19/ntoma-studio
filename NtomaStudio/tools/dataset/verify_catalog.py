#!/usr/bin/env python3
"""Verify that shipped catalog data matches the folders and labels it claims.

Answers three questions, repeatably and in CI:

1. **Folder/label correspondence.** Does every `FabricCategory` asset exist, does every
   file on disk belong to a label, and does every `looks.json` entry reference a real
   asset, a real fabric tag and a real string key?
2. **Asset hygiene.** Dimensions, near-duplicates (dHash) *between* categories, and
   vendor watermarks (bright saturated text over a flat background corner).
3. **Dataset readiness.** Per-class raw photo counts in `raw/<CLASS>/` against the
   thresholds in `label_schema.json` — so "are we ready to train?" is a number, not a vibe.

Read-only: never writes, never mutates the catalog.

Usage:
    python3 tools/dataset/verify_catalog.py              # human report, CI exit code
    python3 tools/dataset/verify_catalog.py --json       # machine-readable
Exit: 0 = no hard failures, 1 = hard failure (missing/orphan/unresolvable asset),
      2 = warnings only (provenance, watermark, duplicates, thin dataset).
"""
import argparse
import glob
import hashlib
import itertools
import json
import os
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]          # NtomaStudio/
ASSETS = ROOT / "app/src/main/assets"
CATALOG = ASSETS / "catalog"
FABRIC_KT = ROOT / "app/src/main/java/com/ntoma/studio/domain/model/Fabric.kt"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
SCHEMA = ROOT / "tools/dataset/label_schema.json"
RAW = ROOT / "tools/dataset/raw"

HARD, WARN = [], []


def hard(msg):
    HARD.append(msg)


def warn(msg):
    WARN.append(msg)


# ---------------------------------------------------------------- source parsing

def enum_categories():
    """FabricCategory members and their declared asset, parsed from Fabric.kt."""
    src = FABRIC_KT.read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(
        r'^\s{4}([A-Z_]+)\(R\.string\.fabric_\w+,\s*R\.string\.fabric_\w+_desc'
        r'(?:,\s*"([^"]+)")?\)',
        src, re.M,
    ):
        out[m.group(1)] = m.group(2)
    return out


def string_keys():
    return set(re.findall(r'name="([^"]+)"', STRINGS.read_text(encoding="utf-8")))


# ---------------------------------------------------------------- image helpers

def dhash(path, size=16):
    img = Image.open(path).convert("L").resize((size + 1, size), Image.LANCZOS)
    px = list(img.getdata())
    bits = 0
    for r in range(size):
        for c in range(size):
            bits = (bits << 1) | (1 if px[r * size + c] < px[r * size + c + 1] else 0)
    return bits


def watermark_suspect(path, frac=0.10, min_sat=0.55, min_val=0.55, max_bg_std=14.0):
    """A saturated bright stamp sitting on a *flat* background is a vendor mark.

    Saturated pixels alone are not enough - kente strip cloth and tie-dye are full of
    them. What distinguishes a stamped SKU from fabric is that the pixels *around* the
    stamp are locally uniform (studio backdrop / flat ground), so we also require the
    non-stamp luminance standard deviation in that corner to be low.

    Returns (bool, corner, coverage).
    """
    img = Image.open(path).convert("RGB")
    w, h = img.size
    cw, ch = max(1, int(w * frac)), max(1, int(h * frac))
    corners = {
        "top-right": (w - cw, 0), "top-left": (0, 0),
        "bottom-right": (w - cw, h - ch), "bottom-left": (0, h - ch),
    }
    for name, (x, y) in corners.items():
        sub = img.crop((x, y, x + cw, y + ch))
        px = list(sub.resize((48, 48)).getdata())
        stamps, background = 0, []
        for r, g, b in px:
            mx, mn = max(r, g, b), min(r, g, b)
            sat = 0 if mx == 0 else (mx - mn) / mx
            if sat >= min_sat and mx / 255 >= min_val:
                stamps += 1
            else:
                background.append(0.299 * r + 0.587 * g + 0.114 * b)
        cov = stamps / len(px)
        if not background or not (0.01 <= cov <= 0.6):
            continue
        mean = sum(background) / len(background)
        std = (sum((v - mean) ** 2 for v in background) / len(background)) ** 0.5
        if std <= max_bg_std:
            return True, name, round(cov, 3), round(std, 1)
    return False, None, 0.0, None


def studio_background(path, near_white=235, flat=10.0):
    """Product-shot detector: a large near-white margin around the cloth.

    Honest for the asset itself, but a *consistency* problem when only some of the
    catalog uses it - those tiles look like shop listings next to the rest.
    """
    img = Image.open(path).convert("RGB")
    px = list(img.resize((64, 64)).getdata())
    white = sum(1 for r, g, b in px if min(r, g, b) >= near_white) / len(px)
    if white < 0.30:
        return False, round(white, 3)
    # confirm the margin is actually flat, not a bright fabric
    vals = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b in px
            if min(r, g, b) >= near_white]
    mean = sum(vals) / len(vals)
    std = (sum((v - mean) ** 2 for v in vals) / len(vals)) ** 0.5
    return std <= flat, round(white, 3)


# ---------------------------------------------------------------- checks

def check_folders():
    cats = enum_categories()
    facts = {"categories": len(cats), "with_asset": 0, "without_asset": []}

    print("\n== 1. FabricCategory <-> catalog/fabrics/ ==")
    disk = {os.path.basename(p) for p in glob.glob(str(CATALOG / "fabrics/*.jpg"))}
    referenced = set()

    for cat, asset in sorted(cats.items()):
        if asset is None:
            facts["without_asset"].append(cat)
            print(f"  {cat:14s} (no asset declared)")
            continue
        facts["with_asset"] += 1
        p = ASSETS / asset
        referenced.add(os.path.basename(asset))
        if not p.exists():
            hard(f"{cat}: declared asset missing on disk -> {asset}")
            print(f"  {cat:14s} MISSING {asset}")
            continue
        with Image.open(p) as im:
            w, h = im.size
        print(f"  {cat:14s} OK  {os.path.basename(asset):18s} {w}x{h}  {p.stat().st_size//1024} KB")

    orphans = disk - referenced
    for o in sorted(orphans):
        hard(f"orphan asset not referenced by any category: catalog/fabrics/{o}")
    if orphans:
        print(f"  ORPHAN FILES (no category claims them): {sorted(orphans)}")

    facts["orphans"] = sorted(orphans)
    facts["disk_files"] = len(disk)
    return cats, facts


def check_looks(cats):
    print("\n== 2. looks.json <-> looks/ + strings ==")
    data = json.loads((CATALOG / "looks.json").read_text(encoding="utf-8"))
    looks = data["looks"]
    keys = string_keys()
    facts = {"looks": len(looks)}

    for lk in looks:
        p = ASSETS / lk["asset"]
        if not p.exists():
            hard(f"look {lk['id']}: asset missing -> {lk['asset']}")
        if lk["title_key"] not in keys:
            hard(f"look {lk['id']}: title_key '{lk['title_key']}' not in values/strings.xml")
        if lk["gender"] not in {"MEN", "WOMEN", "UNISEX"}:
            hard(f"look {lk['id']}: unknown gender '{lk['gender']}'")
        for f in lk["fabrics"]:
            if f not in cats:
                hard(f"look {lk['id']}: fabric tag '{f}' is not a FabricCategory")

    ondisk = {os.path.basename(p) for p in glob.glob(str(CATALOG / "looks/*.jpg"))}
    declared = {os.path.basename(lk["asset"]) for lk in looks}
    for o in sorted(ondisk - declared):
        hard(f"orphan look image not in looks.json: catalog/looks/{o}")

    # Coverage: which categories can actually produce a look, and for whom?
    print(f"  {len(looks)} looks, all assets + keys resolve" if not HARD else "")
    coverage = {}
    for cat in cats:
        tagged = [lk for lk in looks if cat in lk["fabrics"]]
        coverage[cat] = {
            "looks": len(tagged),
            "genders": sorted({lk["gender"] for lk in tagged}),
        }
    print(f"  {'CATEGORY':14s} looks  genders")
    uncovered = []
    for cat, c in coverage.items():
        note = ""
        if cat == "UNKNOWN":
            note = "(matches everything by design)"
        elif c["looks"] == 0:
            uncovered.append(cat)
            note = "<-- NO LOOK: section is hidden entirely"
            warn(f"{cat}: no inspiration look; recommendations screen shows no looks section")
        print(f"  {cat:14s} {c['looks']:^5d}  {','.join(c['genders']) or '-':12s} {note}")

    both = [c for c, v in coverage.items() if {"MEN", "WOMEN"} <= set(v["genders"])]
    facts["uncovered"] = uncovered
    facts["both_gender"] = both
    facts["coverage"] = coverage
    print(f"  coverage: {len(cats) - len(uncovered)}/{len(cats)} categories have >=1 look")
    print(f"  both genders represented: {len(both)}/{len(cats)}")
    return facts


def check_hygiene(cats):
    print("\n== 3. Asset hygiene: duplicates + watermarks ==")
    by_name = {os.path.basename(a): c for c, a in cats.items() if a}
    files = sorted(glob.glob(str(CATALOG / "fabrics/*.jpg")))
    hashes = {f: dhash(f) for f in files}

    dupe_pairs = []
    for a, b in itertools.combinations(files, 2):
        d = bin(hashes[a] ^ hashes[b]).count("1")
        if d <= 12:                       # <=12/256 bits apart = near-identical
            dupe_pairs.append((os.path.basename(a), os.path.basename(b), d))
    for a, b, d in dupe_pairs:
        warn(f"{by_name[a]} and {by_name[b]} are near-identical (dHash {d}) "
             f"- two categories share one visual exemplar")
        print(f"  NEAR-DUPLICATE {a} ~ {b} (dHash {d})")

    flags = []
    for f in files:
        hit, corner, cov, bg_std = watermark_suspect(f)
        if hit:
            flags.append({"file": os.path.basename(f), "category": by_name[os.path.basename(f)],
                          "corner": corner, "coverage": cov, "bg_std": bg_std})
            warn(f"{by_name[os.path.basename(f)]}: vendor stamp detected ({corner}, "
                 f"{cov:.0%} of corner, flat backdrop sd={bg_std}) in {os.path.basename(f)}")
            print(f"  STAMP     {os.path.basename(f):18s} ({by_name[os.path.basename(f)]}) "
                  f"at {corner}, {cov:.0%} of corner, backdrop sd={bg_std}")

    studio = []
    for f in files:
        is_studio, white = studio_background(f)
        if is_studio:
            studio.append({"file": os.path.basename(f),
                           "category": by_name[os.path.basename(f)], "white": white})
    if studio:
        print(f"  PRESENTATION {len(studio)}/{len(files)} fabric assets are studio product shots "
              f"(white margin > 30%): {', '.join(s['category'] for s in studio)}")
        warn("mixed presentation: "
             f"{', '.join(s['category'] for s in studio)} use a white studio margin while the "
             f"other {len(files) - len(studio)} are full-frame flat-lay/drape photos")
    if not dupe_pairs and not flags and not studio:
        print("  clean: no near-duplicate exemplars, no stamps, uniform presentation")
    return {"duplicates": dupe_pairs, "watermarks": flags, "studio_shots": studio}


def check_provenance():
    print("\n== 4. Provenance / licence trail ==")
    looks = json.loads((CATALOG / "looks.json").read_text(encoding="utf-8"))
    documented_looks = sum(1 for lk in looks["looks"] if lk.get("source"))
    total_looks = len(looks["looks"])
    print(f"  looks.json entries with a 'source' field: {documented_looks}/{total_looks}")
    fabric_files = sorted(glob.glob(str(CATALOG / "fabrics/*.jpg")))
    manifests = [p for p in CATALOG.glob("*.json") if "fabric" in p.name]
    if not manifests:
        warn(f"{len(fabric_files)} fabric photos carry no recorded source/licence "
             f"(no fabrics manifest in catalog/) - a Play Store release risk")
        print(f"  fabrics.json manifest: ABSENT -> {len(fabric_files)} fabric photos have "
              f"no recorded source or licence")
    else:
        print(f"  fabrics manifests: {[p.name for p in manifests]}")
    return {"looks_documented": documented_looks, "looks_total": total_looks,
            "fabric_photos": len(fabric_files), "fabric_manifest": bool(manifests)}


def check_dataset_readiness():
    print("\n== 5. Training-data readiness (raw/<CLASS>/) ==")
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    classes = schema["classes"]
    guidance = schema.get("collection_guidance", {})
    train_min, claim_min = 300, 1000

    facts = {"classes": len(classes), "per_class": {}, "train_min": train_min,
             "claim_min": claim_min}
    total_raw = 0
    for c in classes:
        d = RAW / c
        n = len(list(d.glob("*.jpg"))) + len(list(d.glob("*.png"))) + len(list(d.glob("*.webp"))) if d.is_dir() else 0
        facts["per_class"][c] = n
        total_raw += n
    facts["total_raw"] = total_raw

    if total_raw == 0:
        print("  raw/ is empty (gitignored, as designed) - no field photos ingested yet.")
        print(f"  targets: {train_min}/class to train, {claim_min}/class before accuracy claims")
        print(f"  => needing {train_min * len(classes):,} photos to train, "
              f"{claim_min * len(classes):,} to publish numbers")
        facts["status"] = "no_data"
        warn("training dataset is empty: 0 real labelled photos across "
             f"{len(classes)} classes")
        return facts

    ready = claim_ready = 0
    for c in classes:
        n = facts["per_class"][c]
        mark = "CLAIM-READY" if n >= claim_min else ("trainable" if n >= train_min else "thin")
        ready += n >= train_min
        claim_ready += n >= claim_min
        print(f"  {c:14s} {n:5d}  {mark}")
    print(f"  totals: {total_raw:,} photos | trainable classes {ready}/{len(classes)} | "
          f"claim-ready {claim_ready}/{len(classes)}")
    facts["trainable_classes"] = ready
    facts["claim_ready_classes"] = claim_ready
    if ready < len(classes):
        warn(f"{len(classes) - ready} of {len(classes)} classes are below {train_min} photos")
    return facts


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    args = ap.parse_args()

    if args.json:
        import io
        buf = io.StringIO()
        real, sys.stdout = sys.stdout, buf
        cats, f1 = check_folders()
        f2 = check_looks(cats)
        f3 = check_hygiene(cats)
        f4 = check_provenance()
        f5 = check_dataset_readiness()
        sys.stdout = real
        print(json.dumps({"folders": f1, "looks": f2, "hygiene": f3,
                          "provenance": f4, "dataset": f5,
                          "hard_failures": HARD, "warnings": WARN}, indent=2))
    else:
        print("=" * 72)
        print("Ntoma catalog + dataset verification")
        print("=" * 72)
        cats, _ = check_folders()
        check_looks(cats)
        check_hygiene(cats)
        check_provenance()
        check_dataset_readiness()
        print("\n" + "=" * 72)
        if HARD:
            print(f"HARD FAILURES ({len(HARD)}):")
            for m in HARD:
                print(f"  x {m}")
        if WARN:
            print(f"WARNINGS ({len(WARN)}):")
            for m in WARN:
                print(f"  ! {m}")
        if not HARD and not WARN:
            print("VERIFIED CLEAN")
        elif not HARD:
            print("VERIFIED: data matches its folders; warnings above need decisions.")

    return 1 if HARD else (2 if WARN else 0)


if __name__ == "__main__":
    sys.exit(main())
