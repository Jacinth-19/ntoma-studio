# Catalog & dataset audit — does the data match its folders?

**Date:** 2026-09-24 · **Scope:** `app/src/main/assets/catalog/` (15 fabric photos, 12 look
illustrations, 2 JSON catalogs) + `tools/dataset/` training data · **Method:** every file opened
and inspected; every label cross-checked against `FabricCategory` (`Fabric.kt`),
`looks.json`, `values/strings.xml` and its 3 locale overlays.

Reproduce with:

```bash
python3 tools/dataset/verify_catalog.py          # human report, exit code for CI
python3 tools/dataset/verify_catalog.py --json   # machine-readable
```

---

## 1. Verdict

**The folders and the data agree structurally — but one file pair was mislabelled, and the
"kente folder holds kente" assumption does not hold for every other folder.** The wiring is
sound (no missing assets, no orphans, every string key resolves in all 4 locales). The
problem is *content*: several photos do not depict the class they are filed under, and the
15 fabric photos are the one part of this catalog with **no licensing record at all**.

| Question | Answer |
|---|---|
| Does every declared asset exist? | **Yes** — 15/15 resolve, 0 missing |
| Any orphan files nobody references? | **No** — 0 orphans, 27/27 files accounted for |
| Do the 12 looks match their metadata? | **Yes** — all assets, fabrics tags, genders and string keys resolve; illustrations match their titles |
| Does each photo match its folder name? | **No** — 1 pair swapped (fixed), 4 more flagged below |
| Is the data licensed/documented? | **No** — 15/15 fabric photos have zero provenance record |
| Enough data to train a production model? | **No** — `raw/` is empty; 0 of 17 classes have training data |

---

## 2. File-by-file correspondence

### `catalog/fabrics/` — 15 files ↔ 18 `FabricCategory` members

No orphan files, no dangling references. Three members declare no asset **by design**
(`FUGU`, `GONJA`, `UNKNOWN` → `DiscoverScreen` falls back to a generated vector swatch).
Every file on disk is claimed by exactly one category.

| File | Category | Px | Label accurate? |
|---|---|---|---|
| `kente.jpg` | KENTE | 300×300 | Yes — authentic hand-woven strip cloth, visible weft-faced blocks |
| `kente_print.jpg` | KENTE_PRINT | 300×300 | Yes — printed imitation, flatter and more liquid than the woven swatch |
| `adinkra.jpg` | ADINKRA | 640×640 | **Weak** — red/black printed damask with radiata-flower medallions, no `Adinkra` symbol set (`Gye Nyame`, `Sankofa`…). Reads as printed furnishing cloth |
| `wax.jpg` | WAX | 640×480 | Plausible — batik-style ring-dot border and geometric blocks on white |
| `ankara.jpg` | ANKARA | 640×480 | **Weak** — print reads as a *kente-motive* print (strip motifs, two rows). Overlaps the `kente_print.jpg` concept |
| `batik.jpg` | BATIK | 340×270 | **Was wrong → fixed** (see §3) |
| `brocade.jpg` | BROCADE | 340×270 | **Was wrong → fixed** (see §3) |
| `tiedye.jpg` | TIEDYE | 600×600 | Yes — radiating rings, bound-and-dyed marble |
| `lace.jpg` | LACE | 300×300 | Yes — white openwork guipure, correct motif |
| `cotton_plain.jpg` | COTTON_PLAIN | 427×640 | Yes — even matte single-colour weave |
| `linen.jpg` | LINEN | 612×408 | Yes — slubby natural weave |
| `silk.jpg` | SILK | 640×640 | Yes — smooth reflective drape |
| `chiffon.jpg` | CHIFFON | 300×300 | **No** — heavy printed fabric with body; in-app description is "sheer, airy weave that moves with the body". This is opaque and stiff. Looks like printed silk/wax |
| `velvet.jpg` | VELVET | 612×612 | **No** — matte emerald green with a pressed herringbone crease, no pile or lustre. Reads as green cotton/poly. The app's own velvet palette is deep wine (`0xFF4A0E1C`), so the code disagrees with the photo too |
| `denim.jpg` | DENIM | 324×432 | Yes — indigo twill, diagonal weave visible |

**Presentation is inconsistent:** `adinkra`, `ankara`, `wax` are white-background studio
product shots with margins; the other 12 are full-frame flat-lay/drape photos. In the
Discover grid that reads as shop listings next to photographs.

### `catalog/looks/` — 12 files ↔ 12 `looks.json` entries

**Clean.** All 12 assets exist, all 12 `title_key` values resolve in
`values/strings.xml` and in all three locale overlays (`values-ee`, `values-gaa`,
`values-tw`), all fabric tags are real enum members, all genders are valid. The
illustrations genuinely match their titles (kente groom in a draped cloth over
white, northern smocks in indigo and ochre strip, white lace gowns, ankara
two-pieces). No orphans, no duplicates against the fabric swatches.

A test already guards this (`InspirationLooksTest`), but it only checks that assets
*open* — it never checks that the *content* matches the label. That is exactly how the
batik/brocade swap survived.

---

## 3. Fixed in this pass

`batik.jpg` and `brocade.jpg` held each other's image.

- `batik.jpg` contained the grey/teal **damask** — a weave, not a resist dye.
- `brocade.jpg` contained the multi-colour **wax-resist strip** — a print, not a
  lustrous jacquard.

Swapped the file contents so each name matches its photo (`FabricCategory` needed no
change, no code touched, no test affected — this is asset-content only). Verified by
`md5sum` before/after: the two hashes exchanged places.

---

## 4. Coverage gaps (structural, not mislabelling)

### 4.1 Five classes have no inspiration look

`FUGU` and `GONJA` **have no swatch**, and five classes have **no look at all**:

| Category | Looks | Effect in the app |
|---|---|---|
| `ADINKRA` | 0 | `RecommendationsScreen` hides the entire looked section |
| `SILK` | 0 | same |
| `CHIFFON` | 0 | same |
| `VELVET` | 0 | same |
| `DENIM` | 0 | same |
| `FUGU`, `GONJA` | 2 each | look present, but no swatch in Discover → vector fallback |

`state.looks.isNotEmpty()` gates the whole block, so a user who scans silk, chiffon,
velvet or denim gets **no design inspiration at all** — on four of the most common
everyday fabrics. `UNKNOWN` has 0 looks by design (it matches everything).

### 4.2 Gender coverage is thin

Only **6 of 18** categories have both a men's and a women's look. `LACE` and `BROCADE`
are women-only; `COTTON_PLAIN`, `LINEN`, `FUGU`, `GONJA` are men-only. A man scanning
lace, or a woman scanning fugu, sees an empty section even though the looks exist for
the *other* gender.

### 4.3 An honesty claim that is only half true

`looks.json` states the 12 entries "replace earlier web-sourced reference photos".
That is true for the **looks**. It is not true for the **fabric swatches**: at least
13 of the 15 are web-sourced photographs with no source field, no licence and no
author field — and, at the time of writing, no manifest listing them.

---

## 5. Rights and provenance — the real production blocker

There is **no licence, attribution or provenance file anywhere in the repository**, and
`catalog/` has no `fabrics.json` manifest (unlike `looks.json`, which does carry a
`source` field per entry).

Direct evidence that these came from vendor listings:

| Asset | Evidence |
|---|---|
| `lace.jpg` | literal price/SKU stamp **"2820"** burned into the top-right corner |
| `adinkra.jpg` | printed brand "**W-H 9106**" in the selvedge, a plastic swing tag and a red thread tie — a photographed retail product; plus a white studio backdrop |
| `ankara.jpg`, `wax.jpg` | white-background studio product shots, same source pattern |
| all 15 | EXIF stripped, no camera make/model, no capture date, no author |

Shipping these inside the APK is **distribution**, not private reference use. This is
the single item on the release checklist that can actually stop a Play Store release
(and `docs/capability_answers.md` §2 already shows the team knows this — the looks were
replaced for exactly this reason, but the fabric photos were not).

**Recommendation:** treat the 15 fabric photos the same way the looks were treated.
Either (a) commission or generate brand illustrations for all 17 classes, (b) shoot
them in-house, or (c) obtain written licences and record them in a
`catalog/fabrics.json` manifest with a `source` field per file — then add that
manifest check to CI. `verify_catalog.py` already fails the "no manifest" check.

---

## 6. How much data is actually needed?

Two very different numbers, and conflating them is how projects stall.

### 6.1 Browse/catalog content — cheap, high ROI, do it first

| Need | Target | Have | Gap |
|---|---|---|---|
| Fabric swatches (one per real class) | 17 | 15 | **2** (FUGU, GONJA) |
| Looks — full coverage at 2 per fabric per gender | 17 × 2 × 2 = **68** | 12 | **56** |
| Effects | every scan shows a design section | 5 classes show nothing | — |

**56 illustrations** closes every gap in §4. They are generated in the house
vector style already proven by the existing 12, and they are the cheapest possible fix
for the most visible defect. At ~30 KB as WebP, 68 looks ≈ **2 MB** in the APK —
roughly what the current 12 JPEGs already cost.

### 6.2 The classifier — expensive, and needs *far* more than 300/class

`label_schema.json` sets the floor at **300 photos/class** and the honesty bar at
**1,000/class** before publishing accuracy. Those are *training* numbers, not
*production* numbers, and this repo already has the evidence for why:

> `train_starter.py` run of 2026-09-01 on 83 web-sourced photos: **MACRO 0.419**, but
> **KENTE 0.00, GONJA 0.00, BATIK 0.00**.

The classes that failed are precisely the ones that matter culturally and the ones that
are *visually near-identical* to a neighbour. Nothing about adding photos to
`COTTON_PLAIN` fixes that.

**The binding constraint is the confusable pairs, not the total:**

| Hard pair | Why it is hard | Photos needed per class |
|---|---|---|
| `KENTE` vs `KENTE_PRINT` | same motifs, different construction | **3,000–5,000** |
| `FUGU` vs `GONJA` | same narrow-strip weave, palette differs | **2,000–3,000** |
| `WAX` vs `ANKARA` | overlapping print vocabularies | **2,000–3,000** |
| `BATIK` vs `TIEDYE` | both resist-dyed, soft edges | **2,000–3,000** |
| `ADINKRA` | symbol-based, needs its own symbol coverage | **1,500–2,500** |
| `LACE`, `BROCADE`, `SILK`, `CHIFFON`, `VELVET`, `DENIM`, `LINEN`, `COTTON_PLAIN` | visually distinct, transfer learning carries them | **1,000–1,500** |
| **`OTHER` / `UNKNOWN` (negative class)** | so the model can honestly refuse | **10–20 % of the corpus** |

**Production-grade total: ≈ 43,000 fully labelled, licence-cleared photographs**
(~32 k for the 8 hard classes, ~11 k for the 9 easy ones), plus ~5,000 for the negative
class. At 224 px / ~40 KB that is **~1.7 GB** of raw imagery — external storage or
`rclone`-backed bucket, never Git (the `.gitignore` already excludes
`tools/dataset/raw/`, which is correct).

**If you only need a defensible v1:** 1,000/class × 17 = **17,000 photos**, shipped
with top-3 suggestions and a retained `UNKNOWN` fallback. That is a real feature, not a
demo — but it will still be weak on KENTE/KENTE_PRINT.

**Validation split matters as much as volume.** Hold out by *capture session*, not by
augmented variant — the starter script already does this correctly (`train_starter.py`
holds out one real photo per class), and that is why its 0.419 is trustworthy. Also
keep a device/camera-split test set (photos from phones and lighting conditions never
seen in training) or the shipped accuracy will not match the reported accuracy.

### 6.3 Data the other features need

| Feature | Data volume needed |
|---|---|
| Person segmentation (DeepLabV3 257) | shipped, 2.8 MB, **0 training data** |
| Colour/pattern/texture analysis | algorithmic, **0 training data** |
| Recommendation engine | rule-based over `dress_styles.json` (20 styles), **0 training data** |
| Cloud try-on / generative | no local data — needs a paid VTON endpoint + per-image cost and latency budget |
| i18n (`ee`, `gaa`, `tw`) | already at 824 keys, fully covering all 20 styles and 12 looks |

So the only two data-bound features are the **classifier** and the **browse catalog**.

---

## 7. Priority list

| # | Action | Cost | Unblocks |
|---|---|---|---|
| 1 | Replace the 15 fabric photos with licensed/in-house/illustrated equivalents; add `catalog/fabrics.json` with a `source` per file; fail CI on a missing manifest | low | Play Store release |
| 2 | Add 56 looks (17 fabrics × 2 genders × 2 looks = 68 target) to close §4.1 and §4.2 | low | 5 classes showing nothing |
| 3 | Re-shoot/replace `velvet.jpg`, `chiffon.jpg`, `adinkra.jpg`, plus `ankara.jpg` vs `kente_print.jpg` disambiguation | low | Label correctness |
| 4 | Add the 2 missing swatches (`FUGU`, `GONJA`) | trivial | Consistent Discover grid |
| 5 | Make asset presentation uniform (all flat-lay, or all studio) | low | Visual coherence |
| 6 | Extend `InspirationLooksTest` to assert **content**, not just openability; add `verify_catalog.py` to CI | low | Regression protection |
| 7 | Collect 1,000/class → train a real v1; then scale the 8 hard classes to 3–5 k | high (field collection) | Real classifier |

Steps 1–6 are days of work and remove every correctness and legal defect in the shipped
catalog. Step 7 is the multi-month field campaign — and the infrastructure for it is
already built and smoke-tested (`ingest.py`, the in-app contribution flow, dHash dedupe,
the export manifest). Only the photos are missing.

---

## 8. Method notes

- Every one of the 27 catalog images was opened and visually inspected at native and
  enlarged resolution, not inferred from filenames.
- Label correspondence was parsed out of the Kotlin source (the `FabricCategory` enum),
  so it cannot drift from what the app actually ships.
- Duplicate detection used dHash; detection of vendor stamps required confirmed-flat
  backdrop behind the saturated pixels, because a naive "saturated corner" test flagged
  genuine kente and tie-dye as false positives.
- "Wrong label" judgements in §2 are visual assessments and could be debated for
  `wax`, `ankara` and `adinkra`; the batik/brocade swap and the `lace.jpg` stamp are
  objective.
- Not verifiable from this environment: on-device rendering, and the actual copyright
  status of the 15 photographs (no records exist to check).
