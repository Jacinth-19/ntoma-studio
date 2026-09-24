# Fabric dataset & fine-tune pipeline

The shipped classifier is MobileNetV2 (ImageNet) + GLCM + kente-strip heuristics with
honest `ON_DEVICE_DEMO` labelling. This pipeline is the path to a **real** fabric model:
a MobileNetV3-Small fine-tuned on labelled Ghanaian fabric photos, exported to a ~2-3 MB
quantized TFLite model that drops into `app/src/main/assets/` behind the same interface.

## Layout
```
tools/dataset/
  label_schema.json   17 classes incl. FUGU + GONJA, folder layout, collection rules
  raw/<CLASS>/*.jpg   YOUR real labelled photos (not committed — see .gitignore guidance)
  augment.py          Pillow-only augmentation: crop/rotate/perspective/jitter -> augmented/
  train_finetune.py   Keras MobileNetV3-Small fine-tune -> quantized .tflite + val accuracy
  ingest.py           import an app contribution zip into raw/ (validated, deduped)
  verify_catalog.py   audit the SHIPPED catalog: label<->asset correspondence, look
                      coverage, duplicates, watermark-like stamps, licence trail, and
                      per-class readiness vs the thresholds below. Read-only.
  out/                generated models (never shipped without human review)
```

## Audit the catalog any time

```bash
python3 tools/dataset/verify_catalog.py            # human report; exit 2 = warnings
python3 tools/dataset/verify_catalog.py --json     # machine-readable, for CI
```

Exit 0 = clean, 1 = a label points at a missing/orphan asset, 2 = warnings only
(uncovered classes, unlicensed assets, thin dataset). See `docs/DATASET_AUDIT.md`
(2026-09-24) for the findings this tool was written from.

## Workflow
1. **Collect**: 300+ photos/class minimum (1000+ before publishing accuracy claims).
   Market days at Makola/Kantamanto/Kejetia, weaver cooperatives in Bonwire (kente) and
   Tamale (fugu), user contributions with consent. Follow `label_schema.json` guidance —
   lighting, distance, angle and fold variety matter more than volume.
2. **Augment**: `python3 tools/dataset/augment.py --per-image 10`
3. **Train**: `python3 tools/dataset/train_finetune.py` (add `--smoke` first on a laptop
   to prove the toolchain; a smoke model is a pipeline test, NEVER a shipped model).
4. **Review**: check the printed validation accuracy per class; inspect confusions
   (KENTE vs KENTE_PRINT and FUGU vs GONJA will be the hard pairs).
5. **Integrate**: replace the classifier asset, keep the heuristic fallback, re-run
   `./gradlew :app:testDebugUnitTest`, and only then stop labelling results
   `ON_DEVICE_DEMO` for the classes that genuinely improved.

## Honesty rules (non-negotiable)
- No accuracy claims from models trained on <1000 images/class.
- Authentic hand-woven vs machine-printed kente is a **separate task** (see
  Authente-Kente, ~88% published) — do not conflate it with category labels.
- The demo fallback and its labelling stay in the app permanently.
