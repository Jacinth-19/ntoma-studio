# Do we have all the forms of fabric in Ghana?

**Date:** 2026-09-24 · **Scope:** Ghana only · **Question asked:** does the app's current
17-class taxonomy cover every fabric form a Ghanaian user could photograph?

**Answer: no.** The current taxonomy covers the *imported dress-fabric* half of the
Ghanaian market well and the *heritage* half badly. Eight fabric forms that are
unambiguously Ghanaian are missing entirely, and the two classes that carry the most
cultural weight — kente and wax print — are each defined by *name* rather than by
*construction*, which is precisely the distinction a Ghanaian buyer cares about.

A v2 taxonomy of **25 classes** is proposed below, with the rationale and sources for
each change, plus the 50,000-image collection plan that reaches it.

---

## 1. What is already right

Worth stating plainly, because most of the taxonomy holds up:

| Class | Verdict |
|---|---|
| `FUGU`, `GONJA` | Correct, correctly separated, and correctly described as northern hand-loomed strip cloth |
| `ADINKRA` | Correct — stamped with calabash-cut symbols, mourning and ceremony |
| `KENTE` | Right idea, but see §3.1 — it conflates construction with motif |
| `BATIK`, `TIEDYE` | Correct and correctly distinguished as resist-dye methods |
| `LACE`, `BROCADE`, `SILK`, `CHIFFON`, `VELVET`, `DENIM`, `LINEN`, `COTTON_PLAIN` | Correct *as market classes* — all are genuinely traded in Ghana in volume, even though none is indigenous |
| `UNKNOWN` | Correct, and load-bearing — most fabric apps omit it and then cannot refuse |

The commit that added `FUGU` and `GONJA` was a real improvement: those are the two
classes a generic "African print" app always misses, and northern Ghanaian strip cloth
is a large share of the market.

---

## 2. What is missing — eight Ghanaian fabric forms

Grounded in the indigenous-textile literature, which lists Ghanaian textiles as *Kete,
Fugu, Tie-dye, Batik, Nwomu, Kobine, Brisi, Kuntunkuni, Adinkra cloth, Obama
(embroidery cloth) and Tapestry* [8](https://eajess.ac.tz/wp-content/uploads/2023/03/EAJESS-4-1-0252.pdf):

| Missing | What it is | Why it matters |
|---|---|---|
| **Kete (Ewe kente)** | The Ewe equivalent of kente, woven at Kpetoe. Called *kete* in Ewe, *Agbamevor*; brighter, more symmetric and often figural than Ashanti block kente [1](https://www.smashnegativity.com/ghana-traditional-clothing/) | **The app currently files Ewe cloth under `KENTE` and calls it Ashanti.** Ewe Ghanaians are a large share of the Volta Region market. This is a cultural-accuracy bug, not just a taxonomy gap |
| **Nwomu** | Stamped cloth from the Ntonso craft tradition [8](https://eajess.ac.tz/wp-content/uploads/2023/03/EAJESS-4-1-0252.pdf) | A distinct technique adjacent to adinkra that the current schema has no name for |
| **Obama (embroidery cloth)** | Embroidered cloth, listed as indigenous Ghanaian textile [8](https://eajess.ac.tz/wp-content/uploads/2023/03/EAJESS-4-1-0252.pdf) | Missing entirely; visually overlaps lace and brocade, so it cannot be safely absorbed |
| **Real wax vs fancy print** | Real Dutch wax (Vlisco, GTP, Woodin, Uniwax, ATL, ABC) is printed on **both faces**; the Chinese/Indian reproductions (Hitarget, Imiwax, Phoenix, "fancy print") are printed **one side only**, on thinner cotton, and fade faster [5](https://jessi.tips/blog/accra-fabrics) | **The single most commercially important distinction in the Ghanaian market.** Price differs 2–4×. See §3.2 |
| **Java print** | Industrial print imitating Indonesian batik, sold alongside wax [8](https://smartbuy.alibaba.com/buyingguides/ghana-wax-fabric) | Currently gets absorbed into wax or batik, poisoning both |
| **Crepe** | Crepe de chine / moss crepe — a standard Ghanaian dress fabric | Missing; the app jumps from chiffon to velvet |
| **Organza / tulle** | Sheer bridal and net fabrics | Missing, and weddings are a primary use case for this app |
| **Seersucker** | Sold in the market as a summer apparel cloth [2](https://www.hiltontextiles.com/pages/ghana-fabrics-in-todays-market) | Missing |
| **Kente tapestry / jacquard** | Machine-jacquard kente look-alike used for furnishing, made into full-length "Northern Kente" and tapestry panels [3](https://edwardasare.com/celebrating-5-ghanaian-fabrics-that-teach-history/) | If unmodelled it will be absorbed into `KENTE_PRINT` and drag that class's precision down |

### 2.1 The adinkra colour variants — a deliberate *non*-addition

The literature names three adinkra colour types: **Kobene** (brick red), **Kuntunkuni**
(dark brown, dyed with the bark of *Bombax brevicuspe*) and **Brisi** (black or indigo,
sometimes carrying no symbols at all) — all associated with funerals
[6](https://www.africanbeadsandfabrics.com/knowledge/adinkra-cloth/).

These are **not** added as three classes. They are one visual family distinguished by
colour and occasion, so they are modelled as a `colour_variant` attribute on `ADINKRA`.
Adding them as classes would triple the annotation cost for a distinction the model can
learn from colour, and would create a near-impossible three-way confusion between
Kuntunkuni, Brisi and every dark fabric in the corpus.

`BRISI` with no symbols is the one that needs watching — it is effectively a plain
black cloth and will fight `VELVET`, `DENIM` and dark `COTTON_PLAIN`.

---

## 3. What is actually *wrong* — two classes defined by name instead of construction

### 3.1 `KENTE` should be defined by weave, not by motif

The taxonomy treats "kente" as one thing. In the market it is at least three:

- **Ashanti kente** — hand-woven strip cloth, Bonwire and Adanwomase [1](https://www.smashnegativity.com/ghana-traditional-clothing/)
- **Ewe kete / Agbamevor** — hand-woven, Kpetoe, visually distinct [1](https://www.smashnegativity.com/ghana-traditional-clothing/)
- **Kente print** — machine-printed cotton imitating the motifs, usually Chinese, GHS 30–80/yard against GHS 300–1,500 for a hand-woven strip [3](https://www.ghanacitizenship.com/traditional-ghanaian-clothing/)

The honest discriminator is **construction** — weft floats, strip seams, visible
hand-beaten weft — not the motif, because the motifs are copied exactly onto the print.
An earlier dataset audit already found the app's own `kente.jpg` had been swapped with a
printed cloth's image, which is exactly the error this distinction prevents.

This also matters beyond the app: the best published kente-specific work
(Authente-Kente, ~88%) treats authenticity as its own task, and the repo's own
`label_schema.json` warns against mixing woven and printed samples in one class. The
current class structure contradicts that warning.

**Also worth knowing:** northern strip cloth is increasingly sold as **"Northern Kente"**,
an alternative to Ashanti/Ewe kente [3](https://edwardasare.com/celebrating-5-ghanaian-fabrics-that-teach-history/).
That is *fugu* construction under a kente name — a second way the word "kente" does not
mean one thing.

### 3.2 `WAX` and `ANKARA` are not two fabrics

The app currently ships both, described almost identically:

- `fabric_wax_desc` → "Vibrant wax-resist printed cotton, the everyday fabric of West African dressmaking."
- `fabric_ankara_desc` → "Bold all-over printed cotton with saturated colour on both faces."

In Ghana, **"ankara" names the whole category** — both real wax and cheap prints. They
are synonyms in everyday speech, not classes. Meanwhile the distinction that traders,
tailors and buyers actually make is:

| Real wax | Fancy print |
|---|---|
| Dutch wax, GTP, Woodin, ATL, ABC, Uniwax | Hitarget, Imiwax, Phoenix, "fancy print" |
| Printed on **both faces** with registration | Printed **one side only** |
| Heavier cotton, slower fade | Thinner, fades faster [5](https://jessi.tips/blog/accra-fabrics) |
| ~GHS 300–800 / 12 yd | ~GHS 60–200 / 12 yd |

So `WAX` + `ANKARA` should become `WAX_REAL` + `FANCY_PRINT`. Same two classes, but now
they are distinguishable by a test a user can perform (turn the cloth over) and the
model can learn (edge registration, reverse-side bleed-through).

Note the second description above already contains the right test — "saturated colour on
both faces". The taxonomy had the discriminator written down and then didn't use it.

---

## 4. Proposed v2 taxonomy — 25 classes

Implemented in `tools/dataset/label_schema.json` (v2). Grouped by what the fabric *is*:

| # | Class | Group | Origin | Tier |
|---|---|---|---|---|
| 1 | `KENTE_ASHANTI` | heritage woven | GH | 1 |
| 2 | `KETE_EWE` | heritage woven | GH | 1 |
| 3 | `FUGU_BATAKARI` | heritage woven | GH | 1 |
| 4 | `GONJA` | heritage woven | GH | 1 |
| 5 | `ADINKRA` | heritage stamped | GH | 2 |
| 6 | `BATIK` | heritage dyed | GH | 1 |
| 7 | `TIEDYE` | heritage dyed | GH | 1 |
| 8 | `NWOMU` | heritage stamped | GH | 2 |
| 9 | `OBAMA_EMBROIDERY` | heritage embroidered | GH | 2 |
| 10 | `KENTE_PRINT` | print market | GH/CN | 1 |
| 11 | `WAX_REAL` | print market | NL/GH | 1 |
| 12 | `FANCY_PRINT` | print market | CN/IN | 1 |
| 13 | `JAVA_PRINT` | print market | ID/CN | 2 |
| 14 | `LACE` | dress & bridal | IMP | 2 |
| 15 | `BROCADE_BAZIN` | dress & bridal | IMP | 2 |
| 16 | `SATIN_SILK` | dress & bridal | IMP | 3 |
| 17 | `CHIFFON_GEORGETTE` | dress & bridal | IMP | 3 |
| 18 | `CREPE` | dress & bridal | IMP | 3 |
| 19 | `ORGANZA_TULLE` | dress & bridal | IMP | 3 |
| 20 | `VELVET` | dress & bridal | IMP | 3 |
| 21 | `LINEN` | everyday | IMP | 3 |
| 22 | `COTTON_PLAIN` | everyday | GH/IMP | 3 |
| 23 | `DENIM` | everyday | IMP | 3 |
| 24 | `SEERSUCKER` | everyday | IMP | 3 |
| 25 | `TAPESTRY_JACQUARD` | other | GH/CN | 3 |
| — | `UNKNOWN` | negative | — | 0 |

The grouping is load-bearing, not decorative: it makes the **origin** of each class
explicit so the app never presents Vlisco as Ghanaian, and it makes the **tier**
explicit so collection effort follows difficulty.

### 4.1 Migration — and a trap that had to be defused

**Adding** classes is harmless: `ingest.py` only checks membership, so a new class
cannot break anything already ingesting.

**Renaming** classes is not. Seven v1 names are gone (`KENTE`, `WAX`, `ANKARA`,
`BROCADE`, `SILK`, `CHIFFON`, `FUGU`). A contribution zip exported by the *currently
shipped* app carries v1 names, so on first applying this schema, `ingest.py` rejected
**7 of 17 categories as "unknown category"** — every kente, wax, ankara, brocade, silk,
chiffon and fugu photo a user had contributed. That is a live data-loss path, and it was
found by testing an actual v1-named zip rather than by reasoning about it.

The fix is an `aliases` map in the schema that `ingest.py` resolves before validation.
Four renames are lossless and map straight across:

| v1 | v2 | Safe? |
|---|---|---|
| `BROCADE` | `BROCADE_BAZIN` | yes, same fabric |
| `SILK` | `SATIN_SILK` | yes |
| `CHIFFON` | `CHIFFON_GEORGETTE` | yes |
| `FUGU` | `FUGU_BATAKARI` | yes |

Three are **not** lossless and are deliberately routed to `raw/_REVIEW/` instead of
being guessed at:

| v1 | Why it cannot be auto-mapped |
|---|---|
| `KENTE` | v1 said "Ashanti and Ewe traditions" — the woven/printed split survives, but Ashanti vs Ewe is genuinely unrecoverable from the photo |
| `WAX` | v1 named the whole printed-cotton category; real (double-sided) vs fancy (single-sided) needs the turn-the-cloth-over test by a human |
| `ANKARA` | v1 described it as "saturated colour on **both** faces" — which is the *real wax* marker — while v1 `WAX` was described generically. **The two v1 classes contradicted each other**, so neither label can be trusted |

Ambiguous photos are **kept, not discarded**, parked with a reason string, and excluded
from `track_collection.py` progress until a human re-files them. Guessing would have
silently poisoned `WAX_REAL`/`FANCY_PRINT` with mislabelled data — the exact failure mode
this taxonomy exists to prevent.

Verified end-to-end with a synthetic v1-named zip: 4 accepted into v2 classes, 3 parked
in `_REVIEW/` with reasons, 1 genuinely-bad category correctly rejected.

The Kotlin `FabricCategory` enum is a separate change and must follow before users can
contribute the new classes, because the contribution picker reads
`FabricCategory.values()`. The alias map and the ordering constraint are both recorded in
the schema's `aliases` and `migration_from_v1` blocks.

---

## 5. Does 50,000 photographs cover it?

**Yes — and 50,000 is the right number, arrived at independently.** The earlier audit
estimated ~43,000 for the confusable classes plus ~5,000 negatives ≈ 48,000. The plan
below comes to exactly 50,000 across 25 classes, weighted by difficulty:

| Tier | Classes | Target | Per class | Why |
|---|---|---|---|---|
| 1 — critical confusable pairs | 9 | 27,300 | 2,200–3,500 | KENTE_ASHANTI/KENTE_PRINT, FUGU/GONJA, WAX_REAL/FANCY_PRINT, BATIK/TIEDYE. These decide whether the model is shippable |
| 2 — moderate | 6 | 9,800 | 800–2,200 | Distinct but overlapping, or low market volume |
| 3 — visually distinct | 10 | 7,600 | 500–1,100 | Frozen ImageNet features already separate these |
| 0 — negative | 1 | 5,300 | — | ~10% of corpus, so the model can refuse |

**Why not flat 1,000/class.** The repo's own starter run is the evidence: 83 photos
across 17 classes gave **MACRO 0.419 overall, but 0.00 on KENTE, GONJA and BATIK**. Flat
allocation spreads effort across easy and impossible classes alike and reproduces that
result at 50× the cost. 2,000/class flat would leave the four critical pairs still
unlearnable.

Full detail — sources, phases, gates, budget, risks — is in
`tools/dataset/collection_plan.json`. Summary:

- **Sources:** weaver cooperatives (Bonwire, Adanwomase, Kpetoe, Tamale — the only source
  of construction ground truth), market days (Makola, Kaneshie, Kantamanto, Kejetia,
  Tamale Central), heritage workshops (Ntonso for adinkra/nwomu), distributor warehouses
  (the only unambiguous source for the double-sided wax test), the in-app contribution
  flow (already shipped), and tailor networks.
- **Effort:** 250 collector-days at 200 unique photos/day → 10 collectors for 5 field
  weeks, or 2 collectors for 25.
- **Budget:** ~GHS 166,000 (~US$14,400) all-in, ≈ $0.29 per photograph.
- **Storage:** 100–200 GB of originals in cold object storage; ~12 GB working set at
  512 px. `raw/` stays gitignored.

### 5.1 The two ways this number goes wrong

**Counting augmented images.** `augment.py` turns one photo into 10–16. Counting those
toward 50,000 multiplies information already in the corpus. `track_collection.py` counts
`raw/` only and prints the `augmented/` figure separately as a warning.

**Counting one market day as coverage.** Fifty photos of GONJA from a single session
share lighting, backdrop and stock. `track_collection.py` parses the capture date
already embedded in app-exported filenames, reports distinct dates per class, and flags
any class where one date exceeds 40% — which is both a generalisation risk and a
train/val leakage risk, since `train_starter.py` splits by held-out *photo*, not by
session.

---

## 6. Recommended order

| # | Action | Why now |
|---|---|---|
| 1 | Adopt `label_schema.json` v2 | Zero code risk, backward compatible, unblocks collection of the new classes |
| 2 | Split `WAX`/`ANKARA` → `WAX_REAL`/`FANCY_PRINT` in the app | Highest commercial value per unit of work; the discriminator is testable by the user |
| 3 | Add `KETE_EWE` and stop calling Ewe cloth Ashanti | Cultural accuracy; large affected population |
| 4 | Add the remaining 6 classes to `FabricCategory` + strings in all 4 locales | Needed before users can contribute them |
| 5 | Run the P0 pilot (2 collectors, 2,000 photos, 6 classes) through `ingest.py` → `train_starter.py` | Proves the pipeline on real photos before spending on scale |
| 6 | Scale to P1 with 10 collectors | This is the expensive, decisive phase |
| 7 | Add a held-out device test set from unseen phones and lighting | Otherwise reported accuracy will not match shipped accuracy |

The infrastructure for all of this already exists and has been smoke-tested. Nothing in
this document requires new engineering — only photos, and the taxonomy fix in step 1.

---

## 7. Sources

- [1] [Ghana Traditional Clothing: 9 Fashionable Types](https://www.smashnegativity.com/ghana-traditional-clothing/) — Gonja, kente, kete/Ewe, fugu/batakari, kaba and slit
- [3] [Celebrating 5 Ghanaian Fabrics That Teach History](https://edwardasare.com/celebrating-5-ghanaian-fabrics-that-teach-history/) — fugu aliases (Bun-nwo, Bana, Dansika), "Northern Kente", adinkra origin
- [3] [Where to Buy Traditional Ghanaian Clothing in Ghana](https://www.ghanacitizenship.com/traditional-ghanaian-clothing/) — market-level authenticity vs imitation, 2026 price indicators, Bonwire/Adanwomase/Kpetoe
- [5] [Insights on Ghanaian fabrics](https://jessi.tips/blog/accra-fabrics) — Vlisco group brands, hitarget/fancy print/imiwax, single-sided printing
- [6] [Adinkra Cloth](https://www.africanbeadsandfabrics.com/knowledge/adinkra-cloth/) — kobene/kuntunkuni/brisi colour variants, Bombax brevicuspe dye
- [8] [Promotion and Preservation of Indigenous Textiles in Ghana (EAJESS)](https://eajess.ac.tz/wp-content/uploads/2023/03/EAJESS-4-1-0252.pdf) — the canonical list of indigenous Ghanaian textiles
- [8] [How to Choose Ghana Wax Fabric](https://smartbuy.alibaba.com/buyingguides/ghana-wax-fabric) — fancy wax and java print as market variants
- [2] [Ghana Fabrics in Today's Market](https://www.hiltontextiles.com/pages/ghana-fabrics-in-todays-market) — batik, wax, seersucker retail reality
