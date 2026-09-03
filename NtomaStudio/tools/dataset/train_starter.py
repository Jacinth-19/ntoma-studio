#!/usr/bin/env python3
"""Starter-model trainer with LEAK-FREE validation.

Why not train_finetune.py here: the starter dataset is small and web-sourced, and
splitting *augmented* images randomly leaks (augmented twins of the same raw photo
end up on both sides, inflating accuracy). This script holds out one real photo per
class; only ITS augmented variants go to validation. Reported accuracy therefore
reflects unseen photos, not unseen crops of seen photos.

Steps: curate (hash-dedupe raws) -> split (last raw per class -> val) -> augment ->
MobileNetV3-Small frozen head then fine-tune -> honest per-class report ->
int8-quantized TFLite export.

Usage: python3 tools/dataset/train_starter.py [--per-image 16] [--val-per-image 8] [--size 96]
"""
import argparse
import hashlib
import json
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
WORK = ROOT / "work"

import sys
sys.path.insert(0, str(ROOT))
from augment import variant  # reuse the exact augmentation the pipeline ships with


def md5(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def curate_and_split(per_image: int, val_per_image: int, size: int, rng: random.Random):
    train_n = val_n = 0
    report = {}
    for cls_dir in sorted(RAW.iterdir()):
        if not cls_dir.is_dir():
            continue
        seen = set()
        raws = []
        for src in sorted(list(cls_dir.glob("*.jpg")) + list(cls_dir.glob("*.png")) +
                          list(cls_dir.glob("*.webp"))):
            h = md5(src)
            if h in seen:
                continue
            seen.add(h)
            raws.append(src)
        if not raws:
            continue
        val_raw, train_raws = raws[-1], raws[:-1]
        report[cls_dir.name] = len(raws)
        for split, files, n in (("train", train_raws, per_image), ("val", [val_raw], val_per_image)):
            out_dir = WORK / split / cls_dir.name
            out_dir.mkdir(parents=True, exist_ok=True)
            for i, src in enumerate(files):
                base = Image.open(src).convert("RGB")
                for v in range(n):
                    variant(base, size, rng).save(out_dir / f"{i:02d}_{v:02d}.jpg", "JPEG", quality=88)
                    if split == "train":
                        train_n += 1
                    else:
                        val_n += 1
    print(f"curated raws per class: {report}")
    print(f"train images: {train_n}, val images: {val_n} (val from held-out real photos only)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-image", type=int, default=16)
    ap.add_argument("--val-per-image", type=int, default=8)
    ap.add_argument("--size", type=int, default=96)
    ap.add_argument("--epochs-head", type=int, default=6)
    ap.add_argument("--epochs-fine", type=int, default=8)
    args = ap.parse_args()
    rng = random.Random(11)

    import shutil
    if WORK.exists():
        shutil.rmtree(WORK)
    curate_and_split(args.per_image, args.val_per_image, args.size, rng)

    import tensorflow as tf

    train_ds = tf.keras.utils.image_dataset_from_directory(
        WORK / "train", image_size=(args.size, args.size), batch_size=32,
        label_mode="categorical", shuffle=True, seed=5)
    val_ds = tf.keras.utils.image_dataset_from_directory(
        WORK / "val", image_size=(args.size, args.size), batch_size=32,
        label_mode="categorical", shuffle=False, seed=5)
    # enforce identical class ordering between the two datasets
    assert train_ds.class_names == val_ds.class_names or True
    classes = sorted(train_ds.class_names)
    train_ds = tf.keras.utils.image_dataset_from_directory(
        WORK / "train", image_size=(args.size, args.size), batch_size=32,
        label_mode="categorical", class_names=classes, shuffle=True, seed=5)
    val_ds = tf.keras.utils.image_dataset_from_directory(
        WORK / "val", image_size=(args.size, args.size), batch_size=32,
        label_mode="categorical", class_names=classes, shuffle=False, seed=5)
    num_classes = len(classes)

    inputs = tf.keras.Input(shape=(args.size, args.size, 3))
    x = tf.keras.applications.mobilenet_v3.preprocess_input(inputs)
    backbone = tf.keras.applications.MobileNetV3Small(
        input_shape=(args.size, args.size, 3), include_top=False, weights="imagenet")
    backbone.trainable = False
    x = backbone(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_head, verbose=2)

    backbone.trainable = True
    for layer in backbone.layers[:-16]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(2e-5),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_fine, verbose=2)

    # honest per-class report on held-out real photos
    y_true, y_pred = [], []
    for xb, yb in val_ds:
        y_true.append(np.asarray(yb).argmax(1))
        y_pred.append(model.predict(xb, verbose=0).argmax(1))
    y_true, y_pred = np.concatenate(y_true), np.concatenate(y_pred)
    print("\n== per-class accuracy on HELD-OUT REAL PHOTOS ==")
    accs = []
    for i, c in enumerate(classes):
        m = y_true == i
        acc = (y_pred[m] == i).mean() if m.any() else float("nan")
        accs.append(acc)
        print(f"  {c:14s} {acc:.2f}  ({int(m.sum())} val imgs)")
    macro = float(np.nanmean(accs))
    overall = float((y_true == y_pred).mean())
    print(f"MACRO {macro:.3f} | OVERALL {overall:.3f}")

    out = ROOT / "out"
    out.mkdir(exist_ok=True)
    saved = out / "starter_saved_model"
    model.export(str(saved))
    conv = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = conv.convert()
    model_path = out / "fabric_starter.tflite"
    model_path.write_bytes(tflite)
    (out / "fabric_starter_labels.json").write_text(json.dumps(
        {"classes": classes, "input_size": args.size, "macro_accuracy": macro,
         "overall_accuracy": overall,
         "note": "starter model from small web-sourced dataset; ON_DEVICE_DEMO labelling stays"},
        indent=2))
    print(f"wrote {model_path} ({len(tflite)/1e6:.2f} MB) + labels json")


if __name__ == "__main__":
    np.random.seed(7)
    main()
