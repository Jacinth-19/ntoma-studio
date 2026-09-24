#!/usr/bin/env python3
"""Track collection progress against tools/dataset/collection_plan.json.

Answers "how far are we from 50,000?" without the two ways that number lies:

1. **Augmented images are not data.** `augment.py` turns one photo into 10-16.
   Counting those toward the target multiplies information that is already in the
   corpus instead of adding any. This tool counts `raw/` only and reports the
   `augmented/` count separately as a warning.

2. **A class fed from one market day is not covered.** Photos taken in one session
   share lighting, backdrop and stock. A model that has only seen one session of
   GONJA cannot generalise. Using the capture date already embedded in app-exported
   filenames (`<CLASS>_<yyyyMMdd_HHmmss>.jpg`), this tool reports the distinct
   capture dates per class and flags any class where a single date dominates.

Read-only. Never writes, never moves files.

Usage:
    python3 tools/dataset/track_collection.py            # human report
    python3 tools/dataset/track_collection.py --json     # machine-readable
    python3 tools/dataset/track_collection.py --verbose  # per-class capture dates
Exit: 0 = plan complete, 1 = below target, 2 = below target and honesty issues found
"""
import argparse
import collections
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
AUGMENTED = HERE / "augmented"
PLAN = HERE / "collection_plan.json"
SCHEMA = HERE / "label_schema.json"

EXTS = (".jpg", ".jpeg", ".png", ".webp")
DATE_RE = re.compile(r"(\d{4})(\d{2})(\d{2})")

WARNINGS = []


def md5(p: Path) -> str:
    h = hashlib.md5()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def count_unique(d: Path):
    """Unique photos by md5, plus the capture dates parsed from filenames."""
    files = [p for p in d.glob("*") if p.suffix.lower() in EXTS] if d.is_dir() else []
    seen, unique, dates = set(), 0, collections.Counter()
    for p in files:
        h = md5(p)
        if h in seen:
            continue
        seen.add(h)
        unique += 1
        m = DATE_RE.search(p.stem)
        if m:
            dates[f"{m.group(1)}-{m.group(2)}-{m.group(3)}"] += 1
    return {"unique": unique, "total_files": len(files),
            "exact_duplicates": len(files) - unique, "dates": dates}


def bar(frac, width=22):
    filled = int(round(frac * width))
    return "[" + "#" * filled + "." * (width - filled) + "]"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--verbose", action="store_true", help="show capture dates per class")
    args = ap.parse_args()

    if not PLAN.exists() or not SCHEMA.exists():
        print("ERROR: collection_plan.json / label_schema.json not found next to this script.",
              file=sys.stderr)
        return 2

    plan = json.loads(PLAN.read_text())
    schema = json.loads(SCHEMA.read_text())
    targets = plan["targets"]
    total_target = plan["total_target"]
    details = schema["class_details"]
    tier_of = {c: details.get(c, {}).get("tier", 3) for c in targets}

    have, rows = {}, []
    for cls in targets:
        info = count_unique(RAW / cls)
        have[cls] = info["unique"]
        rows.append((cls, info["unique"], targets[cls], info, tier_of[cls]))

    total_have = sum(have.values())

    # Unknown / stray folders in raw/ that the schema does not define.
    strays, review = {}, {}
    if RAW.is_dir():
        for d in sorted(RAW.iterdir()):
            if not d.is_dir():
                continue
            if d.name == "_REVIEW":
                # ingest.py parks ambiguous legacy labels here rather than guessing.
                for sub in sorted(d.iterdir()):
                    if sub.is_dir():
                        n = len([p for p in sub.glob("*") if p.suffix.lower() in EXTS])
                        if n:
                            review[sub.name] = n
                continue
            if d.name not in targets:
                n = len([p for p in d.glob("*") if p.suffix.lower() in EXTS])
                if n:
                    strays[d.name] = n
                    WARNINGS.append(f"raw/{d.name}/ holds {n} photos but is not a class in "
                                    f"label_schema.json - they will never be trained on")
    if review:
        n = sum(review.values())
        WARNINGS.append(f"{n} photo(s) awaiting human relabelling in raw/_REVIEW/ "
                        f"({', '.join(f'{k}:{v}' for k, v in sorted(review.items()))}) - "
                        f"excluded from progress until re-filed")
    if strays:
        print(f"!! stray folders in raw/ not in the schema: {strays}\n")

    aug_total = 0
    if AUGMENTED.is_dir():
        aug_total = sum(1 for p in AUGMENTED.rglob("*") if p.suffix.lower() in EXTS)
    if aug_total > total_have and aug_total > 0:
        WARNINGS.append(f"augmented/ holds {aug_total:,} images vs {total_have:,} raw - the "
                        f"larger number is NOT the dataset size. Check no report is quoting it.")

    exact_dupes = sum(r[3]["exact_duplicates"] for r in rows)
    if exact_dupes:
        WARNINGS.append(f"{exact_dupes:,} exact-duplicate files in raw/ - run ingest.py which "
                        f"rejects them at the door")

    # Session concentration: a class fed from one or two market days will not generalise.
    concentrated = []
    for cls, n, tgt, info, _ in rows:
        if n >= 50 and len(info["dates"]) >= 1:
            top = info["dates"].most_common(1)[0]
            share = top[1] / n
            if share >= 0.40:
                concentrated.append((cls, top[0], round(share, 2), len(info["dates"])))
    for cls, date, share, ndates in concentrated:
        WARNINGS.append(f"{cls}: {share:.0%} of photos come from a single capture date "
                        f"({date}, {ndates} date(s) total) - session concentration risks "
                        f"generalisation failure and train/val leakage")

    if args.json:
        print(json.dumps({
            "total_have": total_have, "total_target": total_target,
            "fraction": round(total_have / total_target, 4),
            "remaining": total_target - total_have,
            "have": have, "targets": targets,
            "augmented_images": aug_total,
            "exact_duplicates": exact_dupes,
            "strays": strays,
            "awaiting_review": review,
            "session_concentration": [
                {"class": c, "date": d, "share": s, "distinct_dates": n}
                for c, d, s, n in concentrated
            ],
            "warnings": WARNINGS,
        }, indent=2))
        return 1 if total_have < total_target else (2 if WARNINGS else 0)

    print("=" * 74)
    print(f"Ntoma collection progress — {plan['plan']} (scope: {plan.get('scope','')})")
    print("=" * 74)
    pct = total_have / total_target
    print(f"\n{bar(pct)}  {total_have:,} / {total_target:,} unique raw photos ({pct:.1%})")
    if aug_total:
        print(f"{' ' * 24}  augmented/ holds {aug_total:,} images — NOT counted, never report "
              f"these as dataset size")
    print(f"{' ' * 24}  remaining: {total_target - total_have:,}")

    for tier in (1, 2, 3, 0):
        members = [r for r in rows if r[4] == tier]
        if not members:
            continue
        th = sum(m[1] for m in members)
        tt = sum(m[2] for m in members)
        meta = plan["tiers"][str(tier)]
        print(f"\n-- Tier {tier} · {meta['label']} · {th:,}/{tt:,} ({th/tt:.0%})")
        for cls, n, tgt, info, _ in sorted(members, key=lambda r: r[1] / r[2] if r[2] else 0):
            frac = n / tgt if tgt else 0
            flag = "DONE" if n >= tgt else ("EMPTY" if n == 0 else "")
            dates = f" {len(info['dates'])}d" if info["dates"] else ""
            print(f"   {cls:20s} {bar(frac, 18)} {n:5d}/{tgt:<5d} {info['unique']/tgt:>5.0%} "
                  f"{flag}{dates}")
            if args.verbose and info["dates"]:
                top = ", ".join(f"{d}:{c}" for d, c in info["dates"].most_common(6))
                print(f"   {'':20s}   sessions: {top}")

    days = (total_target - total_have) / plan["throughput"]["unique_photos_per_collector_day"]
    print(f"\n-- Throughput")
    print(f"   remaining collector-days: {days:,.0f} "
          f"(at {plan['throughput']['unique_photos_per_collector_day']} unique photos/day)")
    for cfg in plan["throughput"]["configurations"]:
        weeks = days / cfg["collectors"] / 5
        print(f"   {cfg['collectors']:>2} collectors -> {weeks:,.1f} calendar weeks "
              f"of field work")

    if WARNINGS:
        print(f"\n-- Honesty / quality warnings ({len(WARNINGS)})")
        for w in WARNINGS:
            print(f"   ! {w}")

    print("\n" + "=" * 74)
    if total_have >= total_target and not WARNINGS:
        print("PLAN COMPLETE — ready for the held-out evaluation described in the plan.")
    elif total_have >= total_target:
        print("TARGET MET, but resolve the warnings before claiming accuracy.")
    else:
        print(f"IN PROGRESS — {total_target - total_have:,} photos to go.")
    return 1 if total_have < total_target else (2 if WARNINGS else 0)


if __name__ == "__main__":
    sys.exit(main())
