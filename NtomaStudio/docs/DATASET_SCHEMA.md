# Dataset schema — one label axis, everything else recorded

**Scope:** how photos, folders and metadata are organised for the Ghanaian-fabric
classifier. Companion to `docs/DATASET_AUDIT.md` (what the catalog is),
`docs/TAXONOMY_GHANA.md` (what the 26 classes are) and
`docs/SYNTHETIC_DATA_POLICY.md` (what may not be counted).

Directory layout, target numbers and the counting rule live in
`tools/dataset/collection_plan.json`. This file adds the part that was missing:
**the per-photo record.**

---

## 1. The rule that decides everything

> **A folder may only hold what a model can see in a photograph. Everything else
> is a column.**

The input is a single photo. The output is one class name. So the folders must
contain exactly the classes the model predicts — the 26 in `label_schema.json` —
and nothing finer.

Anything else of interest — where it was bought, who wove it, what it cost, the
brand on the selvedge, the day, the lighting, the device — is recorded per photo
in `metadata.csv`. It is not lost. It is simply not a folder.

### Why not a deeper tree

A tempting layout splits `kente/` into `bonwire/`, `adanwomase/`, `ewe/`, then
`red_gold/`, `green_gold/` … It looks thorough and it is unusable, for two
reasons:

1. **The label stops being derivable.** Bonwire and Adanwomase weave the same
   cloth in neighbouring towns. `holland_wax` vs `english_wax` is a
   manufacturing fact, often with the *same* pattern. No model can learn these
   and no two labellers will agree — that is label noise, and it puts a ceiling
   on accuracy that no amount of data lifts.
2. **The tree multiplies past the data.** ~86 leaves × 20 view conditions × 3
   splits ≈ 5,400 folders. At the 50,000 target that is ~29 photos per folder.
   A catalog is not a dataset.

The flat tree also has a concrete cost: every class needs names in four locales
(`values/`, `values-ee/`, `values-gaa/`, `values-tw/`), and `track_collection.py`
warns on any folder that is not a schema class. Deepening the tree would fork
`label_schema.json` — the single source of truth the app, the checker and the
trainer all read.

---

## 2. Folder layout

```
raw/
├── metadata.csv              ← the per-photo record (this document)
├── KENTE_ASHANTI/            ← exactly one folder per class in label_schema.json
├── KETE_EWE/
├── ...
└── UNKNOWN/
```

Flat, 26 folders, created up front by:

```bash
python3 tools/dataset/init_dataset.py            # dry run, exit 2 = would create
python3 tools/dataset/init_dataset.py --apply    # create the 26 class folders
```

The tree is the input to `train_kaggle.py` and `make_kaggle_dataset.py`, and
`track_collection.py` counts photos per folder against `collection_plan.json`.

---

## 3. Image naming

```
<CLASS>_<source>_<yyyy-mm-dd>_<seq>.jpg

KENTE_ASHANTI_kejetia_14_2026-10-03_0001.jpg
BATIK_accra_art_centre_2026-10-06_0007.jpg
```

Why this and not `01.jpg`: the name is the last line of defence for the split.
If `metadata.csv` is ever lost, `source` and `session` can still be recovered
from the filename. Photos that arrive as `01.jpg` are, by construction, ungrouped.

App-exported contributions keep their existing
`<CLASS>_<yyyyMMdd_HHmmss>.jpg` form — `train_kaggle.py` already parses that
date as a session, and `ingest.py` resolves v1 category names through the
`aliases` map.

---

## 4. `metadata.csv`

Lives at the root of the dataset (`raw/metadata.csv`), so it travels with the
photos: it is inside the Kaggle zip, and `train_kaggle.py` picks it up at
`<data>/metadata.csv` with no extra wiring.

Template: `tools/dataset/metadata_template.csv`.

| Column | Req | Notes |
|---|---|---|
| `file` | ✅ | path relative to the data root, e.g. `KENTE_ASHANTI/01.jpg` |
| `class` | ✅ | must equal the folder name and a name in `label_schema.json` |
| `source` | ✅ | where it came from: `kejetia_14`, `accra_art_centre`, `team_selfie` |
| `session` | ✅ | `yyyy-mm-dd` or `yyyy-mm-dd_am\|_pm` — one capture session |
| `region` | | Ghana region: `Ashanti`, `Greater Accra`, `Volta`, `Northern`, … |
| `town` | | `Kumasi`, `Bonwire`, `Kpetoe`, `Accra`, … |
| `technique` | | `handwoven`, `strip_woven`, `batik_wax`, `roller_print`, … |
| `material` | | `cotton`, `silk`, `rayon_viscose`, `polyester`, `lace_net`, … |
| `brand_family` | | read off the selvedge: `veritable`, `gtp`, `atl`, `printex` |
| `colour_primary` | | `gold`, `indigo`, `emerald`, … |
| `colour_secondary` | | |
| `pattern_scale` | | `small`, `medium`, `large`, `allover`, `border_only` |
| `view` | | `flat_lay`, `folded`, `worn_by_person`, `market`, `macro_texture`, … |
| `lighting` | | `natural_shade`, `natural_sun`, `indoor_led`, `flash`, … |
| `occasion` | | `everyday`, `ceremonial`, `funeral`, `wedding`, `worship`, … |
| `quality` | | `high`, `medium`, `low` |
| `notes` | | free text |
| `group` | | leave blank; derived as `source\|session` (set only to override) |

Suggested vocabularies are in `init_dataset.py` (`VOCAB`). An unknown *value*
warns; a broken *shape* fails. Ghana's market will outrun any word list, so the
vocabulary is guidance — the split key is not.

### The four columns that are not optional

`source` and `session` exist for the split. `class` and `file` keep the record
attached to reality. Everything else is optional and still worth the ten seconds
it takes.

---

## 5. The split rule

The unit that must never straddle train/val/test is the **group**:

```
group = source | session        e.g.  kejetia_14|2026-10-03_am
```

Splitting by group, not by photo, is what stops the same cloth shot twice in one
afternoon from landing in both train and val. Sessions are dealt whole, and a
class with fewer than three groups cannot be split honestly — it trains, but it
gets **no accuracy claim** and is listed in `split_report.json`.

Both halves of the key are needed. A stall alone spans days; a day alone spans
many stalls; both share light, stock and device. If a photo is shot and then
re-shot from a second angle ten seconds later, those two images are one group —
which is why the second angle is a *view* value, never a new session.

`train_kaggle.py` resolves the split key in this order:

1. `metadata.csv` → `source|session` (preferred; it survives re-encoding)
2. EXIF `DateTimeOriginal`
3. the date in the filename
4. `"unknown"`

**On this corpus, 1–3 all fail.** All 83 delivered photos have zero EXIF, one
uniform JPEG encoder signature and bare `NN.jpg` names — so every photo lands in
session `"unknown"` and the trainer prints:

```
!! ALL 83 photos share one session. Without recorded source/session there is no
   honest split at all.
```

That is not a bug to route around; it is the reason this schema exists. Record
`source` and `session` at capture time, in the field, or the split is unrecoverable
later.

---

## 6. Working order

```bash
# 1. create the tree (once)
python3 tools/dataset/init_dataset.py --apply

# 2. seed a record for every photo already on disk
python3 tools/dataset/init_dataset.py --metadata-template --apply
#    then fill source + session (the other columns are optional)
#    NEW photos: add rows as they are captured, not later

# 3. validate before you look at a model
python3 tools/dataset/init_dataset.py --check      # exit 1 = not ready

# 4. counts against the plan
python3 tools/dataset/track_collection.py          # 68 counted, 49,932 to go

# 5. the split + statistics (imports the split rule, does not restate it)
python3 tools/dataset/split_dataset.py --write     # splits/manifest.csv

# 6. package for Kaggle (ships metadata.csv inside the zip)
python3 tools/dataset/kaggle/make_kaggle_dataset.py --write
```

`split_dataset.py` materialises the grouping as a **manifest**, not as copied
`train/val/test` folders: copies duplicate every photo and go stale the moment
`raw/` changes, while a manifest is a few KB and any loader can read it. `--link`
builds symlink trees for tools that demand real directories. The leakage rule is
asserted on the way out — no group may appear in two splits — and
`dataset_statistics.json` carries a `raw_fingerprint` so a stale split is
detectable rather than silently wrong.

`--check` fails on: a class not in the schema, a class that disagrees with its
folder, a missing image, a duplicate row, a leftover template example row, an
empty `source`/`session`, a malformed `session`, and any header drift. It warns
on vocabulary misses and on any class with fewer than three groups.

Run it on every batch. A defect caught at 200 photos is a note; at 50,000 it is
a re-shoot.

---

## 7. What this replaces, and why

An earlier proposal for this project suggested ~86 leaf folders organised by
family → subtype → town → colour → view, plus a 50,000 budget of
25,000 real + 15,000 derived + 10,000 generated. Three parts of that are not
compatible with this dataset:

| Proposal | Status here | Why |
|---|---|---|
| ~86-leaf hierarchy | **Rejected** | §1 — those labels are not in the pixels |
| 20 view conditions as folders | **Kept, as a column** (`view`) | valuable, but it is a condition, not a class |
| Separate `masks/`, `attributes/`, `train/`, `val/`, `test/` trees | **Rejected** | segmentation and multi-task heads are separate models with separate annotation budgets; splits are computed at training time and must be recomputed per run |
| Bigger taxonomy than 26 | **Rejected** | the proposal's buckets drop 11 of the 26 classes (~18,000 of the 50,000 target) and merge `WAX_REAL` with `FANCY_PRINT`, the most user-visible distinction in the product |
| 15,000 rotation/crop/brightness images counted as data | **Rejected** | derived images are not new samples; `track_collection.py` refuses to count `augmented/` |
| 10,000 AI-generated fabric images as training data | **Rejected** | `docs/SYNTHETIC_DATA_POLICY.md` §5 — permitted for illustration assets, `UNKNOWN` hard negatives and augmentation of real photos only |
| Per-photo metadata CSV | **Adopted** | §4 — the best idea in the proposal |
| Variation by view/lighting/occlusion | **Adopted** | §4 `view` + `lighting` + `occasion` |
| Licence-aware sourcing | **Adopted** | own-capture only; `source` records provenance so a licensed image is never mixed in by accident |

The cultural correction in that proposal is right and is already implemented: **ankara/wax
print is not filed as indigenous Ghanaian cloth.** The taxonomy separates
`WAX_REAL` (wax-resist, double-sided) from `FANCY_PRINT` (single-sided industrial
print, the "ankara" of the market), alongside the indigenous `KENTE_ASHANTI`,
`KETE_EWE`, `ADINKRA`, `FUGU_BATAKARI`, `GONJA`.

---

## 8. Collecting against this

The tree says what to shoot; the columns say what to record.

- **Every class needs ≥3 groups before it can be measured at all.** One long
  session in one market is one group, however many photos it yields.
- **Spread sources deliberately:** different market days, different stalls,
  different towns, different collectors' phones.
- **Vary the view on purpose** — flat, folded, worn, market, close-up. A model
  trained only on flat-lay fails on a phone held over a stall.
- **Record at capture time.** Provenance cannot be reconstructed afterwards;
  this corpus is the proof.
