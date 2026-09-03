package com.ntoma.studio.domain.engine

import com.ntoma.studio.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure colour-theory assistant: given the dominant colours of a fabric, proposes companion
 * colours for garments, shoes, bags, jewellery and headwraps. Deterministic and testable;
 * a future backend can re-rank suggestions per trend data without touching the UI.
 */
object ColorHarmonyEngine {

    enum class Role(val labelRes: Int) {
        COMPLEMENTARY(R.string.color_role_complementary),
        ANALOGOUS(R.string.color_role_analogous),
        NEUTRAL(R.string.color_role_neutral),
        METALLIC(R.string.color_role_metallic),
    }

    data class Suggestion(
        val argb: Long,
        val role: Role,
        val reasonRes: Int,
    )

    private const val GOLD: Long = 0xFFC8952B
    private const val BRONZE: Long = 0xFF8A5A2B
    private const val SILVER: Long = 0xFFB9BEC4

    /**
     * Returns up to [limit] suggestions. Complementary colours sit ~180° from the dominant hue,
     * analogous ~±30°, neutrals are desaturated echoes of the fabric, plus metallics for
     * jewellery/accessories.
     */
    fun suggest(dominantArgb: List<Long>, limit: Int = 8): List<Suggestion> {
        if (dominantArgb.isEmpty()) return emptyList()
        val out = mutableListOf<Suggestion>()
        val primary = dominantArgb.first()
        val (h, s, l) = toHsl(primary)

        if (s > 0.12f) {
            out += Suggestion(fromHsl((h + 180f) % 360f, minOf(s * 1.05f, 1f), balancedL(l)), Role.COMPLEMENTARY, R.string.color_why_complementary)
            out += Suggestion(fromHsl((h + 30f) % 360f, s * 0.85f, balancedL(l)), Role.ANALOGOUS, R.string.color_why_analogous)
            out += Suggestion(fromHsl((h + 330f) % 360f, s * 0.85f, balancedL(l)), Role.ANALOGOUS, R.string.color_why_analogous)
        }
        // Neutral echoes: same hue, low saturation, light and dark variants.
        out += Suggestion(fromHsl(h, 0.08f, 0.82f), Role.NEUTRAL, R.string.color_why_neutral_light)
        out += Suggestion(fromHsl(h, 0.10f, 0.22f), Role.NEUTRAL, R.string.color_why_neutral_dark)
        // Metallics: gold when the fabric is warm, silver when cool, bronze always reasonable.
        val warm = h < 90f || h > 300f
        out += if (warm) Suggestion(GOLD, Role.METALLIC, R.string.color_why_gold) else Suggestion(SILVER, Role.METALLIC, R.string.color_why_silver)
        out += Suggestion(BRONZE, Role.METALLIC, R.string.color_why_bronze)

        return out.distinctBy { it.argb }.take(limit)
    }

    /** Accessory mapping: shoes/bag/jewellery/headwrap/tie pick roles that suit each use. */
    fun accessoryPalette(dominantArgb: List<Long>): List<Suggestion> = suggest(dominantArgb, limit = 6)

    private fun balancedL(l: Float): Float = when {
        l < 0.3f -> 0.45f
        l > 0.75f -> 0.6f
        else -> l
    }

    // --- HSL helpers (Android Color.colorToHSV-compatible, kept pure for unit tests) ---

    fun toHsl(argb: Long): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2f
        val d = mx - mn
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-4f)
        val h = when {
            d == 0f -> 0f
            mx == r -> ((g - b) / d).let { if (it < 0) it + 6 else it } * 60f
            mx == g -> ((b - r) / d + 2) * 60f
            else -> ((r - g) / d + 4) * 60f
        }
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Long {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        val r = ((r1 + m) * 255).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255).roundToInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
