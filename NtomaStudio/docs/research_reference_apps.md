# Reference Research: How Fashion/AI Apps Work Outside Ghana — and What Ntoma Studio Adopts

*Researched 2026-08-31. Sources: Google official blog + shopping docs, virtual-try-on industry
guides (Photta, Genlook, mobilekishop roundups), Play Store / App Store listings and vendor pages
for Whering and GetWardrobe, and academic literature on fabric classification (Authente-Kente,
handloom VGG16 study, CoMMonS, HybridWeaveNet, KF9).*

---

## 1. The American / global leaders and how they actually function

### 1.1 Google Virtual Try-On (the current technical benchmark)
- **What it is**: "try it on" in Google Search/Shopping, billions of items from the Shopping Graph.
- **How it works**: a **custom diffusion-based image generation model trained on pairs of photos of
  people in different poses wearing the same garment**. The model learns how fabric folds,
  stretches, drapes and casts shadows per body type. One product photo + one user full-body photo
  is enough — no 3D avatar, no per-garment 3D asset.
- **Scale lesson**: they started with real model photos (diverse sizes/skin tones/hair), and only
  later allowed user photos. Inclusive model imagery is a launch strategy, not an afterthought.
- **Honesty lesson**: Google ships this inside Search with massive model training budget. Anyone
  claiming parity without that budget is overpromising.

### 1.2 Stitch Fix — human+AI hybrid, no try-on rendering at all
- Style quiz → algorithm narrows inventory → **human stylist makes the final pick**.
- The "AI" is a ranking/filtering engine over a real inventory; the magic is the feedback loop
  (keep/return + comments retrain preferences).
- **Lesson for Ntoma**: recommendation quality = data + explicit feedback (our heart/dismiss +
  occasion chips are the same mechanism). A tailor brief that travels to a human is our version of
  the "human in the loop" — that is a feature, not a gap.

### 1.3 Whering — digital closet with a 100M-item database
- Cataloging: photo → **automatic background removal** → category/color detection → closet.
- Community: see friends' closets, add their items to yours, **share outfits ("style submissions")**.
- Analytics: cost-per-wear, wear rate, colour palette, closet longevity.
- **Lesson for Ntoma**: background removal at capture time is the highest-value closet feature;
  sharing is social glue; analytics give retention.

### 1.4 GetWardrobe — auto-categorize + AI outfit generator
- Snap → AI removes background, detects **category and color, suggests a name**, ~1–2 min/item.
- Outfit generator filtered by **weather, occasion, mood**; virtual try-on add-on; cost-per-wear.
- Free tier = 100 items; premium = unlimited + family wardrobes + stylist access.
- **Lesson for Ntoma**: occasion filters (we have these), auto-naming suggestions, and a generous
  free tier with a clear premium step (our premium screen mirrors this).

### 1.5 Genlook / Photta (B2B VTO platforms)
- Generative 2D try-on from existing product photos; plug-and-play for Shopify ($50–200/mo).
- Reported impact: returns −27…64%, conversion +40…94%, 2–3× page dwell.
- Requirements: clean 2000×2000 product photos minimum.
- **Lesson**: when Ntoma adds a backend, **renting generative try-on (Genlook-class API) is far
  cheaper than training our own diffusion model** — that's exactly what the CloudAnalysisClient
  contract is shaped for.

### 1.6 Other instructive cases
- **Warby Parker**: face-landmark AR (68+ landmarks) — precision AR is per-category, not general.
- **Nike Fit**: CV foot scan → per-model size recommendation; measurement-driven fit beats visual
  fit for reducing returns. Our measurements screen is the same philosophy.
- **IKEA Place**: true-to-scale AR — scale/perspective honesty sells trust.

## 2. Academic state of the art on fabric recognition (what accuracy is real)
- **Authente-Kente (Robinson et al., 2021)**: CNN distinguishing *authentic hand-woven kente from
  machine-printed fakes* reached **~88% accuracy** — a narrow, curated dataset task, and still not
  production-perfect. This is the closest published work to our domain.
- **Handloom authentication (VGG16 transfer learning, 25k images)**: 97.8% train / **93%
  validation** on 6 classes with 500×500 images and heavy augmentation.
- **HybridWeaveNet (EfficientNetV2 + dual attention)**: purpose-built for motif-rich woven
  fabrics because generic CNNs struggle with fine-grained textures and intra-class variety.
- **CoMMonS / KF9 / ISL-Knit**: every serious fabric paper builds its **own dataset** (2k–25k
  images); there is **no public "Ghanaian fabrics" ImageNet-scale dataset** — this is the real
  bottleneck, not model architecture.
- **Defect detection (AlexNet transfer, 92.6%)**: transfer learning from ImageNet works, but
  always with domain fine-tuning.

**Conclusion**: any claim of "detects all Ghanaian fabrics accurately" would be dishonest. The
credible ceiling today, without a bespoke dataset, is: coarse family classification (kente-family,
wax/ankara-family, lace, plain weaves) at moderate confidence, plus honest "unknown". That is
exactly how Ntoma is built (MobileNetV2 hints + GLCM texture + kente-strip detector + capped
confidence + ON_DEVICE_DEMO labeling).

## 3. What Ntoma Studio adopted from this research (implemented)
1. **Real reference look catalog with fabric + gender tags** (Whering/GetWardrobe-style "see real
   outfits"): 12 curated photos of real Ghanaian outfits (kente kaba & slit, kente groom wear,
   lace gowns, northern fugu/batakari smocks, ankara two-pieces and shirts), matched to the
   detected fabric family and the gender filter, labelled honestly as catalog reference photos —
   never passed off as renders of the user's fabric. `catalog/looks.json` + `InspirationLook.matches`
   (unit-tested).
2. **Occasion + gender filters** on recommendations (GetWardrobe pattern) — already present, now
   paired with real-photo proof points.
3. **Event notifications with per-channel user control** (never engagement spam): processing
   channel now also fires "Tailor brief ready" alongside "Look ready"; recommendations and updates
   channels unchanged; all gated by system permission + per-channel settings toggles.
4. **Human-in-the-loop ordering** (Stitch Fix philosophy): the PDF tailor brief is our stylist
   handoff — measurements, customizations, fabric, design art in one document.
5. **Backend-readiness for rented generative try-on** (Genlook/Photta lesson): the
   CloudAnalysisClient contract (multipart `POST /v1/analyze`, confidence capped at 0.8, honest
   ON_DEVICE_DEMO fallback) is shaped so a generative try-on endpoint can be swapped in without
   touching UI code.
6. **Measurement-driven fit** (Nike Fit philosophy): measurements screen feeds the brief.

## 4. What we deliberately did NOT copy
- Diffusion try-on rendering on-device — impossible at our size/heat budget; the demo compositor
  stays and is labelled as a demo.
- Social network features (friend closets) — needs accounts/backend; out of offline-first scope.
- Fake "AI stylist" chat — violates the honest-AI rule.

## 5. Roadmap implied by the research
1. **Dataset**: crowd-source 1–5k labelled photos of Ghanaian fabrics (market photography days with
   Makola/Kantamanto sellers) → fine-tune MobileNetV3/EfficientNet-Lite → real accuracy numbers.
2. **Authenticity check** (Authente-Kente direction): hand-woven vs printed kente — genuinely
   useful to buyers and weavers, and publishable.
3. **Cloud try-on**: integrate a Genlook-class API behind the existing config flag when a budget
   exists; keep the on-device demo as the offline fallback.
4. **Background removal** for wardrobe capture (MediaPipe Selfie Segmentation or the bundled
   DeepLabV3) — the Whering feature with the highest user-perceived value.
