#!/usr/bin/env python3
"""Fine-tune MobileNetV3-Small on the augmented fabric dataset -> quantized TFLite.

Pipeline (mirrors how the shipped classifier integrates):
  1. image_dataset_from_directory over augmented/<CLASS>/ (classes = label_schema.json order)
  2. MobileNetV3-Small, ImageNet weights, frozen backbone -> train head -> unfreeze top blocks
  3. export SavedModel -> TFLite (int8 dynamic-range quantization, ~2-3 MB)
  4. print a validation accuracy report; copy nothing into the app until a human reviews it

Requires: pip install tensorflow (>=2.16). Not shipped in the APK; this is an offline
tooling script. DO NOT replace app assets with a model trained on <1000 images/class.

Usage: python3 tools/dataset/train_finetune.py [--epochs-head 8] [--epochs-fine 12] [--smoke]
  --smoke: tiny run (64px, 2+2 epochs) to prove the pipeline end-to-end on any machine.
"""
import argparse
import json
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent
AUG = ROOT / "augmented"
OUT = ROOT / "out"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs-head", type=int, default=8)
    ap.add_argument("--epochs-fine", type=int, default=12)
    ap.add_argument("--size", type=int, default=224)
    ap.add_argument("--smoke", action="store_true")
    args = ap.parse_args()
    if args.smoke:
        args.size, args.epochs_head, args.epochs_fine = 64, 2, 2

    import tensorflow as tf

    schema_classes = json.loads((ROOT / "label_schema.json").read_text())["classes"]
    present = {p.name for p in AUG.iterdir() if p.is_dir()}
    classes = [c for c in schema_classes if c in present]
    missing = [c for c in schema_classes if c not in present]
    if missing:
        print(f"NOTE: training on {len(classes)} of {len(schema_classes)} classes; "
              f"no raw data yet for: {missing}")
    ds = tf.keras.utils.image_dataset_from_directory(
        AUG, image_size=(args.size, args.size), batch_size=32, label_mode="categorical",
        class_names=classes, shuffle=True, seed=13, validation_split=0.15, subset="training",
    )
    val = tf.keras.utils.image_dataset_from_directory(
        AUG, image_size=(args.size, args.size), batch_size=32, label_mode="categorical",
        class_names=classes, shuffle=False, seed=13, validation_split=0.15, subset="validation",
    )
    num_classes = len(ds.class_names)

    inputs = tf.keras.Input(shape=(args.size, args.size, 3))
    x = tf.keras.applications.mobilenet_v3.preprocess_input(inputs)
    backbone = tf.keras.applications.MobileNetV3Small(
        input_shape=(args.size, args.size, 3), include_top=False, weights="imagenet")
    backbone.trainable = False
    x = backbone(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)

    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    model.fit(ds, validation_data=val, epochs=args.epochs_head, verbose=2)

    # fine-tune the top blocks
    backbone.trainable = True
    for layer in backbone.layers[:-24]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
                  loss="categorical_crossentropy", metrics=["accuracy"])
    model.fit(ds, validation_data=val, epochs=args.epochs_fine, verbose=2)

    loss, acc = model.evaluate(val, verbose=0)
    print(f"validation accuracy: {acc:.3f} (loss {loss:.3f}) — smoke model" if args.smoke
          else f"validation accuracy: {acc:.3f} (loss {loss:.3f})")

    OUT.mkdir(exist_ok=True)
    saved = OUT / "saved_model"
    model.export(str(saved))
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    model_path = OUT / ("fabric_smoke.tflite" if args.smoke else "fabric_finetuned.tflite")
    model_path.write_bytes(tflite)
    print(f"wrote {model_path} ({len(tflite) / 1e6:.2f} MB)")
    if not args.smoke:
        print("NEXT: human review -> replace assets/fabric_classifier.tflite -> re-run app tests")


if __name__ == "__main__":
    np.random.seed(7)
    main()
