#!/usr/bin/env python3
"""Rename legacy v1 class folders in raw/ to their v2 schema names.

`train_kaggle.py` takes the class name from the FOLDER NAME. A folder that is not
a class in `label_schema.json` is skipped without complaint, so photos sitting in
a v1-named folder are silently excluded from training. `track_collection.py`
warns about each one, but nothing fixes it.

The mapping is not invented here: it is read from the `aliases` block of
`label_schema.json`, the same map `ingest.py` applies to contribution zips. That
keeps one source of truth for what a v1 name means.

Names mapped to `@REVIEW` are deliberately NOT touched. Those are cases where the
v1 label does not determine the v2 class (`KENTE` cannot say Ashanti vs Ewe;
`WAX` and `ANKARA` cannot say double-sided real wax vs single-sided fancy print),
so a human has to look at the cloth. Guessing would poison the training set.

Dry-run by default. `--apply` performs the rename.

    python3 tools/dataset/fix_folder_names.py            # show what would happen
    python3 tools/dataset/fix_folder_names.py --apply    # do it

Safe to run repeatedly: already-correct folders are reported and left alone, and
a target that already exists is merged rather than clobbered. Every file's MD5 is
checked before and after, and the run fails loudly if anything goes missing.

Exit codes: 0 = nothing to do or completed, 1 = errors, 2 = would change something
(dry run only).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "tools/dataset/raw"
SCHEMA = ROOT / "tools/dataset/label_schema.json"


def md5(path: Path) -> str:
    return hashlib.md5(path.read_bytes()).hexdigest()


def inventory(root: Path) -> dict[str, str]:
    """Relative path -> md5 for every file under root."""
    if not root.exists():
        return {}
    return {str(p.relative_to(root)): md5(p) for p in sorted(root.rglob("*")) if p.is_file()}


def load_aliases() -> dict[str, str]:
    if not SCHEMA.exists():
        sys.exit(f"ERROR: {SCHEMA} not found")
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    classes = set(schema["classes"])
    aliases = {k: v for k, v in schema.get("aliases", {}).items() if not k.startswith("_")}
    for src, dst in aliases.items():
        if dst != "@REVIEW" and dst not in classes:
            sys.exit(f"ERROR: alias {src} -> {dst}, but {dst} is not a schema class")
    return aliases


def merge_into(src: Path, dst: Path) -> tuple[int, int, list[str]]:
    """Move every file from src into dst without clobbering. Returns (moved, deduped, notes)."""
    moved = deduped = 0
    notes: list[str] = []
    dst.mkdir(parents=True, exist_ok=True)
    for item in sorted(src.rglob("*")):
        if not item.is_file():
            continue
        rel = item.relative_to(src)
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if md5(target) == md5(item):
                deduped += 1
                notes.append(f"identical duplicate dropped: {rel}")
                item.unlink()
                continue
            stem, i = target.stem, 2
            while target.exists():
                target = target.with_name(f"{stem}_renamed{i}{target.suffix}")
                i += 1
            notes.append(f"name collision, kept both: {rel} -> {target.name}")
        shutil.move(str(item), str(target))
        moved += 1
    # remove now-empty directories
    for d in sorted((p for p in src.rglob("*") if p.is_dir()), reverse=True):
        if not any(d.iterdir()):
            d.rmdir()
    if src.exists() and not any(src.iterdir()):
        src.rmdir()
    return moved, deduped, notes


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--apply", action="store_true", help="actually rename (default: dry run)")
    ap.add_argument("--raw", default=str(RAW), help=f"raw root (default: {RAW})")
    args = ap.parse_args()

    raw = Path(args.raw)
    if not raw.is_dir():
        print(f"No {raw} — nothing to do. (Extract raw.rar first.)")
        return 0

    aliases = load_aliases()
    before = inventory(raw)

    renames, reviews, already, unknown = [], [], [], []
    for d in sorted(p for p in raw.iterdir() if p.is_dir()):
        name = d.name
        n = len([x for x in d.rglob("*") if x.is_file()])
        if name in aliases:
            target = aliases[name]
            if target == "@REVIEW":
                reviews.append((name, n))
            else:
                renames.append((name, target, n))
        elif name.startswith("_"):
            continue
        else:
            already.append((name, n))

    print(f"raw root: {raw.relative_to(ROOT) if raw.is_relative_to(ROOT) else raw}")
    print(f"files before: {len(before)}\n")

    if renames:
        print("RENAME (v1 name -> v2 schema class)")
        for src, dst, n in renames:
            print(f"  {src:<18} -> {dst:<22} {n:>4} file(s)")
    else:
        print("RENAME: nothing to do — all renameable folders already use v2 names.")

    if reviews:
        print("\nNEEDS A HUMAN DECISION (left untouched)")
        for name, n in reviews:
            print(f"  {name:<18} {n:>4} file(s)   v1 name does not determine the v2 class")

    if already:
        print("\nALREADY v2 (untouched)")
        for name, n in already:
            print(f"  {name:<18} {n:>4} file(s)")

    if not renames:
        print("\nNothing to change.")
        return 0

    if not args.apply:
        print("\nDRY RUN — nothing written. Re-run with --apply to rename.")
        return 2

    print("\nApplying…")
    total_deduped = 0
    for src, dst, _ in renames:
        moved, deduped, notes = merge_into(raw / src, raw / dst)
        total_deduped += deduped
        extra = f", {deduped} duplicate(s) dropped" if deduped else ""
        print(f"  {src} -> {dst}: moved {moved}{extra}")
        for note in notes:
            print(f"      {note}")

    # Paths are *supposed* to change, so compare the multiset of content hashes.
    # Dropping a byte-identical duplicate is the one legitimate way for a count to
    # fall, so allow exactly that many and account for every one of them.
    after = inventory(raw)
    lost = Counter(before.values()) - Counter(after.values())
    gained = Counter(after.values()) - Counter(before.values())

    print(f"\nfiles after : {len(after)}")
    if sum(gained.values()):
        print(f"ERROR: {sum(gained.values())} unexpected file(s) appeared")
        return 1
    if sum(lost.values()) != total_deduped:
        print(f"ERROR: {sum(lost.values())} file(s) lost but only {total_deduped} "
              f"duplicate(s) were deliberately dropped")
        return 1
    print(f"content preserved: {len(after)} file(s), {len(set(after.values()))} unique"
          + (f", {total_deduped} identical duplicate(s) dropped" if total_deduped else ""))
    print("(paths changed, which is the point — content did not)")

    print("\nDone. Re-check with: python3 tools/dataset/track_collection.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
