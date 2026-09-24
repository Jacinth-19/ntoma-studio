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

## Track collection progress

```bash
python3 tools/dataset/track_collection.py            # progress vs the 50k plan
python3 tools/dataset/track_collection.py --verbose  # + capture dates per class
```

Counts `raw/` only — augmented images are reported separately because counting them
would inflate the dataset with information already in it. Also flags classes fed from a
single capture session (a generalisation *and* train/val leakage risk). Read-only.

## Taxonomy (v2, Ghana only)

`label_schema.json` v2 defines **25 fabric classes + UNKNOWN**, up from 17. Eight
Ghanaian fabric forms the v1 schema had no name for (Ewe kete, nwomu, Obama embroidery,
java print, crepe, organza/tulle, seersucker, kente tapestry) are now explicit, and the
two classes the Ghanaian market actually discriminates by construction rather than name
— `WAX_REAL` vs `FANCY_PRINT` (double-sided vs single-sided) and `KENTE_ASHANTI` vs
`KENTE_PRINT` (woven vs printed) — are now separate. `docs/TAXONOMY_GHANA.md` has the
sources and the reasoning.

**Migration:** adding classes is harmless, but *renaming* them is not — a contribution zip
from the shipped v1 build carries v1 names, and 7 of 17 v1 categories would be rejected as
unknown. The schema's `aliases` map fixes this: `ingest.py` resolves every incoming
category through it first. Four renames (`BROCADE`, `SILK`, `CHIFFON`, `FUGU`) map straight
across; three (`KENTE`, `WAX`, `ANKARA`) are ambiguous in v1 and are parked in
`raw/_REVIEW/` with a reason rather than guessed at.

The app's `FabricCategory` enum is a separate change and must follow before users can
contribute the new classes, because the contribution picker reads `FabricCategory.values()`.
See `aliases` and `migration_from_v1` in the schema, and `docs/TAXONOMY_GHANA.md` §4.1.

## Targets

`collection_plan.json` allocates **50,000 unique raw photos** across the 25 classes,
weighted by how confusable each class is with its nearest neighbour — not flat. Flat
allocation is what produced the 2026-09-01 starter result (MACRO 0.419 overall, but 0.00
on KENTE, GONJA and BATIK).

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
