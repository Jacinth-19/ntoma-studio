# Honest Capability Answers (updated 2026-09-01)

## 1. "Can the app detect ALL fabrics in Ghana with a sane accuracy?"
**No — and no app can honestly claim that.** What it does:
- Classifies into **17 fabric families** (Kente, Kente print, Adinkra, Wax, Ankara, Batik,
  Tie-dye, Lace, Brocade, Cotton plain, Linen, Silk, Chiffon, Velvet, Denim, **Fugu (batakari)**,
  **Gonja**) + honest **Unknown**.
- Pipeline: quantized MobileNetV2 (ImageNet hints) + **GLCM texture metrics** + a dedicated
  **kente-strip detector** + color analysis. Confidence is capped and results are labelled
  `ON_DEVICE_DEMO` until a real backend or fine-tuned model exists.
- **Fugu/Gonja detection (new)**: 2–3-hue narrow-strip weaves with muted palettes are now
  recognised; earth-toned (ochre/brown, judged from raw argb warmth) high-contrast strips
  classify as **Gonja**, cooler indigo/black/white strips as **Fugu**. Unit-tested with
  synthetic strip weaves.
- Research context: the best published kente-specific model (Authente-Kente) reaches ~88% on a
  narrow task with a curated dataset; handloom studies reach ~93% validation with 25k images.
  There is **no public Ghanaian-fabric dataset** — so we built the pipeline to make one
  (`tools/dataset/`, see §6).

## 2. "Can it truly pull images of dresses that match the fabric, by male and female?"
**Yes.** The Recommendations screen shows **"Looks for this fabric"**: 12 brand illustrations in
the house flat-vector style (kente kaba & slit, kente groom wear/agbada, white lace gowns,
northern fugu/batakari smocks, ankara two-pieces and shirts), filtered by the detected fabric
family and the gender chips, each shareable. Earlier web-sourced reference photos (pinterest/
etsy/amazon etc., some watermarked) were fully replaced by generated illustrations on 2026-09-01
for licensing safety. Labelled honestly as **brand illustrations, not renders of your fabric**
(`looks_section_caption`, enforced in `InspirationLook.matches` + tests).

## 3. Offline support
**Yes — the core app is fully offline.** Camera → analysis → designs → customize → try-on →
save → PDF brief → tailor list all run on-device. Network only for the optional (off-by-default)
cloud endpoint and catalog refresh (bundled fallback in assets).

## 4. Notifications
**Working, permission-gated, never spammy.** Three channels with per-channel toggles + Android 13
runtime permission; all deep-link into the app:
- **Processing** (high): "Look ready" + "Tailor brief ready" (PDF generated).
- **Recommendations** (default): background refresh. **Updates** (low): product updates.
Analysis completion deliberately has no notification (user is on-screen).

## 5. Favorites & sharing
**Both work.** Heart toggle → Favorites tab (fabrics + designs). Share flows for looks, designs,
fabric photos, inspiration look photos, and the PDF tailor brief (FileProvider).

## 6. Dataset & fine-tune pipeline — BUILT AND PROVEN
`tools/dataset/` (new):
- `label_schema.json` — 17 classes incl. FUGU/GONJA, folder layout, collection guidance
  (300+ photos/class before training, 1000+ before accuracy claims, market/weaver sourcing).
- `augment.py` — Pillow-only augmentation (crop/rotate/perspective/jitter). **Run: 27 raw
  photos → 162 augmented images.**
- `train_finetune.py` — MobileNetV3-Small fine-tune → quantized TFLite. **Smoke run executed
  end-to-end here (re-run 2026-09-02 on 83 raws / 830 augmented): exported
  `fabric_smoke.tflite` (1.14 MB); honest raw-image top-1 = 0.059 — chance level for 17
  classes, confirming nothing ships until real photos arrive.**
- `ingest.py` — consumes the app's contribution zip (manifest-validated, category-checked
  against `label_schema.json`, dHash-deduped) into `raw/<CLASS>/`. Dry-run by default,
  `--apply` writes. **Smoke-tested 2026-09-02**: synthetic zip with one unique photo, one
  exact duplicate and one bad category → 1 accepted / 1 duplicate skipped / 1 error.
- `train_starter.py` — **leak-free starter run, actually executed 2026-09-01**: 83 unique
  web-sourced photos (3–10/class, hash-deduped, irrelevant results discarded) → 1056 train /
  136 val images; validation held out **one real photo per class** (its augmented twins only),
  so the numbers are not inflated by leakage. Results on held-out real photos:
  **MACRO 0.419** (vs 0.06 random) — LINEN/SILK/TIEDYE 1.00, WAX 0.88, COTTON 0.75, but
  **KENTE 0.00, GONJA 0.00, BATIK 0.00**. Qualitative probe: kente photo → "ANKARA" (0.80).
  Exported `out/fabric_starter.tflite` (1.14 MB, 96×96→17 classes, LiteRT-verified).
- **Verdict: the starter model is NOT shipped.** It beats random 7× but is far below the
  honesty bar (README rule: no shipping under ~1000 images/class). The shipped classifier
  stays MobileNetV2 + GLCM + strip heuristics with ON_DEVICE_DEMO labelling. The experiment's
  value is the proven pipeline + a baseline number to beat once real field photos exist.

## 7. DeepLabV3 person segmentation — QA'd (as far as possible without a device)
`tools/qa_deeplabv3.py` runs the **exact shipped model asset** through the LiteRT interpreter
with the **exact preprocessing math of PersonSegmenter.kt**:
- Contract verified: input `[1,257,257,3]` f32 → output `[1,257,257,21]` f32, person = label 15.
- Flat-image sanity: 128/66049 noise person pixels (no misfire explosion).
- **Real full-body photos: PASS** — person masks of 13–26% of frame, centroids at (125,118) and
  (127,121) vs image centre 128,128, y-extent head→toes (13→241).
Still device-only: GPU delegate behaviour, memory pressure, bitmap scaling paths (no emulator
available in this environment — no KVM).

## 8. AI model that truly understands Ghanaian dress
Not available off-the-shelf (see research doc §2). The path is now concrete: collect via
`label_schema.json` guidance → `augment.py` → `train_finetune.py` → review per-class accuracy →
swap the asset behind the existing `AnalysisEngine` abstraction. Optional later: Authente-Kente-
style authenticity check and Genlook-class generative try-on behind the cloud flag.

## 9. Wardrobe background removal — SHIPPED (2026-09-01)
`media/BackgroundRemover.kt` (pure, unit-tested ×3): border-median background estimate,
tolerance cut, largest-connected-component keep (specks/shadows vanish). Wired into the
wardrobe add dialog as an opt-in checkbox labelled honestly ("works best on plain, even
backgrounds"); saves a transparent PNG to filesDir/media. Not a matting model — fine-detail
edges will not survive, and the UI says so.

**"Surprise me" outfit picker (added 2026-09-01):** the outfit builder has a seeded,
deterministic suggester (`domain/engine/OutfitSuggester.kt`, unit-tested ×4): one DRESS,
or one TOP + one BOTTOM (both required — no core, no suggestion), plus SHOES/BAG/ACCESSORY
when the wardrobe has them. Selection is slot-and-category based only — it does NOT claim
to judge colour or style compatibility; the button is labelled "Surprise me" for exactly
that reason.

## 10. Photo contribution flow — SHIPPED (2026-09-01)
Settings → **Contribute fabric photos**: pick a fabric type (17 categories), explicit consent
checkbox required, photos copy to `filesDir/contributions/<CATEGORY>/` and **stay on-device**
until the user taps **Export for training** (zips to a shareable file). This is how the
dataset grows from real users: collect zips, run `ingest.py --apply` (validates and dedupes
into `tools/dataset/raw/<CLASS>/`), rerun
`train_starter.py`, ship when the honest numbers clear the bar.

**Gallery / delete / auto-naming (added 2026-09-01):**
- In-dialog **gallery**: 3-column thumbnail grid of every contribution, newest first.
- **Delete per contribution**: X button on each thumbnail asks for confirmation
  ("Delete this photo?"), then removes the file.
- **Category fix-up**: tapping a thumbnail re-opens the category picker; picking a new
  category moves the file to the correct folder and rewrites its filename prefix
  (`recategorizeContribution`, unit-tested incl. no-overwrite case) — so mislabelled
  photos can be corrected before export.
- **Near-duplicate rejection**: new photos are perceptually hashed (dHash); if one is
  ≥90% similar to an existing contribution the save is refused with an inline message —
  keeps the exported dataset free of repeat spam.
- **Auto-naming for dataset ingestion**: files save as `<CATEGORY>_<yyyyMMdd_HHmmss>.jpg`
  (collision-safe with a numeric suffix), so an exported zip unpacks straight into
  `tools/dataset/raw/<CLASS>/` with meaningful filenames and no renaming step.
- **Export manifest**: the share zip now includes `manifest.json` with `exportedAt`
  timestamp and a `[{category, file}]` list (unit-tested), so the ingestion side can
  validate contents and detect misfiled entries.

## 11. Cloud endpoint — reference server now exists (2026-09-01)
`tools/mock_server/server.py` implements the documented contract (`POST /v1/analyze`
multipart "image" → RemoteAnalysisDto JSON) with honest mock behaviour (avg-colour toy
classifier, confidence fixed at 0.5, `engine: MOCK`). **Contract verified live here**: 200
with valid DTO, GET /health, 400 missing-part, 501 unknown-path. Point the app's cloud base
URL at it to exercise the full cloud path end-to-end (adb reverse or LAN IP). A real
production endpoint (or Genlook-class try-on API) can now be swapped in against a
contract that is demonstrably implementable.

## 12. DeepLabV3 on-device QA — one command away (2026-09-01)
`app/src/androidTest/.../PersonSegmenterDeviceTest.kt` runs the production
`PersonSegmenter.personMask` path on real hardware with timing bounds. Execute on a phone:
`./gradlew :app:connectedDebugAndroidTest`. Compiles clean here; execution still requires
physical hardware (no KVM in this environment).

## 13. Remaining partial functionality
| Area | Status |
|---|---|
| Cloud analysis / generative try-on | Contract written **and reference server proven**; needs a production endpoint |
| Fabric dataset collection | Pipeline + **in-app contribution flow shipped**; needs real photos (300+/class) |
| Fine-tuned model in app | Blocked on dataset (starter baseline 0.419 macro, not shipped by design) |
| DeepLabV3 on-device (GPU/mem) | Host QA done; **instrumentation test written** — run on a phone |
| CI first run | YAML validated locally; **runs on first GitHub push** |
