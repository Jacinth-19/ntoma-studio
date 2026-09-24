#!/usr/bin/env python3
"""Set up the Ghanaian-fabric dataset tree and its metadata sidecar.

Three jobs, all idempotent:

  --apply              create exactly the 26 schema class folders under --data
  --metadata-template  seed metadata.csv with one blank row per photo on disk
  --check              validate metadata.csv against the tree (structure is law)

The folder tree stays flat at the 26 classes in label_schema.json. Everything
finer-grained (technique, material, colour, region, brand family, view) lives in
metadata.csv as COLUMNS, not folders - see docs/DATASET_SCHEMA.md for why.

Dry run by default: creating folders prints what it would do and exits 2, matching
fix_folder_names.py. Pass --apply to actually create them.

Usage:
  python3 tools/dataset/init_dataset.py                     # dry run
  python3 tools/dataset/init_dataset.py --apply             # create class folders
  python3 tools/dataset/init_dataset.py --metadata-template # seed metadata.csv
  python3 tools/dataset/init_dataset.py --check             # validate metadata.csv
"""
import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCHEMA = HERE / "label_schema.json"
EXTS = (".jpg", ".jpeg", ".png", ".webp")

# Structure is enforced; vocabulary is guidance. Ghana's market will outrun any
# word list, so unknown *values* warn while a broken *shape* fails.
COLUMNS = [
    "file", "class", "source", "session", "region", "town", "technique",
    "material", "brand_family", "colour_primary", "colour_secondary",
    "pattern_scale", "view", "lighting", "occasion", "quality", "notes", "group",
]
REQUIRED_BLANK_INTOLERANT = ("file", "class", "source", "session")

VOCAB = {
    "technique": [
        "handwoven", "strip_woven", "hand_stamped", "hand_printed", "wax_resist_dye",
        "tied_resist_dye", "batik_wax", "screen_print", "roller_print", "machine_print",
        "knitted", "woven", "embroidered", "laminated", "printed_imitation", "unknown",
    ],
    "material": [
        "cotton", "silk", "rayon_viscose", "polyester", "acetate", "linen", "wool",
        "blend", "lace_net", "jacquard", "metallic", "unknown",
    ],
    "pattern_scale": ["small", "medium", "large", "allover", "border_only", "unknown"],
    "view": [
        "flat_lay", "folded", "rolled", "close_up", "macro_texture", "hanging",
        "worn_by_person", "dress", "shirt", "skirt", "trousers", "kaba_and_slit",
        "smock", "market", "shop", "indoor", "outdoor", "low_light", "bright_light",
        "partial_occlusion",
    ],
    "lighting": [
        "natural_shade", "natural_sun", "indoor_tungsten", "indoor_fluorescent",
        "indoor_led", "mixed", "flash", "unknown",
    ],
    "occasion": [
        "everyday", "ceremonial", "funeral", "wedding", "festival", "worship",
        "office", "casual", "formal", "unknown",
    ],
    "quality": ["high", "medium", "low"],
}

SESSION_RE = re.compile(r"^\d{4}-\d{2}-\d{2}(_(am|pm|mid|dusk|night))?$")
EXAMPLE_MARK = "EXAMPLE_ROW"


def schema_classes():
    data = json.loads(SCHEMA.read_text())
    return list(data["classes"])


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", str(text).strip().lower()).strip("_")


def group_key(row):
    """The unit that must never straddle train/val/test."""
    explicit = (row.get("group") or "").strip()
    if explicit:
        return explicit
    return f"{slug(row.get('source'))}|{(row.get('session') or '').strip()}"


def images_on_disk(root):
    return sorted(
        p for p in root.rglob("*")
        if p.is_file() and p.suffix.lower() in EXTS and not p.name.startswith(".")
    )


# --------------------------------------------------------------------- apply

def cmd_apply(root, do_it):
    classes = schema_classes()
    root.mkdir(parents=True, exist_ok=True)
    existing = {d.name for d in root.iterdir() if d.is_dir()}
    todo = [c for c in classes if c not in existing]

    print("=" * 72)
    print("Class-folder initialiser")
    print("=" * 72)
    print(f"root     : {root}")
    print(f"schema   : {len(classes)} classes")
    print(f"present  : {len([c for c in classes if c in existing])}")
    print(f"to create: {len(todo)}")
    for c in todo:
        print(f"   + {c}")

    offschema = sorted(existing - set(classes))
    if offschema:
        print(f"\n  note: {len(offschema)} existing folder(s) are not schema classes "
              f"and are left alone:")
        for d in offschema:
            n = len([f for f in (root / d).rglob("*") if f.is_file()])
            print(f"     {d}  ({n} file(s))")

    if not todo:
        print("\nnothing to do - tree already matches the schema.")
        return 0
    if not do_it:
        print("\ndry run - pass --apply to create these folders.")
        return 2
    for c in todo:
        (root / c).mkdir(exist_ok=True)
    print(f"\ncreated {len(todo)} folder(s).")
    return 0


# --------------------------------------------------------- metadata template

def cmd_template(root, do_it, force):
    out = root / "metadata.csv"
    classes = set(schema_classes())
    imgs = images_on_disk(root)

    print("=" * 72)
    print("Metadata template")
    print("=" * 72)
    print(f"root            : {root}")
    print(f"photos on disk  : {len(imgs)}")
    print(f"output          : {out}")

    offschema = Counter(
        p.parent.name for p in imgs if p.parent != root and p.parent.name not in classes
    )
    if offschema:
        print(f"\n  note: photos sit in {len(offschema)} folder(s) that are not schema "
              f"classes; they still get a row, with class left as the folder name so")
        print("        fix_folder_names.py / a human decision can resolve them:")
        for d, n in offschema.most_common():
            print(f"     {d}  ({n})")

    if out.exists() and not force:
        print(f"\n{out} already exists - refusing to overwrite. Pass --force to replace "
              f"(this discards any fields already filled in).")
        return 1
    if not do_it:
        print(f"\ndry run - would write {len(imgs)} blank row(s) + header. "
              f"Pass --metadata-template (with --apply) to write.")
        return 2

    with out.open("w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS)
        w.writeheader()
        for p in imgs:
            w.writerow({
                "file": str(p.relative_to(root)),
                "class": p.parent.name,
                "notes": "fill source + session first - empty ones fail --check",
            })
    print(f"\nwrote {out} with {len(imgs)} row(s).")
    print("Fill source and session at minimum; everything else is optional but valuable.")
    return 0


# --------------------------------------------------------------------- check

def cmd_check(root, path, quiet=False):
    """Validate metadata.csv against the tree. Returns 0 (ok) or 1 (errors).

    quiet=True prints only the summary counts and the errors - used by
    check_dataset.py, which embeds this check in a wider report.
    """
    classes = set(schema_classes())
    errors, warnings = [], []

    print("=" * 72)
    print("Metadata check")
    print("=" * 72)
    print(f"metadata: {path}")
    if not path.exists():
        print(f"\nERROR: {path} does not exist. Create it with:\n"
              f"  python3 tools/dataset/init_dataset.py --metadata-template --apply",
              file=sys.stderr)
        return 1

    with path.open(newline="") as fh:
        reader = csv.DictReader(fh)
        header = reader.fieldnames or []
        rows = list(reader)

    missing_cols = [c for c in COLUMNS if c not in header]
    extra_cols = [c for c in header if c not in COLUMNS]
    if missing_cols:
        errors.append(f"missing column(s): {missing_cols}")
    if extra_cols:
        errors.append(f"unexpected column(s): {extra_cols} (fix the header or add them "
                      f"to COLUMNS in init_dataset.py)")

    if missing_cols:
        for e in errors:
            print(f"   x {e}")
        return 1

    seen = Counter()
    unfilled, examples = [], []
    by_class = defaultdict(set)          # class -> {group keys}
    value_warn = defaultdict(set)
    disk = {str(p.relative_to(root)) for p in images_on_disk(root)}

    for i, row in enumerate(rows, start=2):
        f = (row.get("file") or "").strip()
        cls = (row.get("class") or "").strip()
        if not f:
            errors.append(f"line {i}: empty 'file'")
            continue
        seen[f] += 1
        if EXAMPLE_MARK in (row.get("notes") or ""):
            examples.append(f)

        if not (row.get("source") or "").strip() or not (row.get("session") or "").strip():
            unfilled.append(f)
            continue                              # cannot group yet; skip group maths

        if cls and cls not in classes:
            errors.append(f"line {i}: class '{cls}' is not in label_schema.json")
        parent = Path(f).parent.name
        if cls and parent and cls != parent:
            errors.append(f"line {i}: class '{cls}' does not match folder '{parent}'")
        if f not in disk:
            errors.append(f"line {i}: no such image: {f}")

        s = (row.get("session") or "").strip()
        if not SESSION_RE.match(s):
            # An error, not a warning: session is half the split key, so a typo
            # (06oct2026 vs 2026-10-06) silently splits one session into two
            # groups and lets near-identical photos straddle train/val.
            errors.append(f"line {i}: session '{s}' is not yyyy-mm-dd[_am|_pm]")

        by_class[cls].add(group_key(row))

        for col, allowed in VOCAB.items():
            v = (row.get(col) or "").strip()
            if v and v not in allowed:
                value_warn[col].add(v)

    dupes = {f: n for f, n in seen.items() if n > 1}
    if dupes:
        errors.append(f"{len(dupes)} file(s) appear more than once, e.g. "
                      f"{list(dupes)[:3]}")
    if examples:
        errors.append(f"{len(examples)} example row(s) still present - delete the "
                      f"'{EXAMPLE_MARK}' row(s) from the template")
    if unfilled:
        errors.append(f"{len(unfilled)} row(s) have blank source/session and cannot be "
                      f"grouped for the split (source + session are the split key)")

    print(f"rows            : {len(rows)}")
    print(f"photos on disk  : {len(disk)}")
    print(f"with source+session: {len(rows) - len(unfilled)}")
    print(f"classes covered : {len(by_class)}/{len(classes)}")

    if not quiet:
        print("\n  groups per class (>=3 is the minimum to hold out validation):")
        for c in sorted(by_class):
            n = len(by_class[c])
            flag = "" if n >= 3 else "   <-- cannot be split honestly"
            print(f"     {c:<22} {n:>3} group(s){flag}")
        missing = sorted(classes - set(by_class))
        if missing:
            print(f"     not yet in metadata ({len(missing)}): {', '.join(missing[:6])}"
                  f"{' ...' if len(missing) > 6 else ''}")

    for col, vals in sorted(value_warn.items()):
        warnings.append(f"{col}: value(s) outside the suggested vocabulary: "
                        f"{sorted(vals)[:6]}")

    if warnings and not quiet:
        print(f"\n{len(warnings)} warning(s):")
        for w in warnings[:30]:
            print(f"   ! {w}")
    if errors:
        print(f"\n{len(errors)} error(s):")
        for e in errors[:30]:
            print(f"   x {e}")
        return 1

    print("\nmetadata OK.")
    return 0


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=str(HERE / "raw"),
                    help="dataset root (default: tools/dataset/raw)")
    ap.add_argument("--metadata", default=None,
                    help="metadata csv (default: <data>/metadata.csv)")
    ap.add_argument("--apply", action="store_true", help="actually make changes")
    ap.add_argument("--metadata-template", action="store_true",
                    help="write a blank metadata.csv seeded from photos on disk")
    ap.add_argument("--check", action="store_true", help="validate metadata.csv")
    ap.add_argument("--force", action="store_true",
                    help="overwrite an existing metadata.csv")
    args = ap.parse_args()

    root = Path(args.data).resolve()
    meta = Path(args.metadata) if args.metadata else root / "metadata.csv"

    if not SCHEMA.exists():
        print(f"ERROR: {SCHEMA} not found", file=sys.stderr)
        return 1
    if not root.is_dir() and not args.metadata_template:
        print(f"ERROR: dataset root not found: {root}", file=sys.stderr)
        return 1

    if args.check:
        return cmd_check(root, meta)
    if args.metadata_template:
        return cmd_template(root, args.apply, args.force)
    return cmd_apply(root, args.apply)


if __name__ == "__main__":
    sys.exit(main())
