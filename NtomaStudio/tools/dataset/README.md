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

## Write the split and the dataset statistics

```bash
python3 tools/dataset/split_dataset.py                 # dry run
python3 tools/dataset/split_dataset.py --write         # manifest + statistics
python3 tools/dataset/split_dataset.py --write --link  # + symlink trees
```

Writes `tools/dataset/splits/manifest.csv` (one row per photo: `file, class,
group, split, source, session, view, lighting`) and `dataset_statistics.json`
(counts, groups, per-class progress, attribute distributions and fill rates).

**The split rule is not defined here** — it imports `split_sessions` from
`kaggle/train_kaggle.py`, so the manifest can never disagree with training. The
leakage property is asserted, not assumed: no group may appear in two splits, and
the script fails if one does.

A **manifest, not copied folders.** Copying `train/val/test` duplicates every
photo and goes stale the moment `raw/` changes; the manifest is a few KB and any
loader can consume it (`pandas.read_csv` → group by `split`). `--link` builds
symlink trees for tools that insist on real directories — no disk, no drift.
`dataset_statistics.json` carries a `raw_fingerprint`, so a stale manifest is
detectable rather than silently wrong.

Derived output: `splits/` is gitignored, like `raw/`.

## Health-check a batch (run this every session)

```bash
python3 tools/dataset/check_dataset.py                  # exit 1 = blockers
python3 tools/dataset/check_dataset.py --strict         # warnings fail too
python3 tools/dataset/check_dataset.py --json /tmp/h.json
```

Decodes every photo and reports integrity, duplicates, resolution, exposure,
presentation and coverage. **Blockers** are things that would poison training —
unreadable or zero-byte files, stray non-image files, photos in a folder that is
not a schema class, near-duplicate pairs **across different classes** (the same
cloth under two labels), and a failing metadata check. **Warnings** are things a
human should judge: studio/catalogue backdrops, possible stock credit bars (a
watermark is pixels, so this is the only way to catch one without metadata),
frames that are very dark or near-featureless, below-minimum resolution, empty
classes and target shortfalls.

Every check exists because it caught something real on the first 83 photos — 3
were one Alamy stock image, 14 were white-backdrop product shots, all 83 had zero
EXIF. A defect found in a 200-photo batch is a note; the same defect found at
50,000 is a re-shoot.

`docs/FIELD_CAPTURE_PROTOCOL.md` turns those findings into the collector's rules,
shot list and rejection list.

## Track collection progress

```bash
python3 tools/dataset/track_collection.py            # progress vs the 50k plan
python3 tools/dataset/track_collection.py --verbose  # + capture dates per class
```

Counts `raw/` only — augmented images are reported separately because counting them
would inflate the dataset with information already in it. Also flags classes fed from a
single capture session (a generalisation *and* train/val leakage risk). Read-only.

## Fix legacy folder names before training

```bash
python3 tools/dataset/fix_folder_names.py            # dry run (default)
python3 tools/dataset/fix_folder_names.py --apply    # rename
```

The trainer reads the class name from the **folder name**, and a folder that is not a
class in `label_schema.json` is skipped *silently*. Photos sitting in a v1-named folder
therefore never reach training, and nothing fails — they simply are not there. This is
easy to miss: an upload of 83 photos counted as 50 until this ran.

The mapping comes from the `aliases` block of `label_schema.json` — the same map
`ingest.py` uses — so there is one source of truth for what a v1 name means. Names mapped
to `@REVIEW` are left alone on purpose: those are cases where the v1 label does not
determine the v2 class, so a human has to look at the cloth. Guessing would poison the
training set.

Safety: dry-run by default; every file's MD5 is compared before and after; a name
collision keeps both files rather than clobbering one; byte-identical duplicates are
reported; and the script is idempotent, so re-running it after re-extracting `raw.rar`
restores the correct names. It needs to be re-runnable because `raw/` is gitignored — the
rename is not captured by git.

## Per-photo metadata (required before training)

```bash
python3 tools/dataset/init_dataset.py                               # dry run: create the 26 class folders
python3 tools/dataset/init_dataset.py --apply                       # create them
python3 tools/dataset/init_dataset.py --metadata-template --apply   # seed a blank row per photo
python3 tools/dataset/init_dataset.py --check                       # validate (exit 1 = not ready)
```

The tree stays flat at the 26 classes in `label_schema.json`; everything finer —
technique, material, colour, region, brand family, view, lighting, occasion — is a
**column** in `raw/metadata.csv`, not a folder. Full rationale and the column
reference: `docs/DATASET_SCHEMA.md`.

`metadata.csv` also carries the **split key**, `source|session`, and it is the only one
that works: all 83 delivered photos have zero EXIF, one uniform JPEG encoder signature
and bare `NN.jpg` names, so EXIF/filename grouping collapses the whole corpus into a
single session and the trainer reports that there is no honest split at all. Record
`source` and `session` at capture time — provenance cannot be reconstructed later.

`--check` fails on a class outside the schema, a class that disagrees with its folder, a
missing image, a duplicate row, a leftover template example, a blank or malformed
`source`/`session`, and any header drift. The packager ships `metadata.csv` inside the
Kaggle zip, so the trainer picks it up at `<data>/metadata.csv` with no extra wiring.

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
