package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.GarmentSilhouette
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Recommendation
import com.ntoma.studio.domain.model.StyleCategory
import com.ntoma.studio.domain.model.TextureType
import com.ntoma.studio.domain.model.WhyReason
import kotlin.math.abs

/**
 * Deterministic, fully testable pairing of analysed fabrics with garment designs.
 * A backend can later replace or re-rank these results without touching the UI.
 */
object RecommendationEngine {

    private val LARGE_PANEL = setOf(
        GarmentSilhouette.KABA, GarmentSilhouette.MODERN_KABA, GarmentSilhouette.MAXI,
        GarmentSilhouette.GOWN, GarmentSilhouette.AGBADA, GarmentSilhouette.KAFTAN,
        GarmentSilhouette.SMOCK, GarmentSilhouette.COVER_UP,
    )

    private val FITTED = setOf(
        GarmentSilhouette.STRAIGHT, GarmentSilhouette.MERMAID, GarmentSilhouette.PEPLUM,
        GarmentSilhouette.PRINT_SHIRT, GarmentSilhouette.SENATOR,
        GarmentSilhouette.SHIRT_TROUSERS, GarmentSilhouette.FORMAL_DRESS,
        GarmentSilhouette.MODERN_TRADITIONAL,
    )

    private val STRUCTURED_PATTERNS = setOf(
        com.ntoma.studio.domain.model.PatternType.STRIPED,
        com.ntoma.studio.domain.model.PatternType.CHECKED,
        com.ntoma.studio.domain.model.PatternType.GEOMETRIC,
        com.ntoma.studio.domain.model.PatternType.SYMBOLIC,
    )

    private fun textureStructure(t: TextureType): Float = when (t) {
        TextureType.WOVEN -> 0.7f
        TextureType.STIFF -> 0.9f
        TextureType.SLUB -> 0.6f
        TextureType.SMOOTH -> 0.4f
        TextureType.SOFT -> 0.2f
        TextureType.SHEER -> 0.15f
        TextureType.PILE -> 0.8f
        TextureType.QUILTED -> 0.9f
        TextureType.EMBROIDERED -> 0.75f
    }

    fun recommend(
        fabric: Fabric,
        styles: List<DressStyle>,
        dismissed: Set<String> = emptySet(),
    ): List<Recommendation> {
        val visible = styles.filterNot { it.id in dismissed }
        val targetStructure = textureStructure(fabric.texture)
        val structured = fabric.pattern in STRUCTURED_PATTERNS

        return visible.map { style ->
            var score = 30f
            val reasons = mutableListOf<WhyReason>()

            if (fabric.category in style.affinity) {
                score += 18f
            }

            val structureFit = 1f - abs(targetStructure - style.structure)
            score += 10f * structureFit
            if (structureFit > 0.75f) {
                reasons += if (targetStructure > 0.55f) WhyReason.TextureHeavy else WhyReason.TextureLight
            }

            if (structured) {
                reasons += WhyReason.PatternStructured
                score += 6f
            } else {
                reasons += WhyReason.PatternFlowing
                score += 4f
            }

            when {
                fabric.motifScale > 0.55f && style.silhouette in LARGE_PANEL -> {
                    score += 8f
                    reasons += WhyReason.ScaleLarge
                }
                fabric.motifScale < 0.35f && style.silhouette in FITTED -> {
                    score += 8f
                    reasons += WhyReason.ScaleSmall
                }
            }

            val overlap = fabric.suggestedUses.intersect(style.occasions)
            if (overlap.isNotEmpty()) {
                score += 12f * overlap.size / style.occasions.size.coerceAtLeast(1)
                reasons += WhyReason.OccasionFit(overlap.first())
            }

            if (fabric.palette.saturation > 0.5f) {
                score += 6f
                reasons += WhyReason.ColorsRich
            } else if (fabric.palette.saturation < 0.3f) {
                score += 6f
                reasons += WhyReason.ColorsMuted
            }

            if (fabric.category in TRADITIONAL_CLOTHS && style.category == StyleCategory.TRADITIONAL) {
                score += 8f
                reasons += WhyReason.Tradition
            }

            reasons += WhyReason.GenderFit(style.gender)

            Recommendation(
                fabricId = fabric.id.takeIf { it != 0L },
                style = style,
                score = score.toInt().coerceIn(5, 98),
                reasons = reasons.distinct().take(4),
            )
        }
            .sortedByDescending { it.score }
    }

    /** Convenience for Discover (no fabric selected). */
    fun scoreForGender(style: DressStyle, gender: GenderCategory?): Boolean =
        gender == null || style.gender == gender || style.gender == GenderCategory.UNISEX

    private val TRADITIONAL_CLOTHS = setOf(
        FabricCategory.KENTE, FabricCategory.ADINKRA, FabricCategory.KENTE_PRINT, FabricCategory.WAX,
        FabricCategory.FUGU, FabricCategory.GONJA,
    )
}
