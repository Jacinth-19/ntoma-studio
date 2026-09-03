package com.ntoma.studio.domain.model

/**
 * A garment design that can be recommended and visualised.
 *
 * Titles/descriptions live in string resources (resolved through [titleKey]); the catalogue JSON
 * shipped in assets (or served by a backend) maps onto this model, so new styles can be added
 * without touching UI code.
 */
data class DressStyle(
    val id: String,
    val titleKey: String,
    val descriptionKey: String,
    val gender: GenderCategory,
    val category: StyleCategory,
    val occasions: Set<Occasion>,
    /** Which garment silhouette to draw for illustration + the try-on overlay. */
    val silhouette: GarmentSilhouette,
    /** Fabrics this cut traditionally works best with — feeds the recommendation engine. */
    val affinity: Set<FabricCategory>,
    /** 0 = relaxed drape … 1 = rigid structure; matched against fabric texture. */
    val structure: Float,
)

enum class GenderCategory(val labelRes: Int) {
    WOMEN(com.ntoma.studio.R.string.recs_filter_women),
    MEN(com.ntoma.studio.R.string.recs_filter_men),
    UNISEX(com.ntoma.studio.R.string.recs_filter_unisex),
}

enum class StyleCategory(val labelRes: Int) {
    CASUAL(com.ntoma.studio.R.string.recs_filter_casual),
    FORMAL(com.ntoma.studio.R.string.recs_filter_formal),
    TRADITIONAL(com.ntoma.studio.R.string.recs_filter_traditional),
}

enum class Occasion(val labelRes: Int) {
    WEDDING(com.ntoma.studio.R.string.occasion_wedding),
    CHURCH(com.ntoma.studio.R.string.occasion_church),
    OFFICE(com.ntoma.studio.R.string.occasion_office),
    PARTY(com.ntoma.studio.R.string.occasion_party),
    EVERYDAY(com.ntoma.studio.R.string.occasion_everyday),
    CEREMONY(com.ntoma.studio.R.string.occasion_ceremony),
    MOURNING(com.ntoma.studio.R.string.occasion_mourning),
    FESTIVAL(com.ntoma.studio.R.string.occasion_festival),
}

/** Rendering key for the procedural garment artwork (see ui/components/Art.kt). */
enum class GarmentSilhouette {
    KABA,
    MODERN_KABA,
    MAXI,
    STRAIGHT,
    MERMAID,
    PEPLUM,
    JUMPSUIT,
    SKIRT_BLOUSE,
    GOWN,
    FORMAL_DRESS,
    KAFTAN,
    AGBADA,
    PRINT_SHIRT,
    SENATOR,
    SMOCK,
    AFRICAN_SUIT,
    SHIRT_TROUSERS,
    MODERN_TRADITIONAL,
    COVER_UP,
    CO_ORDS,
}
