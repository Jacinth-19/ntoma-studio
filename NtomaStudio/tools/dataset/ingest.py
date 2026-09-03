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

    pool = {}  # hash of every existing raw image
    for f in RAW.rglob("*.jpg"):
        try:
            pool[f.name] = dhash(Image.open(f))
        except Exception:
            pass

    accepted, dupes, errors = [], [], []
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
            cat, rel = entry.get("category"), entry.get("file")
            if cat not in classes:
                errors.append(f"{rel}: unknown category {cat!r}")
                continue
            if rel not in names and f"{cat}/{rel}" not in names:
                member = rel if rel in names else f"{cat}/{rel}"
                errors.append(f"{rel}: listed in manifest but missing from zip")
                continue
            member = rel if rel in names else f"{cat}/{rel}"
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
            accepted.append((cat, rel, member))

    print(f"manifest entries: {len(entries)}")
    print(f"accepted: {len(accepted)}")
    for cat, rel, _ in accepted:
        print(f"  + {cat}/{pathlib.Path(rel).name}")
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

    for cat, rel, member in accepted:
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
