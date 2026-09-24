# Training on Kaggle

Fine-tune the Ghanaian fabric classifier on Kaggle's free GPU and export the int8 TFLite
that drops into the Android app.

## The honest starting position

**There is no usable public dataset of Ghanaian fabrics.** A search of Kaggle, Hugging
Face, Roboflow and Zenodo turned up exactly one related dataset — *African Fabric Images*
(1,056 images, 64×64 px, sourced from Google Image searches, no licence). 64 px is below
the 224 px the pipeline trains at, the images are unlabelled by fabric type, and
Google-sourced images have no usable licence. It cannot be shipped in a store app.

Everything else that surfaced was either fabric **defect** detection for industrial
textile QC (TILDA, FabricSpotDefect, the Kaggle defect datasets) — a different task — or
stock-photo libraries (Adobe, Shutterstock, Etsy) that are licensed for design use, not
for training a model and redistributing it.

So the 50,000 photos have to be collected. What Kaggle is for is not *finding* the data,
it is turning collected data into a model without owning a GPU. This directory makes that
step a two-cell job once photos exist.

## Pipeline

```
  collect (field)                    Kaggle                        app
  ───────────────                    ──────                        ───
  raw/<CLASS>/*.jpg   ──►  ntoma-fabric dataset  ──►  ntoma_train.ipynb  ──►  fabric_ghana.tflite
        ▲                                                  │                  fabric_labels.json
        │                                                  ▼
  ingest.py  ◄──  app contribution zip            split_report.json
```

## Step by step

### 1. Package the photos

```bash
python3 tools/dataset/kaggle/make_kaggle_dataset.py            # inspect + report
python3 tools/dataset/kaggle/make_kaggle_dataset.py --write    # produce the zip
```

It refuses to build (without `--force`) when photos sit in folders that are not schema
classes, and it warns about classes below their `collection_plan.json` target, classes
with fewer than 3 capture sessions, and images below 200 px. The zip's root contains
`raw/`, so it lands at `/kaggle/input/<slug>/raw` — the training script's default.

### 2. Upload it

```bash
kaggle datasets create -p . --dir-mode zip     # or drag the zip to kaggle.com/datasets/new
```

### 3. Run the notebook

Open `ntoma_train.ipynb` on Kaggle. **Set both of these** in the right-hand panel:

| Setting | Value |
|---|---|
| Accelerator | `GPU T4 x2` |
| Internet | `On` (needed for ImageNet weights) |

Then *Add Input* → your dataset, and run all cells. Cell 4 is a dry run that reports the
split before you spend GPU time; cell 5 trains.

### 4. Ship the model

Download `fabric_ghana.tflite` and `fabric_labels.json` from the notebook output and put
them in `app/src/main/assets/`. The model contract is **uint8 in, uint8 out**:

```
input : [1, 224, 224, 3] uint8   (scale 1.0, zero_point 0)
output: [1, 25]          uint8   (scale 1/256, zero_point 0)
```

The Android side must feed `uint8` and dequantise the output with the scale in the JSON.
Keep the `ON_DEVICE_DEMO` labelling for every class that did not clear its bar — the
labels file lists which those are under `unsplittable_classes`.

Expected size: **2–4 MB** at 224 px, ~1.2 MB at 96 px.

## Why this trains differently from `train_starter.py`

`train_starter.py` holds out one real photo per class. That is leak-free but **not
session-aware**. With 50,000 field photos, each class will span dozens of market days, and
photos from one session share lighting, backdrop and stock. Splitting them across
train/val is leakage that inflates the accuracy number without ever looking like a bug.

`train_kaggle.py` deals **whole sessions** into train/val/test:

- session key = EXIF `DateTimeOriginal`, else the date in the filename
  (`<CLASS>_<yyyyMMdd_HHmmss>.jpg`, which is what the app already exports)
- a session never straddles two splits
- a class with fewer than 3 sessions **cannot be split honestly**, so it goes to train
  only, is named in `unsplittable_classes`, and must not carry an accuracy claim

The reported number is therefore accuracy on *unseen capture sessions*, which is what the
shipped model will actually face. The notebook also prints a confusion count for the
critical pairs — `KENTE_ASHANTI`/`KENTE_PRINT`, `FUGU_BATAKARI`/`GONJA`,
`WAX_REAL`/`FANCY_PRINT`, `BATIK`/`TIEDYE` — because those five decide shippability, and
an aggregate macro score hides them completely.

## Testing this without Kaggle

The training script runs unmodified on CPU, which is how it was validated before being
written up here:

```bash
python3 tools/dataset/kaggle/train_kaggle.py \
    --data tools/dataset/raw --out /tmp/out --dry-run      # split report only, seconds
python3 tools/dataset/kaggle/train_kaggle.py \
    --data tools/dataset/raw --out /tmp/out --smoke        # ~3 min, 96px, 1+1 epochs
```

`--smoke` proves the plumbing — data loading, the two-phase fit, per-class reporting,
int8 export and the TFLite contract. It proves **nothing about accuracy**, and a model
from it must never be shipped or quoted.

## Files

| File | Purpose |
|---|---|
| `train_kaggle.py` | The trainer. Runs on Kaggle GPU, or locally with `--smoke`. Also usable in CI. |
| `ntoma_train.ipynb` | Kaggle notebook wrapper: environment → clone → find data → dry run → train → download. |
| `make_kaggle_dataset.py` | Packages `raw/` into an uploadable zip and refuses to build dishonest ones. |
| `--dry-run` | Split report only. Use this before every training run. |
