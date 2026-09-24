#!/usr/bin/env python3
"""Write the train/val/test split and the dataset statistics.

The split rule is NOT defined here. It lives in kaggle/train_kaggle.py
(`split_sessions`), and this script imports it - so there is one definition of
what "leak-free" means and the manifest can never disagree with training.

Output (default tools/dataset/splits/):

  manifest.csv              file, class, group, split, source, session, ...
  dataset_statistics.json   counts, groups, attribute distributions, progress
  README.txt                what this is derived from
  train/ val/ test/         symlinks, only with --link

Why a manifest and not copied folders
-------------------------------------
Copying train/val/test into real directories duplicates every photo and creates
a second copy of the truth that goes stale the moment raw/ changes. A manifest
is a few hundred KB, is regenerated in seconds, and can be consumed by any
loader (`pandas.read_csv` -> group by `split`). Use --link when a tool insists
on real folders; symlinks cost no disk and stay in sync with raw/.

The leakage property is checked on the way out: no group may appear in two
splits. That is asserted, not assumed, because a silent leak inflates the
accuracy number without ever looking like a bug.

Usage:
  python3 tools/dataset/split_dataset.py                 # dry run
  python3 tools/dataset/split_dataset.py --write         # manifest + statistics
  python3 tools/dataset/split_dataset.py --write --link  # + symlink trees
"""
import argparse
import collections
import csv
import hashlib
import json
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
KAGGLE = HERE / "kaggle"
SCHEMA = HERE / "label_schema.json"
PLAN = HERE / "collection_plan.json"
RAW = HERE / "raw"
SPLITS = HERE / "splits"

ATTRS = ["region", "town", "technique", "material", "brand_family",
         "colour_primary", "pattern_scale", "view", "lighting", "occasion", "quality"]

sys.path.insert(0, str(KAGGLE))
try:
    import train_kaggle as T
except Exception as e:                                    # pragma: no cover
    print(f"ERROR: cannot import the split rule from kaggle/train_kaggle.py: {e}",
          file=sys.stderr)
    sys.exit(2)


def fingerprint(root):
    """Cheap staleness check: hash of (relative path, size) for every photo."""
    h = hashlib.sha256()
    for p in sorted(root.rglob("*")):
        if p.is_file() and p.suffix.lower() in T.EXTS:
            h.update(f"{p.relative_to(root)}|{p.stat().st_size}\n".encode())
    return h.hexdigest()[:16]


def assert_no_leakage(splits):
    """A group must not appear in more than one split. Returns the offender."""
    where = collections.defaultdict(set)
    for split, pairs in splits.items():
        for group, _cls in pairs:
            where[group].add(split)
    return {g: sorted(s) for g, s in where.items() if len(s) > 1}


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=str(RAW))
    ap.add_argument("--metadata", default=None)
    ap.add_argument("--out", default=str(SPLITS))
    ap.add_argument("--seed", type=int, default=T.DEFAULTS["seed"])
    ap.add_argument("--val-frac", type=float, default=T.DEFAULTS["val_frac"])
    ap.add_argument("--test-frac", type=float, default=T.DEFAULTS["test_frac"])
    ap.add_argument("--write", action="store_true", help="write the manifest and stats")
    ap.add_argument("--link", action="store_true",
                    help="also build train/val/test symlink trees (needs --write)")
    args = ap.parse_args()

    root = Path(args.data).resolve()
    out = Path(args.out).resolve()
    if not root.is_dir():
        print(f"ERROR: dataset root not found: {root}", file=sys.stderr)
        return 2

    classes = T.schema_classes() or set(json.loads(SCHEMA.read_text())["classes"])
    targets = json.loads(PLAN.read_text())["targets"] if PLAN.exists() else {}
    total_target = sum(targets.values())

    meta, meta_path = T.load_metadata(root, args.metadata)
    table = T.index_dataset(root, meta)

    # Carry the group alongside each path so the manifest can name it.
    grouped = {}
    for cls, sessions in table.items():
        for group, paths in sessions.items():
            for p in paths:
                grouped[p] = group

    tagged = {cls: {g: ps for g, ps in sess.items()} for cls, sess in table.items()}
    rng = random.Random(args.seed)
    splits, report = T.split_sessions(tagged, rng, args.val_frac, args.test_frac)

    # -> {split: [(group, cls, path)]}
    rows = {s: [(grouped[p], c, p) for p, c in pairs] for s, pairs in splits.items()}

    n_total = sum(len(v) for v in rows.values())
    print("=" * 74)
    print("Split manifest")
    print("=" * 74)
    print(f"root      : {root}")
    print(f"seed      : {args.seed}   val {args.val_frac}   test {args.test_frac} "
          f"(dealt per class, whole groups)")
    print(f"photos    : {n_total:,}   classes: {len(table)}")
    for s in ("train", "val", "test"):
        n = len(rows[s])
        print(f"  {s:<6} {n:>7,}  ({n / n_total * 100:5.1f}%)" if n_total else "")

    leaked = assert_no_leakage({s: [(g, c) for g, c, _ in v] for s, v in rows.items()})
    if leaked:
        print(f"\n!! LEAKAGE: {len(leaked)} group(s) appear in more than one split: "
              f"{list(leaked.items())[:3]}", file=sys.stderr)
        return 1

    unsplittable = sorted(c for c, r in report.items() if not r["splittable"])
    ungrouped = {c: len(s.get("unknown", [])) for c, s in table.items()
                 if s.get("unknown")}
    n_ungrouped = sum(ungrouped.values())

    n_groups = len(set(g for v in rows.values() for g, _, _ in v))
    print(f"\nleakage check : no group appears in two splits  "
          f"({n_groups:,} group{'' if n_groups == 1 else 's'})")
    print(f"unsplittable  : {len(unsplittable)} class(es) below 3 groups "
          f"(train-only, no accuracy claim)")
    if n_ungrouped:
        print(f"ungrouped     : {n_ungrouped:,} photo(s) have no source/session and "
              f"fall back to EXIF/filename")

    # ------------------------------------------------------------ statistics
    per_class = {}
    for cls in sorted(table):
        r = report.get(cls, {})
        have = sum(len(p) for p in table[cls].values())
        tgt = targets.get(cls, 0)
        per_class[cls] = {
            "photos": have,
            "groups": r.get("sessions", len(table[cls])),
            "splittable": r.get("splittable", False),
            "train": r.get("train", have), "val": r.get("val", 0), "test": r.get("test", 0),
            "target": tgt,
            "progress_pct": round(have / tgt * 100, 3) if tgt else None,
        }

    attr_dist = {a: {} for a in ATTRS}
    filled, blank = collections.Counter(), collections.Counter()
    for p in grouped:
        row = meta.get(str(p.resolve()))
        if not row:
            continue
        for a in ATTRS:
            v = (row.get(a) or "").strip()
            (filled if v else blank)[a] += 1
            if v:
                attr_dist[a][v] = attr_dist[a].get(v, 0) + 1

    stats = {
        "generated_from": str(root),
        "raw_fingerprint": fingerprint(root),
        "metadata": str(meta_path) if meta else None,
        "photos": n_total,
        "classes": len(table),
        "classes_empty": sorted(c for c in classes
                                if not table.get(c) and c != "UNKNOWN"),
        "groups": len(set(g for v in rows.values() for g, _, _ in v)),
        "split_policy": "whole groups dealt per class; seed %d; val %.2f; test %.2f"
                        % (args.seed, args.val_frac, args.test_frac),
        "splits": {s: len(v) for s, v in rows.items()},
        "per_class": per_class,
        "unsplittable_classes": unsplittable,
        "photos_without_source_session": n_ungrouped,
        "attribute_distributions": attr_dist,
        "attribute_fill_rates": {
            a: {"filled": filled[a], "blank": blank[a],
                "pct": round(filled[a] / (filled[a] + blank[a]) * 100, 1)
                if (filled[a] + blank[a]) else 0.0}
            for a in ATTRS},
        "targets": {"total": total_target,
                    "counted": sum(v["photos"] for c, v in per_class.items()
                                   if c in classes and c != "UNKNOWN"),
                    "progress_pct": round(
                        sum(v["photos"] for c, v in per_class.items()
                            if c in classes and c != "UNKNOWN")
                        / total_target * 100, 3) if total_target else None},
        "note": "Counting rule: unique raw photos only. Augmented/derived images are "
                "never counted and never appear in this manifest.",
    }

    if not args.write:
        print(f"\ndry run - pass --write to produce {out}/manifest.csv and "
              f"dataset_statistics.json")
        return 0

    out.mkdir(parents=True, exist_ok=True)
    with (out / "manifest.csv").open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "class", "group", "split", "source", "session",
                    "view", "lighting"])
        for s in ("train", "val", "test"):
            for group, cls, p in sorted(rows[s], key=lambda r: str(r[2])):
                row = meta.get(str(p.resolve()), {})
                w.writerow([str(Path(p).relative_to(root)), cls, group, s,
                            row.get("source", ""), row.get("session", ""),
                            row.get("view", ""), row.get("lighting", "")])
    (out / "dataset_statistics.json").write_text(json.dumps(stats, indent=2))
    (out / "README.txt").write_text(
        "Derived data - do not edit, do not commit.\n\n"
        f"Source      : {root}\n"
        f"Fingerprint : {stats['raw_fingerprint']}  (hash of relative path + size)\n"
        "Regenerate  : python3 tools/dataset/split_dataset.py --write\n\n"
        "manifest.csv columns: file, class, group, split, source, session, view, lighting.\n"
        "The split is dealt by GROUP (source|session), never by photo, so no group\n"
        "straddles train/val/test. If you edit raw/ or metadata.csv, the fingerprint\n"
        "above no longer matches and you must regenerate.\n")

    print(f"\nwrote {out/'manifest.csv'} ({n_total:,} rows)")
    print(f"wrote {out/'dataset_statistics.json'}")
    print(f"wrote {out/'README.txt'}")

    if args.link:
        made = 0
        for s in ("train", "val", "test"):
            base = out / s
            base.mkdir(parents=True, exist_ok=True)
            for _g, cls, p in rows[s]:
                dst = base / cls
                dst.mkdir(parents=True, exist_ok=True)
                link = dst / p.name
                if link.is_symlink() or link.exists():
                    continue
                rel = Path("../../..") / p.relative_to(root.parent)
                try:
                    link.symlink_to(rel)
                    made += 1
                except OSError as e:
                    print(f"  ! symlink failed ({e}); use manifest.csv instead")
                    break
        print(f"linked {made:,} file(s) into {out}/{{train,val,test}}/ "
              f"(symlinks, no extra disk)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
