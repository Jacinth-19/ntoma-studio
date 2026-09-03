package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.AnalyzedColor
import com.ntoma.studio.domain.model.ColorName
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.GarmentSilhouette
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.PaletteSummary
import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.domain.model.StyleCategory
import com.ntoma.studio.domain.model.TextureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private val styles = listOf(
        DressStyle(
            id = "kaba_slit",
            titleKey = "style_kaba_slit_title",
            descriptionKey = "style_kaba_slit_desc",
            gender = GenderCategory.WOMEN,
            category = StyleCategory.TRADITIONAL,
            occasions = setOf(Occasion.CEREMONY, Occasion.CHURCH, Occasion.FESTIVAL),
            silhouette = GarmentSilhouette.KABA,
            affinity = setOf(FabricCategory.KENTE, FabricCategory.WAX, FabricCategory.BROCADE),
            structure = 0.6f,
        ),
        DressStyle(
            id = "mermaid",
            titleKey = "style_mermaid_title",
            descriptionKey = "style_mermaid_desc",
            gender = GenderCategory.WOMEN,
            category = StyleCategory.FORMAL,
            occasions = setOf(Occasion.WEDDING, Occasion.PARTY),
            silhouette = GarmentSilhouette.MERMAID,
            affinity = setOf(FabricCategory.LACE, FabricCategory.SILK, FabricCategory.VELVET),
            structure = 0.3f,
        ),
        DressStyle(
            id = "kaftan",
            titleKey = "style_kaftan_title",
            descriptionKey = "style_kaftan_desc",
            gender = GenderCategory.MEN,
            category = StyleCategory.TRADITIONAL,
            occasions = setOf(Occasion.CEREMONY, Occasion.WEDDING),
            silhouette = GarmentSilhouette.KAFTAN,
            affinity = setOf(FabricCategory.COTTON_PLAIN, FabricCategory.LINEN),
            structure = 0.35f,
        ),
    )

    private fun kente(): Fabric = Fabric(
        imageUri = "test://kente.jpg",
        name = null,
        category = FabricCategory.KENTE,
        colors = listOf(AnalyzedColor(0xFFC8952B, ColorName.GOLD, 0.6f)),
        pattern = PatternType.STRIPED,
        texture = TextureType.WOVEN,
        confidence = 0.7f,
        palette = PaletteSummary(saturation = 0.7f, brightness = 0.6f, contrast = 0.5f, colorCount = 4),
        motifScale = 0.7f,
        suggestedUses = listOf(Occasion.CEREMONY, Occasion.FESTIVAL),
        createdAt = 0L,
    )

    @Test
    fun `recommendations are sorted by score descending`() {
        val recs = RecommendationEngine.recommend(kente(), styles)
        val scores = recs.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `scores stay inside the 5-98 band`() {
        RecommendationEngine.recommend(kente(), styles).forEach {
            assertTrue(it.score in 5..98)
        }
    }

    @Test
    fun `every recommendation carries at least one reason`() {
        RecommendationEngine.recommend(kente(), styles).forEach {
            assertTrue(it.reasons.isNotEmpty())
        }
    }

    @Test
    fun `affinity and tradition lift the matching style above others`() {
        val recs = RecommendationEngine.recommend(kente(), styles)
        val kabaScore = recs.first { it.style.id == "kaba_slit" }.score
        val mermaidScore = recs.first { it.style.id == "mermaid" }.score
        assertTrue(kabaScore > mermaidScore)
    }

    @Test
    fun `scoreForGender accepts unisex styles for everyone`() {
        val unisex = styles.first().copy(gender = GenderCategory.UNISEX)
        assertTrue(RecommendationEngine.scoreForGender(unisex, GenderCategory.MEN))
        assertTrue(RecommendationEngine.scoreForGender(unisex, null))
    }
}
