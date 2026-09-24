#!/usr/bin/env python3
"""Train the Ntoma Ghanaian-fabric classifier, designed for Kaggle GPU.

Runs unchanged in three places:
  * Kaggle notebook  -> /kaggle/input/ntoma-fabric/raw   (GPU T4/P100, ~10-40 min)
  * local CPU smoke  -> tools/dataset/raw                (--smoke, minutes)
  * CI               -> any path via --data

Why this exists separately from train_starter.py: that script holds out one real
photo per class, which is leak-free but *not session-aware*. With 50,000 field photos
a class will span dozens of market days, and photos from one session share lighting,
backdrop and stock. Splitting them across train/val is leakage that inflates accuracy
and, worse, does not show up as an obvious bug. This script splits by SESSION.

Split policy (the important part):
  1. Every photo gets a session key: EXIF DateTimeOriginal, else the date in the
     filename (app exports `<CLASS>_<yyyyMMdd_HHmmss>.jpg`), else "unknown".
  2. Sessions - never individual photos - are dealt into train/val/test per class,
     so no session ever straddles two splits.
  3. A class with fewer than 3 sessions cannot be split honestly. It goes to train
     only, is reported as having no validation coverage, and is flagged in the
     metrics json. Training on it is fine; claiming an accuracy number for it is not.

Usage:
  python3 train_kaggle.py --data /kaggle/input/ntoma-fabric/raw --out /kaggle/working
  python3 train_kaggle.py --smoke            # tiny CPU run to prove the pipeline
"""
import argparse
import csv
import json
import os
import random
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")

import numpy as np
from PIL import Image, ExifTags

# ----------------------------------------------------------------- config

DEFAULTS = dict(
    size=224,
    batch=32,
    epochs_head=6,
    epochs_fine=18,
    lr_head=1e-3,
    lr_fine=2e-5,
    dropout=0.3,
    backbone="mobilenetv3small",   # mobilenetv3small | efficientnetlite0 | mobilenetv2
    seed=1337,
    val_frac=0.15,
    test_frac=0.15,
)

CRITICAL_PAIRS = [
    ("KENTE_ASHANTI", "KENTE_PRINT"),
    ("FUGU_BATAKARI", "GONJA"),
    ("WAX_REAL", "FANCY_PRINT"),
    ("BATIK", "TIEDYE"),
    ("KENTE_ASHANTI", "KETE_EWE"),
]

EXTS = (".jpg", ".jpeg", ".png", ".webp")


# ----------------------------------------------------------------- sessions

def exif_datetime(path: Path):
    try:
        with Image.open(path) as im:
            exif = im.getexif()
            if not exif:
                return None
            for tag, val in exif.items():
                if ExifTags.TAGS.get(tag) in ("DateTimeOriginal", "DateTime"):
                    return str(val).strip()
    except Exception:
        pass
    return None


def filename_date(path: Path):
    """Session date from an app-exported filename: <CLASS>_<yyyyMMdd_HHmmss>.jpg.

    Deliberately strict. An earlier version tried strptime on every underscore-separated
    chunk in reverse and took the first that parsed, which allowed a *time* chunk to be
    read as a bogus date (a lenient %Y matches 1-4 digits and %m/%d accept 1-digit
    values). Silently mis-grouping sessions corrupts the train/val split, which inflates
    accuracy without ever looking like a bug - so this refuses anything that is not
    unambiguously a date.
    """
    stem = path.stem
    for part in stem.split("_"):
        if re.fullmatch(r"\d{8}", part):
            fmt = "%Y%m%d"
        elif re.fullmatch(r"\d{14}", part):
            fmt = "%Y%m%d%H%M%S"
        elif re.fullmatch(r"\d{4}-\d{2}-\d{2}", part):
            fmt = "%Y-%m-%d"
        else:
            continue
        try:
            d = datetime.strptime(part, fmt)
        except ValueError:
            continue
        if 2000 <= d.year <= 2100:
            return d.strftime("%Y-%m-%d")
    return None


def schema_classes():
    """The 26 class names, if label_schema.json is reachable.

    It is not on Kaggle (only this script and the images are uploaded), so a miss
    is normal and the off-schema check is simply skipped there.
    """
    for cand in (Path(__file__).resolve().parent.parent / "label_schema.json",
                 Path("label_schema.json")):
        if cand.exists():
            try:
                return set(json.loads(cand.read_text())["classes"])
            except Exception:
                return set()
    return set()


def load_metadata(root: Path, path=None):
    """Read the metadata sidecar (docs/DATASET_SCHEMA.md) into {abs path: row}.

    Metadata is the *primary* session source, not EXIF. Every photo delivered so
    far has had its EXIF stripped by one re-encoding pipeline, so EXIF grouping
    collapses the whole corpus into a single "unknown" session and the split
    silently degrades to "everything is train". The sidecar is what survives.

    The split group is `source|session`, joining two columns a collector can
    actually observe; either alone under-groups (one stall spans days, one day
    spans many stalls, and both share light and stock).
    """
    csv_path = Path(path) if path else root / "metadata.csv"
    if not csv_path.exists():
        return {}, csv_path

    meta, skipped = {}, 0
    with csv_path.open(newline="") as fh:
        for row in csv.DictReader(fh):
            rel = (row.get("file") or "").strip()
            source = (row.get("source") or "").strip()
            session = (row.get("session") or "").strip()
            if not rel:
                continue
            if not source or not session:
                skipped += 1                   # ungroupable; fall through to EXIF
                continue
            explicit = (row.get("group") or "").strip()
            group = explicit or f"{source}|{session}"
            row["_group"] = f"meta:{group}"
            meta[str((root / rel).resolve())] = row
    if skipped:
        print(f"  note: {skipped} metadata row(s) lack source/session and fall back "
              f"to EXIF/filename grouping")
    return meta, csv_path


def session_key(path: Path, meta=None) -> str:
    if meta:
        row = meta.get(str(Path(path).resolve()))
        if row and row.get("_group"):
            return row["_group"]
    ex = exif_datetime(path)
    if ex:
        return ex[:10]                      # date is the session proxy
    fd = filename_date(path)
    if fd:
        return fd
    return "unknown"


def index_dataset(root: Path, meta=None):
    """-> {class: {session: [paths]}} plus a per-class census."""
    table = defaultdict(lambda: defaultdict(list))
    for cls_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        if cls_dir.name.startswith("_"):     # _REVIEW etc from ingest.py
            continue
        for f in sorted(cls_dir.rglob("*")):
            if f.suffix.lower() in EXTS:
                table[cls_dir.name][session_key(f, meta)].append(f)
    return table


def split_sessions(table, rng, val_frac, test_frac):
    """Deal whole sessions per class. Never splits a session across sets."""
    out = {"train": [], "val": [], "test": []}
    report = {}
    for cls, sessions in sorted(table.items()):
        keys = sorted(sessions)
        rng.shuffle(keys)
        total = sum(len(sessions[k]) for k in keys)

        if len(keys) < 3:
            # Cannot be split honestly -> train only, and say so.
            out["train"] += [(p, cls) for k in keys for p in sessions[k]]
            report[cls] = {"sessions": len(keys), "photos": total,
                           "train": total, "val": 0, "test": 0,
                           "splittable": False,
                           "reason": f"only {len(keys)} session(s); needs >=3"}
            continue

        n_val = max(1, round(len(keys) * val_frac))
        n_test = max(1, round(len(keys) * test_frac))
        while n_val + n_test >= len(keys) and (n_val > 1 or n_test > 1):
            if n_val >= n_test and n_val > 1:
                n_val -= 1
            elif n_test > 1:
                n_test -= 1
            else:
                break
        val_keys, test_keys = keys[:n_val], keys[n_val:n_val + n_test]
        train_keys = keys[n_val + n_test:]

        for name, ks in (("train", train_keys), ("val", val_keys), ("test", test_keys)):
            out[name] += [(p, cls) for k in ks for p in sessions[k]]
        report[cls] = {
            "sessions": len(keys), "photos": total, "splittable": True,
            "train": sum(len(sessions[k]) for k in train_keys),
            "val": sum(len(sessions[k]) for k in val_keys),
            "test": sum(len(sessions[k]) for k in test_keys),
            "session_dates": keys[:8],
        }
    return out, report


# ----------------------------------------------------------------- tf helpers

def build_model(size, n_classes, backbone, dropout, weights="imagenet"):
    import tensorflow as tf

    inputs = tf.keras.Input(shape=(size, size, 3))
    x = tf.keras.applications.mobilenet_v3.preprocess_input(inputs)
    if backbone == "mobilenetv3small":
        bb = tf.keras.applications.MobileNetV3Small(
            input_shape=(size, size, 3), include_top=False, weights=weights)
    elif backbone == "efficientnetlite0":
        x = tf.keras.applications.efficientnet.preprocess_input(inputs)
        bb = tf.keras.applications.EfficientNetB0(
            input_shape=(size, size, 3), include_top=False, weights=weights)
    elif backbone == "mobilenetv2":
        bb = tf.keras.applications.MobileNetV2(
            input_shape=(size, size, 3), include_top=False, weights=weights)
    else:
        raise SystemExit(f"unknown backbone {backbone}")
    bb.trainable = False
    y = bb(x, training=False)
    y = tf.keras.layers.GlobalAveragePooling2D()(y)
    y = tf.keras.layers.Dropout(dropout)(y)
    y = tf.keras.layers.Dense(n_classes, activation="softmax", name="fabric")(y)
    return tf.keras.Model(inputs, y), bb


def make_ds(pairs, classes, size, batch, train, seed):
    import tensorflow as tf
    paths = [str(p) for p, _ in pairs]
    labels = [classes.index(c) for _, c in pairs]
    ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if train:
        ds = ds.shuffle(min(len(paths), 4096), seed=seed, reshuffle_each_iteration=True)

    def load(path, label):
        img = tf.io.read_file(path)
        img = tf.io.decode_image(img, channels=3, expand_animations=False)
        img = tf.image.resize(img, (size, size), method="bilinear")
        img = tf.cast(img, tf.float32)
        img = tf.image.random_flip_left_right(img) if train else img
        return img, tf.one_hot(label, len(classes))

    ds = ds.map(load, num_parallel_calls=tf.data.AUTOTUNE)
    ds = ds.batch(batch).prefetch(tf.data.AUTOTUNE)
    return ds


def confusion_matrix(y_true, y_pred, n):
    cm = np.zeros((n, n), dtype=int)
    for t, p in zip(y_true, y_pred):
        cm[t, p] += 1
    return cm


def export_tflite(model, out_dir, size, rep_pairs, classes):
    import tensorflow as tf

    saved = out_dir / "saved_model"
    model.export(str(saved))
    conv = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]

    def rep_gen():
        rng = random.Random(0)
        sample = rep_pairs[: min(len(rep_pairs), 300)]
        for _ in range(120):
            p, _ = rng.choice(sample)
            try:
                with Image.open(p) as im:
                    a = np.asarray(im.convert("RGB").resize((size, size)), dtype=np.float32)
                yield [a[None, ...]]
            except Exception:
                continue

    conv.representative_dataset = rep_gen
    conv.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    conv.inference_input_type = tf.uint8
    conv.inference_output_type = tf.uint8
    tflite = conv.convert()
    p = out_dir / "fabric_ghana.tflite"
    p.write_bytes(tflite)
    return p, len(tflite)


# ----------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default="/kaggle/input/ntoma-fabric/raw")
    ap.add_argument("--out", default="/kaggle/working")
    ap.add_argument("--smoke", action="store_true", help="tiny CPU run to prove the pipeline")
    ap.add_argument("--size", type=int, default=DEFAULTS["size"])
    ap.add_argument("--batch", type=int, default=DEFAULTS["batch"])
    ap.add_argument("--epochs-head", type=int, default=DEFAULTS["epochs_head"])
    ap.add_argument("--epochs-fine", type=int, default=DEFAULTS["epochs_fine"])
    ap.add_argument("--backbone", default=DEFAULTS["backbone"])
    ap.add_argument("--seed", type=int, default=DEFAULTS["seed"])
    ap.add_argument("--no-pretrained", action="store_true", help="random init (offline/CI)")
    ap.add_argument("--dry-run", action="store_true", help="index + split report only")
    ap.add_argument("--metadata", default=None,
                    help="metadata.csv sidecar (default: <data>/metadata.csv if present)")
    args = ap.parse_args()

    if args.smoke:
        args.size, args.batch = 96, 16
        args.epochs_head, args.epochs_fine = 1, 1

    data = Path(args.data)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    if not data.is_dir():
        print(f"ERROR: dataset root not found: {data}", file=sys.stderr)
        print("On Kaggle, add your dataset and pass "
              "--data /kaggle/input/<dataset-slug>/raw", file=sys.stderr)
        return 2

    rng = random.Random(args.seed)
    np.random.seed(args.seed)

    meta, csv_path = load_metadata(data, args.metadata)
    table = index_dataset(data, meta)
    if not table:
        print(f"ERROR: no class folders with images under {data}", file=sys.stderr)
        return 2

    total = sum(len(v) for s in table.values() for v in s.values())
    print("=" * 74)
    print(f"Ntoma Ghanaian fabric classifier — {args.backbone} @ {args.size}px")
    print("=" * 74)
    print(f"data: {data}")
    print(f"classes: {len(table)} | photos: {total:,}")

    grouped = sum(1 for s in table.values() for k in s if k.startswith("meta:"))
    if not meta:
        state = "exists but has no source/session filled in" if csv_path.exists() \
            else "not found"
        print(f"metadata: {csv_path.name} {state} — grouping by EXIF/filename only")
        print("          (tools/dataset/init_dataset.py --metadata-template --apply, "
              "then --check)")
    else:
        print(f"metadata: {grouped}/{total} photos grouped from {csv_path.name}")

    splits, report = split_sessions(table, rng, DEFAULTS["val_frac"], DEFAULTS["test_frac"])
    for name in ("train", "val", "test"):
        print(f"  {name:5s} {len(splits[name]):6,d} photos")
    n_sessions = {c: r["sessions"] for c, r in report.items()}
    print(f"  sessions per class: min {min(n_sessions.values())}, "
          f"median {sorted(n_sessions.values())[len(n_sessions)//2]}, "
          f"max {max(n_sessions.values())}")

    unsplittable = [c for c, r in report.items() if not r["splittable"]]
    if unsplittable:
        print(f"\n!! {len(unsplittable)} class(es) have too few sessions to hold out a "
              f"validation set:")
        for c in unsplittable:
            print(f"     {c}: {report[c]['reason']}")
        print("   These train but get NO accuracy claim. Collect more sessions.")

    known = schema_classes()
    off_schema = sorted(c for c in table if known and c not in known)
    if off_schema:
        n = sum(len(v) for c in off_schema for v in table[c].values())
        print(f"\n!! {len(off_schema)} folder(s) are not classes in label_schema.json "
              f"({n} photos): {off_schema}")
        print("   They would train as classes with no app localisation and no "
              "manifest entry.")
        print("   make_kaggle_dataset.py refuses to package these; fix with "
              "tools/dataset/fix_folder_names.py.")

    ungrouped = sum(len(s.get("unknown", [])) for s in table.values())
    if ungrouped == total and total:
        print(f"\n!! ALL {total:,} photos share one session. Without recorded "
              f"source/session there is no honest split at all:")
        print("     EXIF is stripped on delivery, so every photo falls back to "
              "'unknown'.")
        print("     Fix: tools/dataset/init_dataset.py --metadata-template --apply, "
              "then fill source + session.")

    critical_missing = [
        (a, b) for a, b in CRITICAL_PAIRS
        if a not in table or b not in table
        or len(table.get(a, {})) < 3 or len(table.get(b, {})) < 3
    ]
    if critical_missing:
        print(f"\n!! critical pairs that cannot yet be evaluated: {critical_missing}")

    (out / "split_report.json").write_text(json.dumps({
        "data_root": str(data), "total_photos": total, "classes": len(table),
        "per_class": report, "unsplittable": unsplittable,
        "critical_pairs_unevaluable": [list(p) for p in critical_missing],
        "policy": "sessions dealt whole; a session never straddles two splits. "
                  "Split key = metadata source|session when a sidecar is present, "
                  "else EXIF date, else the date in the filename, else 'unknown'",
        "metadata": str(csv_path) if meta else None,
        "photos_grouped_from_metadata": grouped,
    }, indent=2))
    print(f"\nwrote {out/'split_report.json'}")

    if args.dry_run:
        return 0

    if len(splits["train"]) == 0:
        print("ERROR: nothing in train split.", file=sys.stderr)
        return 2
    if len(splits["val"]) == 0:
        print("ERROR: nothing in val split - collect more sessions per class.",
              file=sys.stderr)
        return 2

    import tensorflow as tf

    classes = sorted(table)
    if args.size >= 224:
        tf.keras.mixed_precision.set_global_policy("mixed_float16")

    weights = None if args.no_pretrained else "imagenet"
    model, backbone = build_model(args.size, len(classes), args.backbone,
                                  DEFAULTS["dropout"], weights)

    train_ds = make_ds(splits["train"], classes, args.size, args.batch, True, args.seed)
    val_ds = make_ds(splits["val"], classes, args.size, args.batch, False, args.seed)
    test_ds = make_ds(splits["test"], classes, args.size, args.batch, False, args.seed)

    cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=5,
                                         restore_best_weights=True, verbose=1),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.4,
                                             patience=3, min_lr=1e-6, verbose=1),
    ]

    model.compile(optimizer=tf.keras.optimizers.Adam(DEFAULTS["lr_head"]),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    print(f"\n-- phase 1: frozen head ({args.epochs_head} epochs, {len(classes)} classes)")
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_head,
              callbacks=cbs, verbose=2)

    backbone.trainable = True
    thaw = max(1, len(backbone.layers) // 4)
    for layer in backbone.layers[:-thaw]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(DEFAULTS["lr_fine"]),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    print(f"-- phase 2: fine-tune last {thaw} backbone layers "
          f"({args.epochs_fine} epochs)")
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_fine,
              callbacks=cbs, verbose=2)

    def predict(ds):
        yt, yp = [], []
        for xb, yb in ds:
            yt.append(np.asarray(yb).argmax(1))
            yp.append(model.predict(xb, verbose=0).argmax(1))
        return np.concatenate(yt), np.concatenate(yp)

    print("\n== per-class report (held-out SESSIONS, not held-out crops) ==")
    results = {}
    for split, ds in (("val", val_ds), ("test", test_ds)):
        if len(ds) == 0:
            continue
        yt, yp = predict(ds)
        cm = confusion_matrix(yt, yp, len(classes))
        accs = []
        for i, c in enumerate(classes):
            m = yt == i
            a = float((yp[m] == i).mean()) if m.any() else float("nan")
            accs.append(a)
            results.setdefault(c, {})[split] = None if np.isnan(a) else round(a, 4)
            if not np.isnan(a):
                print(f"  {c:20s} {a:.3f}  n={int(m.sum())}")
        macro = float(np.nanmean(accs))
        overall = float((yt == yp).mean())
        print(f"  {split.upper():20s} MACRO {macro:.3f} | OVERALL {overall:.3f}")
        results.setdefault("_summary", {})[split] = {
            "macro": round(macro, 4), "overall": round(overall, 4),
            "n": int(len(yt)),
        }
        np.save(out / f"confusion_{split}.npy", cm)

    print("\n== critical-pair accuracy (this is what decides shippability) ==")
    pair_report = {}
    for a, b in CRITICAL_PAIRS:
        if a not in classes or b not in classes:
            continue
        yt, yp = predict(test_ds if len(splits["test"]) else val_ds)
        ia, ib = classes.index(a), classes.index(b)
        m = (yt == ia) | (yt == ib)
        if not m.any():
            continue
        acc = float((yp[m] == yt[m]).mean())
        # how often the two are confused with each other specifically
        conf_ab = int((((yt == ia) & (yp == ib)) | ((yt == ib) & (yp == ia))).sum())
        pair_report[f"{a}|{b}"] = {"pair_accuracy": round(acc, 4),
                                   "mutual_confusions": conf_ab, "n": int(m.sum())}
        print(f"  {a} vs {b}: {acc:.3f}  (n={int(m.sum())}, "
              f"{conf_ab} confused with each other)")

    tflite_path, nbytes = export_tflite(model, out, args.size,
                                        splits["train"][:400], classes)
    print(f"\nwrote {tflite_path} ({nbytes/1e6:.2f} MB, int8)")

    (out / "fabric_labels.json").write_text(json.dumps({
        "classes": classes, "input_size": args.size, "backbone": args.backbone,
        "quantization": "int8", "input_dtype": "uint8",
        "trained_with": "train_kaggle.py", "split_policy": "session-held-out",
        "per_class": results, "critical_pairs": pair_report,
        "unsplittable_classes": unsplittable,
        "note": ("classes listed in unsplittable_classes have NO held-out validation "
                 "and must not carry an accuracy claim"),
    }, indent=2))
    print(f"wrote {out/'fabric_labels.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
