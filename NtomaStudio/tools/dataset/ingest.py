#!/usr/bin/env python3
"""Ingest a contribution zip (exported by the app) into tools/dataset/raw.

The app's export zip contains manifest.json ({exportedAt, files:[{category,file}]})
plus one JPEG per contribution under <CATEGORY>/ folders. This tool:

  1. validates the manifest (parses, categories exist in label_schema.json,
     every listed file is present in the zip),
  2. rejects near-duplicates via 64-bit dHash (hamming <= threshold) against
     both the existing raw pool and earlier entries in the same zip,
  3. unpacks survivors to raw/<CATEGORY>/<original name> (collision-safe).

Dry-run by default so a bad batch never touches the pool; pass --apply to write.

Usage:
  python3 tools/dataset/ingest.py path/to/ntoma_contributions.zip [--apply] [--threshold 6]
"""
import argparse
import json
import pathlib
import sys
import zipfile

from PIL import Image
import io

HERE = pathlib.Path(__file__).resolve().parent
RAW = HERE / "raw"
SCHEMA = HERE / "label_schema.json"


def dhash(img: Image.Image) -> int:
    g = img.convert("L").resize((9, 8))
    px = g.tobytes()
    h = 0
    for y in range(8):
        for x in range(8):
            h = (h << 1) | (1 if px[y * 9 + x] > px[y * 9 + x + 1] else 0)
    return h


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("zip", help="contribution zip exported by the app")
    ap.add_argument("--apply", action="store_true", help="actually write files (default: dry run)")
    ap.add_argument("--threshold", type=int, default=6, help="dHash hamming distance considered duplicate")
    args = ap.parse_args()

    schema = json.loads(SCHEMA.read_text())
    classes = set(schema.get("classes", []))
    aliases = {k: v for k, v in schema.get("aliases", {}).items() if not k.startswith("_")}
    review_reason = schema.get("review_reason", {})

    pool = {}  # hash of every existing raw image
    for f in list(RAW.rglob("*.jpg")) + list(RAW.rglob("*.png")) + list(RAW.rglob("*.webp")):
        try:
            pool[f.name] = dhash(Image.open(f))
        except Exception:
            pass

    accepted, dupes, errors, queued, remapped = [], [], [], [], []
    batch_hashes = {}

    with zipfile.ZipFile(args.zip) as z:
        names = set(z.namelist())
        if "manifest.json" not in names:
            print("ERROR: zip has no manifest.json — export from a current app build.", file=sys.stderr)
            return 2
        manifest = json.loads(z.read("manifest.json"))
        entries = manifest.get("files", [])
        if not entries:
            print("ERROR: manifest lists no files.", file=sys.stderr)
            return 2

        for entry in entries:
            raw_cat, rel = entry.get("category"), entry.get("file")
            # Resolve shipped-v1 category names before anything else, so a contribution
            # zip from an older build is never rejected as an unknown category.
            cat = aliases.get(raw_cat, raw_cat)
            if cat == "@REVIEW":
                if raw_cat not in classes:
                    remapped.append(raw_cat)
            elif raw_cat != cat:
                remapped.append(raw_cat)

            if rel not in names and f"{raw_cat}/{rel}" not in names:
                errors.append(f"{rel}: listed in manifest but missing from zip")
                continue
            member = rel if rel in names else f"{raw_cat}/{rel}"
            try:
                img = Image.open(io.BytesIO(z.read(member)))
            except Exception as e:
                errors.append(f"{rel}: cannot decode ({e})")
                continue
            h = dhash(img)
            near = [n for n, ph in {**pool, **batch_hashes}.items() if hamming(h, ph) <= args.threshold]
            if near:
                dupes.append(f"{rel} ~ {near[0]}")
                continue
            batch_hashes[rel] = h

            if cat == "@REVIEW":
                # Ambiguous legacy label: keep the photo, park it for a human rather than
                # guessing a class. See schema "review_reason" for why these are ambiguous.
                queued.append((raw_cat, rel, member))
            elif cat not in classes:
                errors.append(f"{rel}: unknown category {raw_cat!r} (no alias mapping)")
            else:
                accepted.append((cat, rel, member))

    print(f"manifest entries: {len(entries)}")
    if remapped:
        uniq = sorted(set(remapped))
        print(f"legacy v1 categories remapped via schema aliases: {', '.join(uniq)}")
    print(f"accepted: {len(accepted)}")
    for cat, rel, _ in accepted:
        print(f"  + {cat}/{pathlib.Path(rel).name}")
    if queued:
        print(f"queued for human review: {len(queued)}  (kept, NOT discarded)")
        for raw_cat, rel, _ in queued:
            print(f"  ? {raw_cat}/{pathlib.Path(rel).name} -> raw/_REVIEW/{raw_cat}/")
        for raw_cat in sorted({q[0] for q in queued}):
            why = review_reason.get(raw_cat)
            if why:
                print(f"    why {raw_cat}: {why}")
    print(f"duplicates skipped: {len(dupes)}")
    for d in dupes:
        print(f"  = {d}")
    if errors:
        print(f"errors: {len(errors)}")
        for e in errors:
            print(f"  ! {e}")

    if not args.apply:
        print("\ndry run — pass --apply to write.")
        return 0

    def write(cat, rel, member):
        with zipfile.ZipFile(args.zip) as z:
            data = z.read(member)
        out_dir = RAW / cat
        out_dir.mkdir(parents=True, exist_ok=True)
        out = out_dir / pathlib.Path(rel).name
        n = 2
        stem, suffix = pathlib.Path(rel).stem, pathlib.Path(rel).suffix
        while out.exists():
            out = out_dir / f"{stem}_{n}{suffix}"
            n += 1
        out.write_bytes(data)
        print(f"wrote {out.relative_to(HERE)}")

    for cat, rel, member in accepted:
        write(cat, rel, member)
    for raw_cat, rel, member in queued:
        write(f"_REVIEW/{raw_cat}", rel, member)

    if queued:
        print(f"\n{len(queued)} photo(s) parked in raw/_REVIEW/ - resolve them before training.")
        print("They are excluded from counts by track_collection.py until re-filed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
