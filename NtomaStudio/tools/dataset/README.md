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

## Expand photos into lighting / size / angle / sensor variants

```bash
python3 tools/dataset/make_variants.py                              # plan only, writes nothing
python3 tools/dataset/make_variants.py --per-image 12 --write
python3 tools/dataset/make_variants.py --catalog-demo --write        # exercise with no data
```

Applies colour temperature, exposure, contrast, gamma, directional shadow, vignette,
sheen, saturation, rotation, zoom-crop, keystone, motion blur, sensor noise, and cycles
output sizes (224/320/512) — the conditions a real phone photo of cloth will have.

**These are variants, not photographs.** Every one derives from a source photo already in
`raw/`, so 2,000 photos expanded 10× contain the information of 2,000 photos. They make
the model robust to *condition*, never to a fabric it has never seen. `track_collection.py`
counts `raw/` only and reports `variants/` separately; the output folder gets a
`README_DO_NOT_COUNT.txt` saying so.

Variant filenames **preserve the source capture date** so `train_kaggle.py`'s session
split still holds — every variant of one source photo lands in the same split. Never
rename variant outputs; the name is the mechanism that prevents leakage.

Two bugs found and fixed while building this, both worth knowing because either would
have silently produced a useless expansion:

- **Compounding exposure.** Independent exposure, gamma, shadow and vignette multiplied
  to 0.55 × 0.55 × 0.60 × 0.70 — removing ~87% of the light. Measured mean luminance
  collapsed to 15.7 against a 109.4 source, with the darkest variant at 0.8% brightness.
  There is now exactly **one** global exposure control (0.70–1.35×, the real range of
  capture variation) and a `normalize_exposure` floor that guarantees every variant stays
  legible.
- **Black-filled perspective.** The keystone transform passed 8 arbitrary random integers
  to `Image.PERSPECTIVE`. PIL treats those as the output→input matrix, so random values
  blew up the denominator and mapped most output pixels outside the source, which PIL
  fills black. It now jitters the *source* quad and clamps every corner inside the image,
  so no black band is possible. Rotation and perspective also fill from the cloth's own
  median edge colour rather than black.

Verified on the 15 catalog swatches (180 variants): luminance ratios 0.58–1.29 of source,
no black-fill artefacts, all 180 files pixel-distinct.

## Gate against synthetic / under-diverse data

```bash
python3 tools/dataset/audit_diversity.py --data tools/dataset/raw
```

AI-generated fabric images pass visual inspection **and** defeat texture heuristics (a
synthetic kente set measured *higher* on the app's own GLCM noise metric than real
photographs). The signal that does separate is diversity: generated images from one prompt
are far more mutually similar than real photos of the same cloth, which collapses the
effective sample size — and because the same bias sits in train and val, the validation
set cannot detect it.

Exit 2 on a suspicious class, so it can gate a Kaggle upload. See
`docs/SYNTHETIC_DATA_POLICY.md` for the measurements and why the dataset was not
generated.

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

## Train on Kaggle

`tools/dataset/kaggle/` turns collected photos into a shippable model on Kaggle's free GPU:

```bash
python3 tools/dataset/kaggle/make_kaggle_dataset.py --write   # package raw/ for upload
# then run tools/dataset/kaggle/ntoma_train.ipynb on Kaggle (GPU T4 x2, Internet On)
```

It exports `fabric_ghana.tflite` (int8, 2–4 MB at 224 px) plus `fabric_labels.json`.
Unlike `train_starter.py`, it splits by **whole capture session**, because photos from one
market day share lighting, backdrop and stock — splitting them across train/val inflates
accuracy without ever looking like a bug. See `kaggle/README.md`.

The trainer also runs on CPU, which is how it was validated here before being written up:

```bash
python3 tools/dataset/kaggle/train_kaggle.py --data tools/dataset/raw --out /tmp/out --dry-run
python3 tools/dataset/kaggle/train_kaggle.py --data tools/dataset/raw --out /tmp/out --smoke
```

`--dry-run` reports the split in seconds and is worth running before every training run.
`--smoke` proves the plumbing and nothing else.

### On public datasets

There is **no usable public dataset of Ghanaian fabrics.** The only related find is
*African Fabric Images* on Kaggle (1,056 images at 64×64 px, Google-sourced, unlicensed) —
below training resolution, unlabelled by fabric type, and not shippable. The rest of the
results are industrial fabric *defect* datasets (a different task) or stock-photo
libraries. The 50,000 photos must be collected; Kaggle converts them into a model, it does
not supply them.

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
