package com.ntoma.studio.domain.model

import com.ntoma.studio.R

/** A user-curated set of fabrics, designs and looks ("Wedding Ideas", "My Kente", …). */
data class Collection(
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val items: List<CollectionItem> = emptyList(),
)

data class CollectionItem(
    val type: CollectionItemType,
    val itemId: String,
    val addedAt: Long,
)

enum class CollectionItemType { FABRIC, DESIGN, LOOK }

/** A photographed piece the user already owns. */
data class WardrobeItem(
    val id: Long = 0,
    val name: String,
    val category: WardrobeCategory,
    val imageUri: String,
    val notes: String?,
    val createdAt: Long,
)

enum class WardrobeCategory(val labelRes: Int) {
    TOP(R.string.wardrobe_cat_top),
    BOTTOM(R.string.wardrobe_cat_bottom),
    DRESS(R.string.wardrobe_cat_dress),
    SHOES(R.string.wardrobe_cat_shoes),
    BAG(R.string.wardrobe_cat_bag),
    ACCESSORY(R.string.wardrobe_cat_accessory),
}

/** A saved combination of wardrobe items. */
data class Outfit(
    val id: Long = 0,
    val name: String,
    val itemIds: List<Long>,
    val createdAt: Long,
)

/** Optional tailoring measurements, always user-entered, stored locally, never claimed as AI-derived. */
data class Measurements(
    val heightCm: Int? = null,
    val chestCm: Int? = null,
    val waistCm: Int? = null,
    val hipCm: Int? = null,
    val shoulderCm: Int? = null,
    val sleeveCm: Int? = null,
    val inseamCm: Int? = null,
    val neckCm: Int? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
) {
    val isEmpty: Boolean
        get() = listOfNotNull(heightCm, chestCm, waistCm, hipCm, shoulderCm, sleeveCm, inseamCm, neckCm).isEmpty()
}

/**
 * A tailor/designer listing.
 *
 * The bundled source is clearly-labelled DEMO data — real listings will come from a backend
 * directory with verification. [isDemo] must always drive a visible "demo" badge in the UI.
 */
data class Tailor(
    val id: String,
    val name: String,
    val isDemo: Boolean,
    val city: String,
    val area: String,
    val specialties: List<Int>, // string res ids
    val priceRange: PriceRange,
    val worksWith: GenderCategory,
    val hoursSummary: Int, // string res id
    val contactSummary: String,
    val verified: Boolean,
    val offersDelivery: Boolean,
    val portfolioNote: Int, // string res id
)

enum class PriceRange(val labelRes: Int) {
    BUDGET(R.string.tailor_price_budget),
    MID(R.string.tailor_price_mid),
    PREMIUM(R.string.tailor_price_premium),
}

/** Per-design customization choices, forwarded to the try-on backend/demo compositor. */
data class DesignCustomization(
    val sleeve: SleeveOption = SleeveOption.DEFAULT,
    val neckline: NecklineOption = NecklineOption.DEFAULT,
    val length: LengthOption = LengthOption.DEFAULT,
    val placement: FabricPlacement = FabricPlacement.FULL,
    /** Contrast fabric colour (ARGB) for sleeves/panels/accents when placement calls for it. */
    val accentArgb: Long? = null,
)

enum class SleeveOption(val labelRes: Int) {
    DEFAULT(R.string.custom_sleeve_default),
    SHORT(R.string.custom_sleeve_short),
    LONG(R.string.custom_sleeve_long),
    SLEEVELESS(R.string.custom_sleeve_sleeveless),
    PUFF(R.string.custom_sleeve_puff),
}

enum class NecklineOption(val labelRes: Int) {
    DEFAULT(R.string.custom_neck_default),
    ROUND(R.string.custom_neck_round),
    VNECK(R.string.custom_neck_v),
    HIGH(R.string.custom_neck_high),
    OFF_SHOULDER(R.string.custom_neck_off_shoulder),
}

enum class LengthOption(val labelRes: Int) {
    DEFAULT(R.string.custom_length_default),
    MINI(R.string.custom_length_mini),
    MIDI(R.string.custom_length_midi),
    MAXI(R.string.custom_length_maxi),
}

enum class FabricPlacement(val labelRes: Int, val descriptionRes: Int) {
    FULL(R.string.custom_place_full, R.string.custom_place_full_desc),
    TOP_ONLY(R.string.custom_place_top, R.string.custom_place_top_desc),
    SKIRT_ONLY(R.string.custom_place_skirt, R.string.custom_place_skirt_desc),
    PANELS(R.string.custom_place_panels, R.string.custom_place_panels_desc),
    ACCENTS(R.string.custom_place_accents, R.string.custom_place_accents_desc),
}

/** Which customizer controls a silhouette supports ("only show controls that design supports"). */
enum class CustomControl { SLEEVE, NECKLINE, LENGTH, PLACEMENT, ACCENT }

object CustomizationSupport {
    fun forSilhouette(silhouette: GarmentSilhouette): Set<CustomControl> = when (silhouette) {
        GarmentSilhouette.KABA, GarmentSilhouette.MODERN_KABA, GarmentSilhouette.MAXI,
        GarmentSilhouette.GOWN, GarmentSilhouette.MERMAID, GarmentSilhouette.PEPLUM,
        GarmentSilhouette.SKIRT_BLOUSE, GarmentSilhouette.FORMAL_DRESS,
        -> setOf(CustomControl.SLEEVE, CustomControl.NECKLINE, CustomControl.LENGTH, CustomControl.PLACEMENT, CustomControl.ACCENT)

        GarmentSilhouette.STRAIGHT, GarmentSilhouette.COVER_UP, GarmentSilhouette.CO_ORDS,
        -> setOf(CustomControl.SLEEVE, CustomControl.NECKLINE, CustomControl.LENGTH, CustomControl.PLACEMENT, CustomControl.ACCENT)

        GarmentSilhouette.KAFTAN, GarmentSilhouette.AGBADA, GarmentSilhouette.SMOCK,
        -> setOf(CustomControl.SLEEVE, CustomControl.NECKLINE, CustomControl.LENGTH, CustomControl.ACCENT)

        GarmentSilhouette.PRINT_SHIRT, GarmentSilhouette.SENATOR, GarmentSilhouette.MODERN_TRADITIONAL,
        -> setOf(CustomControl.SLEEVE, CustomControl.NECKLINE, CustomControl.ACCENT)

        GarmentSilhouette.JUMPSUIT, GarmentSilhouette.SHIRT_TROUSERS, GarmentSilhouette.AFRICAN_SUIT,
        -> setOf(CustomControl.SLEEVE, CustomControl.NECKLINE, CustomControl.ACCENT)
    }
}
