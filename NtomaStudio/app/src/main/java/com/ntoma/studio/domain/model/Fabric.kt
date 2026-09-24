package com.ntoma.studio.domain.model

import com.ntoma.studio.R

/**
 * Domain model for a fabric the user has scanned or uploaded.
 *
 * A [Fabric] carries the output of the analysis engine plus bookkeeping. The analysis is kept
 * embedded so a fabric card can render everything offline without a second lookup.
 */
data class Fabric(
    val id: Long = 0,
    val imageUri: String,
    val name: String?,
    val category: FabricCategory,
    val colors: List<AnalyzedColor>,
    val pattern: PatternType,
    val texture: TextureType,
    /** 0f..1f — how sure the engine is about [category]. Always displayed honestly. */
    val confidence: Float,
    val palette: PaletteSummary = PaletteSummary(),
    /** 0 = tiny repeat … 1 = huge motif. Drives silhouette matching. */
    val motifScale: Float = 0.5f,
    val printCharacteristics: List<Int> = emptyList(),
    val suggestedUses: List<Occasion> = emptyList(),
    val engine: AnalysisEngine = AnalysisEngine.ON_DEVICE_DEMO,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val notes: String? = null,
    /** Approximate length owned, centimetres. */
    val amountCm: Int? = null,
    val intendedWearer: String? = null,
    /** Occasion name (free text or enum name). */
    val intendedOccasion: String? = null,
    val acquiredAt: Long? = null,
    /** What the bundled on-device ImageNet model sees; a second opinion, never persisted. */
    val mlHints: List<MlHint> = emptyList(),
)

/** One ImageNet label the on-device classifier saw, with its softmax score. */
data class MlHint(
    val label: String,
    val score: Float,
    /** Fabric category the label maps to, when we know one. */
    val mapped: FabricCategory? = null,
)

/** Aggregate colour statistics read from the photo. */
data class PaletteSummary(
    val saturation: Float = 0.5f,
    val brightness: Float = 0.5f,
    val contrast: Float = 0.3f,
    val colorCount: Int = 1,
)

/** A dominant colour read from the photo, with a human name for display + a11y. */
data class AnalyzedColor(
    val argb: Long,
    val colorName: ColorName,
    val fraction: Float,
)

enum class ColorName(val labelRes: Int) {
    RED(R.string.color_name_red),
    ORANGE(R.string.color_name_orange),
    GOLD(R.string.color_name_gold),
    YELLOW(R.string.color_name_yellow),
    GREEN(R.string.color_name_green),
    TEAL(R.string.color_name_teal),
    BLUE(R.string.color_name_blue),
    INDIGO(R.string.color_name_indigo),
    PURPLE(R.string.color_name_purple),
    PINK(R.string.color_name_pink),
    BROWN(R.string.color_name_brown),
    BLACK(R.string.color_name_black),
    GREY(R.string.color_name_grey),
    WHITE(R.string.color_name_white),
    CREAM(R.string.color_name_cream),
}

enum class PatternType(val labelRes: Int) {
    STRIPED(R.string.pattern_striped),
    CHECKED(R.string.pattern_checked),
    GEOMETRIC(R.string.pattern_geometric),
    FLORAL(R.string.pattern_floral),
    ORGANIC(R.string.pattern_organic),
    SYMBOLIC(R.string.pattern_symbolic),
    DOTTED(R.string.pattern_dotted),
    ABSTRACT(R.string.pattern_abstract),
    SOLID(R.string.pattern_solid),
    GRADIENT(R.string.pattern_gradient),
}

enum class TextureType(val labelRes: Int) {
    WOVEN(R.string.texture_woven),
    SMOOTH(R.string.texture_smooth),
    SLUB(R.string.texture_slub),
    SHEER(R.string.texture_sheer),
    PILE(R.string.texture_pile),
    STIFF(R.string.texture_stiff),
    SOFT(R.string.texture_soft),
    QUILTED(R.string.texture_quilted),
    EMBROIDERED(R.string.texture_embroidered),
}

enum class FabricCategory(val labelRes: Int, val descriptionRes: Int, val asset: String? = null) {
    KENTE(R.string.fabric_kente, R.string.fabric_kente_desc, "catalog/fabrics/kente.jpg"),
    KETE_EWE(R.string.fabric_kete_ewe, R.string.fabric_kete_ewe_desc),
    ADINKRA(R.string.fabric_adinkra, R.string.fabric_adinkra_desc, "catalog/fabrics/adinkra.jpg"),
    NWOMU(R.string.fabric_nwomu, R.string.fabric_nwomu_desc),
    OBAMA_EMBROIDERY(R.string.fabric_obama_embroidery, R.string.fabric_obama_embroidery_desc),
    WAX(R.string.fabric_wax, R.string.fabric_wax_desc, "catalog/fabrics/wax.jpg"),
    ANKARA(R.string.fabric_ankara, R.string.fabric_ankara_desc, "catalog/fabrics/ankara.jpg"),
    BATIK(R.string.fabric_batik, R.string.fabric_batik_desc, "catalog/fabrics/batik.jpg"),
    TIEDYE(R.string.fabric_tiedye, R.string.fabric_tiedye_desc, "catalog/fabrics/tiedye.jpg"),
    JAVA_PRINT(R.string.fabric_java_print, R.string.fabric_java_print_desc),
    LACE(R.string.fabric_lace, R.string.fabric_lace_desc, "catalog/fabrics/lace.jpg"),
    BROCADE(R.string.fabric_brocade, R.string.fabric_brocade_desc, "catalog/fabrics/brocade.jpg"),
    COTTON_PLAIN(R.string.fabric_cotton_plain, R.string.fabric_cotton_plain_desc, "catalog/fabrics/cotton_plain.jpg"),
    LINEN(R.string.fabric_linen, R.string.fabric_linen_desc, "catalog/fabrics/linen.jpg"),
    SEERSUCKER(R.string.fabric_seersucker, R.string.fabric_seersucker_desc),
    SILK(R.string.fabric_silk, R.string.fabric_silk_desc, "catalog/fabrics/silk.jpg"),
    CHIFFON(R.string.fabric_chiffon, R.string.fabric_chiffon_desc, "catalog/fabrics/chiffon.jpg"),
    CREPE(R.string.fabric_crepe, R.string.fabric_crepe_desc),
    ORGANZA_TULLE(R.string.fabric_organza_tulle, R.string.fabric_organza_tulle_desc),
    VELVET(R.string.fabric_velvet, R.string.fabric_velvet_desc, "catalog/fabrics/velvet.jpg"),
    DENIM(R.string.fabric_denim, R.string.fabric_denim_desc, "catalog/fabrics/denim.jpg"),
    KENTE_PRINT(R.string.fabric_kente_print, R.string.fabric_kente_print_desc, "catalog/fabrics/kente_print.jpg"),
    TAPESTRY_JACQUARD(R.string.fabric_tapestry_jacquard, R.string.fabric_tapestry_jacquard_desc),
    FUGU(R.string.fabric_fugu, R.string.fabric_fugu_desc),
    GONJA(R.string.fabric_gonja, R.string.fabric_gonja_desc),
    UNKNOWN(R.string.fabric_unknown, R.string.fabric_unknown_desc),
}

enum class AnalysisEngine {
    /** Runs fully on-device; nothing is uploaded. */
    ON_DEVICE_DEMO,

    /** A cloud CV backend, once configured by the operator. */
    CLOUD,
}

/** String resource ids describing what the print is doing. */
object PrintTraits {
    val HIGH_CONTRAST = R.string.print_trait_high_contrast
    val LOW_CONTRAST = R.string.print_trait_low_contrast
    val LARGE_SCALE = R.string.print_trait_large_scale
    val SMALL_REPEAT = R.string.print_trait_small_repeat
    val MULTICOLOUR = R.string.print_trait_multicolour
    val TWO_TONE = R.string.print_trait_two_tone
    val DIRECTIONAL = R.string.print_trait_directional
    val ALL_OVER = R.string.print_trait_all_over
    val WOVEN_STRIPS = R.string.print_trait_woven_strips
}
