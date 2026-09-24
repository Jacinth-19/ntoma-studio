# Dataset status — what exists today

Snapshot taken 2026-09-24, after extracting `tools/dataset/raw.rar`.

## The data that arrived

`raw.rar` (12.6 MB, RAR 5.0, added by the repo owner as commit `ac6b8c4`) unpacks to
`tools/dataset/raw/<CLASS>/NN.jpg` — the exact layout the tooling expects.

| | |
|---|---|
| photographs | **83** |
| distinct class folders | **17** |
| duplicates (by MD5) | **0** |
| distinct pixel dimensions | 55 |
| images with an EXIF capture date | **0** |
| smallest image | 256 × 192 (`raw/GONJA/03.jpg`) |
| median size | ~525 × 350 |

All 83 are real photographs. `raw/` is gitignored, so this stays out of the repository;
the archive itself is the committed copy.

### But only 50 of the 83 count

`train_kaggle.py` reads the class name from the **folder name**. Seven of the 17 folders
carry v1 names that are not classes in `label_schema.json`, and the trainer skips them
without complaint — so 33 of the 83 photographs are currently invisible to training.
`track_collection.py` says so plainly:

```
! raw/KENTE/ holds 5 photos but is not a class in label_schema.json - they will never be trained on
```

(the same for `ANKARA`, `BROCADE`, `CHIFFON`, `FUGU`, `SILK`, `WAX`). Four of them rename
across losslessly — 18 photos recovered in one command:

```bash
cd tools/dataset/raw
mv BROCADE BROCADE_BAZIN && mv SILK SATIN_SILK
mv CHIFFON CHIFFON_GEORGETTE && mv FUGU FUGU_BATAKARI
```

The remaining three cannot be renamed, because the v1 name genuinely does not determine
the v2 class:

| Folder | Photos | Re-file as |
|---|---|---|
| `KENTE` | 5 | `KENTE_ASHANTI` or `KETE_EWE` — ask which tradition |
| `WAX` | 5 | `WAX_REAL` or `FANCY_PRINT` — turn the cloth over |
| `ANKARA` | 5 | `WAX_REAL` or `FANCY_PRINT` — v1's own labels contradicted each other |

`ingest.py` applies this same alias map automatically, but only for contribution zips
exported by the app (they carry a `manifest.json`). It does not read a bare `raw/` tree,
which is why the rename is a manual step here.

## Is this every fabric grouping in Ghana?

**No.** The 17 folders are the app's *v1* vocabulary. The v2 Ghana taxonomy has **26
classes** — 25 fabrics plus `UNKNOWN` — so this data covers 17 of the 25 fabric classes
and **8 have no photographs at all**:

| Missing class | Needs |
|---|---|
| `KETE_EWE` | 2,200 |
| `JAVA_PRINT` | 1,600 |
| `OBAMA_EMBROIDERY` | 1,000 |
| `NWOMU` | 800 |
| `CREPE` | 800 |
| `ORGANZA_TULLE` | 800 |
| `SEERSUCKER` | 500 |
| `TAPESTRY_JACQUARD` | 500 |

Three of the 17 folders cannot be filed automatically and need a human decision — the
schema's alias map routes them to `raw/_REVIEW/` rather than guessing:

| Folder | Photos | Why it cannot be auto-mapped |
|---|---|---|
| `KENTE` | 5 | v1 said "Ashanti and Ewe traditions". Ashanti (`KENTE_ASHANTI`) vs Ewe (`KETE_EWE`) is not recoverable from the photo |
| `WAX` | 5 | real double-sided (`WAX_REAL`) vs single-sided fancy (`FANCY_PRINT`) needs the turn-the-cloth-over test |
| `ANKARA` | 5 | v1's own `WAX`/`ANKARA` descriptions contradicted each other, so neither label is trustworthy |

So: **68 photos file cleanly, 15 are parked pending re-filing.**

## How much per fabric to reach 50,000

Targets come from `collection_plan.json`, weighted by how easily each class is confused
with another — not by how common it is. They sum to exactly 50,000.

| Fabric class | Target | Have | To go | % done |
|---|---:|---:|---:|---:|
| `UNKNOWN` (reject) | 5,300 | 0 | 5,300 | 0.0% |
| `KENTE_ASHANTI` | 3,500 | 5 | 3,495 | 0.1% |
| `KENTE_PRINT` | 3,500 | 5 | 3,495 | 0.1% |
| `WAX_REAL` | 3,500 | 5 | 3,495 | 0.1% |
| `FANCY_PRINT` | 3,200 | 5 | 3,195 | 0.2% |
| `FUGU_BATAKARI` | 3,000 | 3 | 2,997 | 0.1% |
| `BATIK` | 3,000 | 4 | 2,996 | 0.1% |
| `GONJA` | 2,800 | 3 | 2,797 | 0.1% |
| `TIEDYE` | 2,600 | 5 | 2,595 | 0.2% |
| `KETE_EWE` | 2,200 | 0 | 2,200 | 0.0% |
| `ADINKRA` | 2,200 | 4 | 2,196 | 0.2% |
| `LACE` | 2,200 | 4 | 2,196 | 0.2% |
| `BROCADE_BAZIN` | 2,000 | 5 | 1,995 | 0.2% |
| `JAVA_PRINT` | 1,600 | 0 | 1,600 | 0.0% |
| `SATIN_SILK` | 1,100 | 5 | 1,095 | 0.5% |
| `OBAMA_EMBROIDERY` | 1,000 | 0 | 1,000 | 0.0% |
| `CHIFFON_GEORGETTE` | 1,000 | 5 | 995 | 0.5% |
| `NWOMU` | 800 | 0 | 800 | 0.0% |
| `CREPE` | 800 | 0 | 800 | 0.0% |
| `ORGANZA_TULLE` | 800 | 0 | 800 | 0.0% |
| `VELVET` | 800 | 5 | 795 | 0.6% |
| `LINEN` | 700 | 5 | 695 | 0.7% |
| `COTTON_PLAIN` | 700 | 10 | 690 | 1.4% |
| `DENIM` | 700 | 5 | 695 | 0.7% |
| `SEERSUCKER` | 500 | 0 | 500 | 0.0% |
| `TAPESTRY_JACQUARD` | 500 | 0 | 500 | 0.0% |
| **TOTAL** | **50,000** | **83** | **49,917** | **0.17%** |

Average target is ~1,923 photos per class. The single biggest ask is `UNKNOWN`:
~5,300 images of things that are *not* fabric — walls, floors, blurred shots, garments
seen from too far away. Without it the model answers confidently on non-fabric input.

Regenerate this at any time with:

```
python3 tools/dataset/track_collection.py
```


**`Have` above is what the trainer actually sees** — v2-named folders only. The 18
photos in the four renameable folders are *not* in it, and neither are the 15 awaiting a
human decision. Renaming the four folders moves the total from 50 to 68, and re-filing the
other three would take it to 83. Nothing about the 50,000 changes either way; 83 vs 50 is
the difference between 0.17% and 0.10%.

## Two things that change what can be claimed

**1. No capture dates, so the split cannot be session-aware.** `train_kaggle.py` groups
photos into capture sessions (EXIF date → strict filename date → `unknown`) precisely so
that several shots of the *same cloth* never straddle train and validation. These 83 files
are named `01.jpg`, `02.jpg` with no EXIF date, so every one of them lands in a single
`unknown` session. With fewer than 3 sessions the trainer deliberately refuses to split
them, trains without a validation claim, and flags the result unsplittable. That is the
guard working as designed — but it means **these 83 photos cannot produce an accuracy
figure**, only a smoke test.

Fixing it is cheap and worth doing now rather than at 10,000 photos: put the capture date
in the filename (`KENTE_20260901_120000.jpg`) or keep the EXIF intact. One session = one
photo trip or one shooting batch, not one per image.

**2. Variants never count toward the 50,000.** `make_variants.py` produces
lighting/size/angle/sensor variations of photos you already have. They make the model
robust to *capture condition*; they cannot teach it a fabric it has never seen, because
every variant is derived from an existing photo. `track_collection.py` counts `raw/` only
and reports variants separately, and the table above is `raw/` only.

The honest headline: **50 / 50,000 = 0.10%** as the trainer counts it, or 83 / 50,000 =
0.17% once the four folders above are renamed. Either way, nine classes still have nothing
at all, and the field work has not meaningfully started.
