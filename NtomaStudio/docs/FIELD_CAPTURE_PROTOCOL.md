# Field capture protocol — first collection wave

**Goal of this wave:** the five classes with no inspiration look and the weakest
photo coverage, to a real target, in about a week.

| Class | Target | Have now | Usable now | Confusable with |
|---|---:|---:|---:|---|
| `ADINKRA` | 2,200 | 4 | **2** | NWOMU, KENTE_PRINT, BATIK |
| `SATIN_SILK` | 1,100 | 5 | 5 | BROCADE_BAZIN, CHIFFON_GEORGETTE |
| `CHIFFON_GEORGETTE` | 1,000 | 5 | 5 | ORGANZA_TULLE, SATIN_SILK |
| `VELVET` | 800 | 5 | 5 | CREPE, DENIM |
| `DENIM` | 700 | 5 | **2** | VELVET, COTTON_PLAIN |
| | **5,800** | 24 | 19 | |

**5,800 photos ÷ 200/day/collector = 29 collector-days.** With 5 collectors that
is **about 6 working days**; with 3, about 10.

"Usable now" is not a guess — `check_dataset.py` found that 2 of the 4 ADINKRA
photos are market scenes with people rather than cloth, and 3 of the 5 DENIM
photos are Alamy stock (image IDs `W8K6RY` and `W1W51H`; one of the three is a
copy of another with the credit bar cropped off, confirmed by correlation 0.86
against 0.16 for a genuine control pair). Those have to be replaced before
anything is measured.

This wave matters beyond its own classes: it is the rehearsal for the full 50,000.
Run it, and the protocol is either proven or corrected at 5,800 photos instead of
50,000.

---

## The four non-negotiables

Every one of these exists because `check_dataset.py` caught the opposite on the
first 83 photos.

1. **Own capture only.** No downloaded images, ever. Two of your 83 turned out to
   be watermarked stock with a photographer's licence attached — see
   `docs/DATASET_AUDIT.md`. If a photo was not taken by the team, it does not
   enter `raw/`.
2. **Record `source` and `session` at capture time**, in the field. All 83
   delivered photos arrived with zero EXIF and one uniform encoder signature, so
   provenance was unrecoverable and the train/val split collapsed to a single
   session. This is the one thing that cannot be fixed afterwards.
3. **Flat cloth in real conditions**, not studio product shots. 14 of the 83 were
   white-backdrop catalogue shots. A model trained mostly on those learns the
   backdrop, then fails on a phone held over a stall — which is where it will
   actually be used.
4. **Never delete the original.** Rename and file, but keep the untouched file.
   Every conversion loses the thing you will later want.

---

## On the day

### Before the first shot

- Create the session folder on the phone and name it `source_yyyy-mm-dd` (e.g.
  `kejetia_14_2026-10-03`). One session = one source on one day.
- Turn **location off** but keep the camera app's default quality settings. Do
  not use a beauty/scan app, and do not let anything re-compress on share.

### While shooting

- **Fill the frame with cloth.** A person may hold or wear it, but the cloth must
  be the subject — no portraits, no shopfronts, no crowd scenes.
- **Shoot 4–6 angles of each distinct cloth** (flat, folded, held up to light,
  close-up, worn). Those are one *group*; they are not four sessions.
- **Keep the second angle in the same session.** Ten minutes later, same cloth,
  same stall = same group. A new group needs a different source or a different day.
- Make sure the plug is in frame at least once per cloth — the selvedge print
  (`veritable`, `GTP`, `ATL`, `PRINTEX`) is a `brand_family` value that is
  otherwise unrecoverable.
- Shoot at the phone's **maximum resolution**, hold steady, and avoid the digital
  zoom. Blur and tiny files are the two commonest rejections.

### Before leaving the stall

Run down the rejection list below and reshoot anything that fails. It is much
cheaper here than from Kumasi by WhatsApp.

---

## Rejection list — delete on the spot

| Reject if | Because |
|---|---|
| Any watermark, logo or `alamy`-style credit bar | Licensed stock, not ours. This is the defect that already cost 3 photos. |
| White/seamless studio backdrop | Teaches background, not cloth. 14 of 83 were this. |
| Short edge under 224px | Upscaled noise at the 224px training input. 3 of 83. |
| Flat, featureless frame with no visible structure | Nothing to identify. 2 of 83 — both satin/chiffon, which is why those two classes need deliberate angled light. |
| Blurry, or motion-smeared | Not recoverable. |
| The cloth is not the subject | Market scenes and portraits do not teach fabric. 2 of 83 ADINKRA photos. |
| Blown-out white sheen covering the weave | Detail is gone where it matters most, on the shiny classes. |
| A near-copy of a shot already taken | Same information twice. |

`check_dataset.py` automates every row of that table except "is the cloth the
subject", which needs eyes.

---

## Shot list for this wave

Each entry gives the target, what actually distinguishes the class in a
photograph, and what to vary so the model generalises.

### ADINKRA — 2,200 · Ntonso Adinkra Village, Kumasi, Bono Region

**Distinguishes it:** symbols stamped in a grid on a woven ground, with border
bands. Closest confusions are NWOMU, KENTE_PRINT and BATIK.

- Grid of stamped symbols, uncut length, close enough to read one symbol
- Border pattern bands, which are often the giveaway versus BATIK
- Ground colours: charcoal, brown, red, multicolour — **one group each minimum**
- Brisi (plain black, no symbols) — deliberately, for the NWOMU boundary, and
  expect it to be hard
- The stamping in progress, and the dried cloth hanging at the village
- Do not let this class become only black cloth; the literature's colour names
  are variants of one visual family

### SATIN_SILK — 1,100 · Makola, Kaneshie

**Distinguishes it:** a smooth, liquid, continuous sheen with no visible weave.
Confused with BROCADE_BAZIN and CHIFFON_GEORGETTE.

- Cloth angled across the light so a **highlight gradient** runs across the frame —
  the sheen is the label, and flat-on shots of satin look like nothing
- Draped over a hand or a rail, so the fold highlights read
- Close-up of the surface, showing that there is no weave and no pattern
- Colour range: champagne, cream, black, jewel tones
- Avoid: the near-featureless frame. If the frame has no highlight and no fold,
  delete it and shoot again

### CHIFFON_GEORGETTE — 1,000 · Makola, Kaneshie

**Distinguishes it:** semi-sheer cloth that lets light and shape through.
Confused with ORGANZA_TULLE and SATIN_SILK.

- **Held up against light** with a hand or an arm behind it — translucency is the
  label and can only be shown that way
- Layered over itself, and layered over another cloth, to show the see-through
- Gathered or ruffled, since it is nearly always used gathered
- Close-up of the open weave
- Avoid: a frame with neither translucency nor a fold. That was the second
  near-featureless photo in the existing set

### VELVET — 800 · Makola, Kejetia

**Distinguishes it:** a dense pile that catches light directionally, with a
crushed bloom. Confused with CREPE and DENIM.

- **Raking light** so the pile reads as a bright sheen that shifts with angle
- Crushed and folded, showing the nap changing direction
- Colour range: the existing 5 are all dark (luminance 26–75), which is exactly
  why the paler velvets need collecting — emerald, burgundy, gold
- Worn as a dress or a full length, since that is the use case
- Close-up of the pile texture, one frame

### DENIM — 700 · Kantamanto, Makola

**Distinguishes it:** a diagonal twill weave, usually indigo. Confused with
VELVET and COTTON_PLAIN.

- Close-up enough that the **diagonal twill line is visible** — this is the
  discriminator against plain cotton
- Flat, on a rail, and worn as a garment
- Indigo, black, light-wash and stone-washed variants
- The reverse face, which is paler
- Replace the three stock DENIM photos first. `DENIM/02.jpg` (Alamy `W8K6RY`)
  and `DENIM/03.jpg` (Alamy `W1W51H`) carry the agency's credit bar in-frame;
  `DENIM/01.jpg` is `02` with that bar cropped off. All three out.

---

## Views to cover in every class

Aim for roughly this spread across each class's photos. `view` is a metadata
column, not a folder — record it, do not file it.

| Share | Views |
|---|---|
| ~30% | `flat_lay`, `folded` |
| ~20% | `close_up`, `macro_texture` |
| ~20% | `hanging`, `rolled` |
| ~20% | `worn_by_person`, `dress`, `skirt`, `shirt`, `trousers`, `kaba_and_slit` |
| ~10% | `market`, `shop`, `partial_occlusion` |

Also vary `lighting` on purpose: natural shade, natural sun, indoor LED,
indoor fluorescent, flash. Market reality is the target, so the sunlit-stall shot
matters as much as the clean one.

---

## After the session

```bash
# 1. copy the session in, keeping the originals
#    raw/<CLASS>/<CLASS>_<source>_<yyyy-mm-dd>_<seq>.jpg

# 2. add one row per photo to raw/metadata.csv
#    source and session are required; the rest is ten seconds each
python3 tools/dataset/init_dataset.py --check

# 3. the gate - run it every session, not at the end
python3 tools/dataset/check_dataset.py

# 4. counts against the plan
python3 tools/dataset/track_collection.py
```

`check_dataset.py` exits 1 on anything that would poison training: unreadable
files, photos in a folder that is not a schema class, and **the same cloth filed
under two different classes**. It exits 0 with warnings for the things a human
should judge — studio shots, credit bars, dark or featureless frames.

**Run it per session.** A defect found on day one costs a reshoot of one stall; the
same defect found at 5,800 costs the wave.

---

## What "done" looks like for this wave

- 5,800 photos in the five class folders, `check_dataset.py` with **0 blockers**
- Every photo carrying `source` and `session`, and every class with **≥3 groups**
  so it can be split honestly and get an accuracy number
- No watermarks, no studio backdrops, no frames below 224px short edge
- All five classes present in the `view` and `lighting` spread above
- The three Alamy DENIM photos gone, and the 2 unusable ADINKRA photos replaced

At that point the five classes have real photos behind them, the five staged
illustrations in `tools/dataset/synthetic/looks/` become the look section for
classes that currently show nothing, and the same protocol scales to the
remaining 21 classes.
