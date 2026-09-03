#!/usr/bin/env python3
"""Device-independent QA for the bundled DeepLabV3 person segmenter asset.

Runs the SAME model file the app ships (app/src/main/assets/deeplabv3_257_mv.tflite)
through the LiteRT interpreter with the SAME preprocessing math as
PersonSegmenter.kt (RGB, pixel/127.5-1 -> [-1,1], float32, argmax over 21
PASCAL classes, person = label 15).

This is not a substitute for on-device QA (GPU delegate, memory pressure,
bitmap scaling paths), but it verifies: model contract (tensor shapes/dtypes),
normalization, label index, and that real person photos produce a centered,
body-shaped mask.

Usage: python3 tools/qa_deeplabv3.py [person_photo.jpg ...]
Requires: pip install ai-edge-litert pillow numpy
"""
import sys
import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

MODEL = "app/src/main/assets/deeplabv3_257_mv.tflite"
SIZE, CLASSES, PERSON = 257, 21, 15

def main(photos):
    interp = Interpreter(model_path=MODEL)
    interp.allocate_tensors()
    tin, tout = interp.get_input_details()[0], interp.get_output_details()[0]

    assert list(tin["shape"]) == [1, SIZE, SIZE, 3], tin["shape"]
    assert tin["dtype"] is np.float32
    assert list(tout["shape"]) == [1, SIZE, SIZE, CLASSES], tout["shape"]
    assert tout["dtype"] is np.float32
    print(f"contract OK: in {tin['shape']} f32 -> out {tout['shape']} f32")

    # Flat image must not explode into 'person'.
    interp.set_tensor(tin["index"], np.zeros((1, SIZE, SIZE, 3), np.float32))
    interp.invoke()
    flat_person = int((interp.get_tensor(tout["index"])[0].argmax(2) == PERSON).sum())
    assert flat_person < 1000, f"flat image misfires: {flat_person} person px"
    print(f"flat-image sanity OK ({flat_person} noise px)")

    ok = True
    for path in photos:
        arr = np.asarray(Image.open(path).convert("RGB").resize((SIZE, SIZE), Image.BILINEAR), np.float32)
        interp.set_tensor(tin["index"], np.ascontiguousarray(((arr / 127.5) - 1.0)[None]))
        interp.invoke()
        person = interp.get_tensor(tout["index"])[0].argmax(2) == PERSON
        frac = person.sum() / person.size
        ys, xs = np.nonzero(person)
        cx, cy = (xs.mean(), ys.mean()) if len(xs) else (-1, -1)
        centered = len(xs) > 0 and abs(cx - SIZE / 2) < 60 and abs(cy - SIZE / 2) < 60
        verdict = "PASS" if (frac > 0.03 and centered) else "FAIL"
        ok &= verdict == "PASS"
        print(f"{verdict} {path}: person {frac:.3f}, centroid ({cx:.0f},{cy:.0f}), "
              f"y-extent {ys.min() if len(ys) else '-'}..{ys.max() if len(ys) else '-'}")
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main(sys.argv[1:])
