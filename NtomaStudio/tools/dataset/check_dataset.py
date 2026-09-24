#!/usr/bin/env python3
"""Health-check a batch of fabric photos before it joins the training set.

Run this on every batch. A defect caught at 200 photos is a note; at 50,000 it
is a re-shoot. Every check here exists because it caught something real on the
first 83 photos - see docs/DATASET_SCHEMA.md and the findings it came from.

Checks, in order:

  integrity     decode every file, flag corruption, truncation, zero-byte files
  duplicates    exact (MD5) and perceptual (dHash). Cross-class near-duplicates
                are BLOCKERS: the same photo under two labels is a label
                conflict the model cannot resolve.
  resolution    short-edge distribution and anything below --min-edge
  exposure      dark frames, near-featureless frames, blown highlights
  presentation  studio/catalogue product shots and possible stock credit bars.
                Both are proxies for "not photographed by us in the field" and
                are reported for a human to judge - watermarks are pixels, so
                this is the only way to catch them without metadata.
  coverage      photos per class against collection_plan.json
  metadata      embeds init_dataset.py --check: is the split key actually filled

Exit codes:
  0  no blockers (warnings may still be listed)
  1  blockers found
  2  usage / IO error

Usage:
  python3 tools/dataset/check_dataset.py
  python3 tools/dataset/check_dataset.py --json /tmp/health.json
  python3 tools/dataset/check_dataset.py --strict        # warnings fail too
"""
import argparse
import collections
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
SCHEMA = HERE / "label_schema.json"
PLAN = HERE / "collection_plan.json"
EXTS = (".jpg", ".jpeg", ".png", ".webp")

# Thresholds. Each is justified by a measurement on the first 83 photos; they
# are deliberately loose, because this is a triage tool and a false alarm costs
# a glance while a missed defect costs a re-shoot.
MIN_EDGE = 224          # below this, 224px training input is upscaling noise
DUPE_DISTANCE = 8       # dHash Hamming distance counted as "same cloth, same shot"
DARK_LUM = 60.0         # mean luminance below this is hard to judge colour on
FLAT_DETAIL = 3.0       # near-featureless frame (satin/chiffon sit near this)
CLIP_PCT = 8.0          # % of pixels >= 250 in every channel
WHITE_BORDER_PCT = 50.0 # % of border ring at >= 245 - a studio backdrop
BAR_LUM = 70.0          # credit-bar candidate: flat band this dark ...
BAR_JUMP = 45.0         # ... this much darker than the body ...
BAR_INK_LO, BAR_INK_HI = 0.3, 12.0   # ... carrying a little bright text

try:
    _bitcount = np.bitwise_count
except AttributeError:                                    # numpy < 2.0
    def _bitcount(a):
        return np.vectorize(lambda x: bin(int(x)).count("1"), otypes=[np.uint8])(a)


def schema_classes():
    return list(json.loads(SCHEMA.read_text())["classes"])


def plan_targets():
    if not PLAN.exists():
        return {}, 0
    plan = json.loads(PLAN.read_text())
    return plan.get("targets", {}), plan.get("total_target", 0)


def dhash(gray, size=8):
    """8x8 difference hash -> a 64-bit int. Cheap perceptual fingerprint."""
    small = np.asarray(
        Image.fromarray(gray).resize((size + 1, size), Image.LANCZOS), dtype=np.int16)
    bits = (small[:, 1:] > small[:, :-1]).ravel()
    return int.from_bytes(np.packbits(bits).tobytes(), "big")


def analyze(path):
    """Decode once, derive every metric. Raises on unreadable files."""
    raw = path.read_bytes()
    with Image.open(path) as im:
        fmt, mode, size = im.format, im.mode, im.size
        rgb = np.asarray(im.convert("RGB"), dtype=np.uint8)
    a = rgb.astype(np.float32)
    h, w, _ = a.shape
    gray = a.mean(2)

    # detail: mean absolute second difference - "is there anything to look at"
    detail = float(np.abs(np.diff(gray, 2, axis=0)).mean()
                   + np.abs(np.diff(gray, 2, axis=1)).mean())

    # border ring, for backdrop detection
    b = max(4, int(min(h, w) * 0.06))
    ring = np.concatenate([a[:b].reshape(-1, 3), a[-b:].reshape(-1, 3),
                           a[:, :b].reshape(-1, 3), a[:, -b:].reshape(-1, 3)])
    white_border = float((ring >= 245).all(1).mean() * 100)
    med = np.median(ring, 0)
    uniform = float((np.abs(ring - med).max(1) < 14).mean())
    border_lum = float(med.mean())
    seamless = uniform > 0.75 and border_lum > 150

    # credit-bar candidate: a flat band much darker than the body with a little ink
    bar = {"candidate": False}
    bh = max(5, int(h * 0.07))
    for edge, band in (("bottom", a[-bh:]), ("top", a[:bh])):
        bl = float(band.reshape(-1, 3).mean())
        ink = float((band >= 170).all(2).mean() * 100)
        body = float((a[:-bh] if edge == "bottom" else a[bh:]).reshape(-1, 3).mean())
        if bl < BAR_LUM and body - bl > BAR_JUMP and BAR_INK_LO < ink < BAR_INK_HI:
            bar = {"candidate": True, "edge": edge,
                   "band_lum": round(bl, 1), "ink_pct": round(ink, 2)}
            break

    return {
        "bytes": len(raw), "format": fmt, "mode": mode, "size": [w, h],
        "md5": hashlib.md5(raw).hexdigest(),
        "dhash": dhash(gray.astype(np.uint8)),
        "lum": float(gray.mean()), "detail": detail,
        "clipped_pct": float((a >= 250).all(2).mean() * 100),
        "colour_std": float(max(a[:, :, i].std() for i in range(3))),
        "white_border": white_border, "seamless": seamless,
        "studio": white_border > WHITE_BORDER_PCT or seamless,
        "bar": bar,
    }


def find_near_dupes(hashes, distance):
    """Chunked XOR/popcount over 64-bit hashes. O(n^2) but numpy-speed."""
    n = len(hashes)
    if n < 2:
        return []
    h = np.array(hashes, dtype=np.uint64)
    pairs = []
    step = max(1, 512 if n > 8192 else n)
    for start in range(0, n, step):
        block = h[start:start + step]
        d = _bitcount(block[:, None] ^ h[None, :]).astype(np.uint8)
        rows, cols = np.where(d <= distance)
        for r, c in zip(rows, cols):
            i, j = start + int(r), int(c)
            if i < j:
                pairs.append((int(d[r, c]), i, j))
    # de-duplicate: the chunked loop can only produce i<j once per pair
    return pairs


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=str(HERE / "raw"))
    ap.add_argument("--metadata", default=None)
    ap.add_argument("--min-edge", type=int, default=MIN_EDGE)
    ap.add_argument("--dupe-distance", type=int, default=DUPE_DISTANCE,
                    help="dHash Hamming distance counted as a near-duplicate")
    ap.add_argument("--json", default=None, help="write the report as JSON here")
    ap.add_argument("--strict", action="store_true",
                    help="treat warnings as blockers (exit 1)")
    ap.add_argument("--quiet", action="store_true", help="summary + blockers only")
    args = ap.parse_args()

    root = Path(args.data).resolve()
    if not root.is_dir():
        print(f"ERROR: dataset root not found: {root}", file=sys.stderr)
        return 2

    classes = schema_classes()
    known = set(classes)
    targets, total_target = plan_targets()

    files, broken, offschema = [], [], collections.Counter()
    for d in sorted(p for p in root.iterdir() if p.is_dir()):
        if d.name.startswith("_"):                    # _REVIEW from ingest.py
            continue
        found = [f for f in sorted(d.rglob("*")) if f.is_file()]
        for f in found:
            if f.suffix.lower() not in EXTS:
                if d.name in known:
                    broken.append((str(f.relative_to(root)), "not an image file"))
                continue
            if d.name not in known:
                offschema[d.name] += 1
            files.append(f)

    print("=" * 74)
    print("Dataset health check")
    print("=" * 74)
    print(f"root: {root}")

    # ---------------------------------------------------------- integrity
    metrics, corrupt, tiny, dark, flat, clipped, studio, bars = {}, [], [], [], [], [], [], []
    for f in files:
        rel = str(f.relative_to(root))
        try:
            m = analyze(f)
        except Exception as e:
            corrupt.append((rel, type(e).__name__, str(e)[:60]))
            continue
        metrics[rel] = m
        if m["bytes"] == 0:
            corrupt.append((rel, "ZeroByte", "0 bytes"))
        if min(m["size"]) < args.min_edge:
            tiny.append((rel, m["size"]))
        if m["lum"] < DARK_LUM:
            dark.append((rel, m["lum"]))
        if m["detail"] < FLAT_DETAIL:
            flat.append((rel, m["detail"]))
        if m["clipped_pct"] > CLIP_PCT:
            clipped.append((rel, m["clipped_pct"]))
        if m["studio"]:
            studio.append((rel, round(m["white_border"], 1), m["seamless"]))
        if m["bar"]["candidate"]:
            bars.append((rel, m["bar"]["edge"], m["bar"]["band_lum"], m["bar"]["ink_pct"]))

    print(f"\n[integrity]   {len(metrics)}/{len(files)} decoded")
    print(f"              {len(corrupt)} corrupt/zero-byte   {len(broken)} non-image file(s)")

    # --------------------------------------------------------- duplicates
    rels = sorted(metrics)
    by_md5 = collections.defaultdict(list)
    for r in rels:
        by_md5[metrics[r]["md5"]].append(r)
    exact = {m: v for m, v in by_md5.items() if len(v) > 1}

    pairs = find_near_dupes([metrics[r]["dhash"] for r in rels], args.dupe_distance)
    cross, same = [], []
    for d, i, j in pairs:
        a, b = rels[i], rels[j]
        (cross if a.split("/")[0] != b.split("/")[0] else same).append((d, a, b))
    print(f"[duplicates]  {sum(len(v) - 1 for v in exact.values())} exact   "
          f"{len(same)} near within-class   {len(cross)} near CROSS-CLASS")

    # --------------------------------------------------------- resolution
    dims = collections.Counter(tuple(metrics[r]["size"]) for r in rels)
    shorts = sorted(min(metrics[r]["size"]) for r in rels)
    print(f"[resolution]  short edge {shorts[0] if shorts else '-'}.."
          f"{shorts[-1] if shorts else '-'}px, {len(dims)} distinct sizes, "
          f"{len(tiny)} below {args.min_edge}px")

    # ----------------------------------------------------------- exposure
    print(f"[exposure]    {len(dark)} dark (<{DARK_LUM:.0f} lum)   "
          f"{len(flat)} near-featureless (<{FLAT_DETAIL} detail)   "
          f"{len(clipped)} blown highlights (>{CLIP_PCT:.0f}%)")

    # ------------------------------------------------------- presentation
    print(f"[presentation] {len(studio)} studio/catalogue shot(s)   "
          f"{len(bars)} possible credit bar(s)")

    # ----------------------------------------------------------- coverage
    per_class = collections.Counter(Path(r).parent.name for r in rels)
    counted = sum(n for c, n in per_class.items() if c in known)
    empty = [c for c in classes if per_class.get(c, 0) == 0 and c != "UNKNOWN"]
    print(f"[coverage]    {counted:,} counted of {total_target:,} "
          f"({counted / total_target * 100:.2f}%)   {len(empty)} schema class(es) empty "
          f"(UNKNOWN excluded)")

    # ----------------------------------------------------------- metadata
    print("\n[metadata]    validating with init_dataset.py --check")
    sys.path.insert(0, str(HERE))
    meta_rc = 0
    try:
        import init_dataset
        meta_path = Path(args.metadata) if args.metadata else root / "metadata.csv"
        meta_rc = init_dataset.cmd_check(root, meta_path, quiet=True)
    except Exception as e:
        print(f"   ! could not run the metadata check: {type(e).__name__}: {e}")
        meta_rc = 1

    # ------------------------------------------------------------ verdict
    blockers, warnings = [], []

    if corrupt:
        blockers.append(f"{len(corrupt)} file(s) will not decode: {corrupt[:5]}")
    if broken:
        blockers.append(f"{len(broken)} non-image file(s) inside a class folder: "
                        f"{broken[:5]}")
    if offschema:
        n = sum(offschema.values())
        blockers.append(f"{n} photo(s) in folder(s) that are not schema classes "
                        f"(never trained on, refused by the packager): "
                        f"{dict(offschema)}")
    if cross:
        blockers.append(f"{len(cross)} near-duplicate pair(s) across DIFFERENT "
                        f"classes - the same cloth under two labels: "
                        f"{[(a, b, d) for d, a, b in cross[:4]]}")
    if meta_rc != 0:
        blockers.append("metadata check failed (see above; run "
                        "init_dataset.py --check without --quiet for the detail)")

    if exact:
        warnings.append(f"{sum(len(v) - 1 for v in exact.values())} exact duplicate "
                        f"file(s): {[v for v in list(exact.values())[:3]]}")
    if same:
        warnings.append(f"{len(same)} near-duplicate pair(s) within a class "
                        f"(wasted capacity, same shot twice): "
                        f"{[(a, b) for _, a, b in same[:3]]}")
    if tiny:
        warnings.append(f"{len(tiny)} photo(s) below {args.min_edge}px short edge "
                        f"(upscaled at training): {tiny[:5]}")
    if studio:
        warnings.append(f"{len(studio)} studio/catalogue shot(s) - white or seamless "
                        f"backdrop. A model fed mostly these learns background, not "
                        f"cloth: {[s[0] for s in studio[:6]]}")
    if bars:
        warnings.append(f"{len(bars)} possible stock credit bar(s) - INSPECT BY EYE, "
                        f"stock photos are licensed, not ours: {[b[0] for b in bars]}")
    if dark:
        warnings.append(f"{len(dark)} very dark frame(s), hard to judge colour: "
                        f"{[(r, round(v)) for r, v in sorted(dark, key=lambda x: x[1])[:5]]}")
    if flat:
        warnings.append(f"{len(flat)} near-featureless frame(s): {[f[0] for f in flat]}")
    if clipped:
        warnings.append(f"{len(clipped)} frame(s) above {CLIP_PCT:.0f}% pure-white "
                        f"pixels - white backdrop or specular sheen on satin/velvet, "
                        f"so detail there is gone: "
                        f"{[(r, round(v)) for r, v in clipped[:5]]}")
    if empty:
        warnings.append(f"{len(empty)} schema class(es) have no photos: "
                        f"{', '.join(empty[:8])}{' ...' if len(empty) > 8 else ''}")
    under = sorted((c, (per_class.get(c, 0), targets[c])) for c in classes
                   if targets.get(c) and per_class.get(c, 0) < targets[c])
    if under:
        warnings.append(f"{len(under)} class(es) below plan target, e.g. "
                        + ", ".join(f"{c} {a}/{b}" for c, (a, b) in under[:5]))

    if not args.quiet:
        if blockers:
            print(f"\n-- BLOCKERS ({len(blockers)})")
            for b in blockers:
                print(f"   x {b}")
        if warnings:
            print(f"\n-- WARNINGS ({len(warnings)})")
            for w in warnings:
                print(f"   ! {w}")
        if not blockers and not warnings:
            print("\nno blockers and no warnings.")

    failed = bool(blockers) or (args.strict and bool(warnings))
    print("\n" + "=" * 74)
    if blockers:
        print(f"NOT READY — {len(blockers)} blocker(s), {len(warnings)} warning(s)")
    elif warnings and args.strict:
        print(f"NOT READY (--strict) — 0 blocker(s), {len(warnings)} warning(s)")
    elif warnings:
        print(f"USABLE WITH CAVEATS — 0 blockers, {len(warnings)} warning(s)")
    else:
        print("CLEAN — no blockers, no warnings")
    print("=" * 74)

    if args.json:
        Path(args.json).write_text(json.dumps({
            "root": str(root), "photos": len(files), "decoded": len(metrics),
            "counted": counted, "total_target": total_target,
            "per_class": dict(per_class),
            "corrupt": corrupt, "non_image": broken,
            "off_schema": dict(offschema),
            "exact_duplicates": {k: v for k, v in exact.items()},
            "near_duplicates_cross_class": [{"distance": d, "a": a, "b": bb}
                                            for d, a, bb in cross],
            "near_duplicates_within_class": [{"distance": d, "a": a, "b": bb}
                                             for d, a, bb in same],
            "below_min_edge": tiny, "dark": dark, "near_featureless": flat,
            "blown_highlights": clipped, "studio": studio, "credit_bar_candidates": bars,
            "empty_classes": empty, "blockers": blockers, "warnings": warnings,
            "metadata_check_exit": meta_rc,
        }, indent=2))
        print(f"wrote {args.json}")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
