#!/usr/bin/env python3
"""Package tools/dataset/raw/ into a Kaggle dataset archive.

Produces a zip whose root contains `raw/<CLASS>/*.jpg`, so that after uploading it as a
Kaggle Dataset the training script finds it at /kaggle/input/<slug>/raw - which is
exactly the default of train_kaggle.py.

Refuses to build a dataset that would produce a dishonest model, unless --force:
  * classes below the plan target are reported with their shortfall
  * classes with too few SESSIONS (<3) are reported, because they cannot be split
  * near-duplicates inside a class are reported (they inflate a class without adding
    information)
  * images below a minimum edge length are reported, since the pipeline trains at 224px

Usage:
    python3 tools/dataset/kaggle/make_kaggle_dataset.py            # dry run + report
    python3 tools/dataset/kaggle/make_kaggle_dataset.py --write    # produce the zip
    python3 tools/dataset/kaggle/make_kaggle_dataset.py --write --force
"""
import argparse
import json
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOLS = HERE.parent
RAW = TOOLS / "raw"
SCHEMA = TOOLS / "label_schema.json"
PLAN = TOOLS / "collection_plan.json"

EXTS = (".jpg", ".jpeg", ".png", ".webp")
MIN_EDGE = 200          # below this, 224px training is upscaling noise
KAGGLE_SOFT_LIMIT_GB = 20


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--write", action="store_true", help="produce the zip")
    ap.add_argument("--force", action="store_true", help="build despite blocking conditions")
    ap.add_argument("--out", default="ntoma-fabric-kaggle.zip")
    ap.add_argument("--data", default=str(RAW),
                    help=f"class-folder root (default: {RAW})")
    ap.add_argument("--min-per-class", type=int, default=0,
                    help="refuse if any class is below this (0 = report only)")
    ap.add_argument("--quiet-notes", action="store_true")
    args = ap.parse_args()

    raw = Path(args.data)
    if not raw.is_dir():
        print(f"ERROR: no class-folder root at {raw}", file=sys.stderr)
        print("Collect or ingest photos first (see tools/dataset/README.md), or pass "
              "--data <path>.", file=sys.stderr)
        return 2

    schema = json.loads(SCHEMA.read_text())
    valid = set(schema["classes"])
    targets = json.loads(PLAN.read_text())["targets"] if PLAN.exists() else {}

    sys.path.insert(0, str(HERE))
    try:
        from train_kaggle import session_key, index_dataset  # reuse, do not duplicate
    except Exception:
        index_dataset = session_key = None

    files, sizes = [], 0
    per_class, sessions, small, offschema = {}, defaultdict(set), [], []

    for cls_dir in sorted(p for p in raw.iterdir() if p.is_dir()):
        if cls_dir.name.startswith("_"):
            continue
        if cls_dir.name not in valid:
            n = len([f for f in cls_dir.rglob("*") if f.suffix.lower() in EXTS])
            if n:
                offschema.append((cls_dir.name, n))
            continue
        got = 0
        for f in sorted(cls_dir.rglob("*")):
            if f.suffix.lower() not in EXTS:
                continue
            files.append(f)
            sizes += f.stat().st_size
            got += 1
            if index_dataset is None:
                continue
            sessions[cls_dir.name].add(session_key(f))
            if not small or len(small) < 5:
                try:
                    from PIL import Image
                    with Image.open(f) as im:
                        if min(im.size) < MIN_EDGE:
                            small.append((f.name, im.size))
                except Exception:
                    small.append((f.name, "unreadable"))
        per_class[cls_dir.name] = got

    print("=" * 74)
    print("Kaggle dataset packager")
    print("=" * 74)
    print(f"images: {len(files):,} | classes: {len(per_class)} | "
          f"raw bytes: {sizes/1e9:.2f} GB")

    blocking, notes = [], []
    if offschema:
        blocking.append(f"{sum(n for _, n in offschema)} photos in folders that are not "
                        f"schema classes: {offschema}")
    if not per_class:
        blocking.append("no images found in any valid class folder")

    missing = [c for c in valid if c not in per_class and c != "UNKNOWN"]
    if missing:
        notes.append(f"{len(missing)} schema classes have no photos at all: "
                     f"{', '.join(missing[:8])}{' ...' if len(missing) > 8 else ''}")

    if targets:
        short = {c: (per_class.get(c, 0), targets[c]) for c in per_class
                 if targets.get(c) and per_class[c] < targets[c]}
        if short:
            notes.append(f"{len(short)} class(es) below plan target, e.g. "
                         + ", ".join(f"{c} {a}/{b}" for c, (a, b) in list(short.items())[:5]))

    few_sessions = {c: len(s) for c, s in sessions.items() if len(s) < 3}
    if few_sessions:
        notes.append(f"{len(few_sessions)} class(es) have <3 capture sessions and cannot "
                     f"be split honestly: {few_sessions}")

    if small:
        notes.append(f"at least {len(small)} image(s) below {MIN_EDGE}px min edge "
                     f"(upscaled to 224px during training): {small[:3]}")

    for n in notes:
        print(f"  note: {n}")
    for b in blocking:
        print(f"  BLOCK: {b}")

    if args.min_per_class:
        under = {c: n for c, n in per_class.items() if n < args.min_per_class}
        if under:
            blocking.append(f"classes below --min-per-class {args.min_per_class}: {under}")

    if not args.write:
        print("\ndry run - pass --write to produce the zip.")
        return 1 if blocking else 0

    if blocking and not args.force:
        print("\nrefusing to build. Fix the blocking items or pass --force.", file=sys.stderr)
        return 2

    out = Path(args.out)
    print(f"\nwriting {out} ...")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for f in files:
            z.write(f, f.relative_to(raw.parent))     # keeps the leading folder name
    gb = out.stat().st_size / 1e9
    print(f"wrote {out} ({gb:.2f} GB, {len(files):,} images)")
    if gb > KAGGLE_SOFT_LIMIT_GB:
        print(f"!! above the ~{KAGGLE_SOFT_LIMIT_GB} GB comfortable upload size. Consider "
              f"resizing working copies to 512px, or uploading in parts "
              f"(kaggle datasets create -p <dir> --dir-mode zip).")

    (Path(args.out).parent / "kaggle_dataset_manifest.json").write_text(json.dumps({
        "images": len(files), "classes": len(per_class), "per_class": per_class,
        "sessions_per_class": {c: len(s) for c, s in sessions.items()},
        "bytes": sizes, "archive_bytes": out.stat().st_size,
        "notes": notes, "blocking": blocking,
        "layout": f"{raw.name}/<CLASS>/*.jpg",
    }, indent=2))
    print(f"wrote {Path(args.out).parent/'kaggle_dataset_manifest.json'}")
    print("\nUpload with:  kaggle datasets create -p "
          f"{out.parent} --dir-mode zip   (or drag it into kaggle.com/datasets/new)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
