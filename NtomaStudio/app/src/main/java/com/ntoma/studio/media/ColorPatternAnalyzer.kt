package com.ntoma.studio.media

import com.ntoma.studio.domain.model.AnalyzedColor
import com.ntoma.studio.domain.model.ColorName
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.PaletteSummary
import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.domain.model.PrintTraits
import com.ntoma.studio.domain.model.TextureType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin, dependency-free fabric vision heuristics.
 *
 * Works on a flat ARGB array so it runs identically in unit tests and on-device. This is the
 * "demo engine" — deliberately honest about confidence and designed to be replaced by a real CV
 * backend through [com.ntoma.studio.domain.repository.FabricAnalysisRepository].
 */
object ColorPatternAnalyzer {

    data class FabricSignature(
        val colors: List<AnalyzedColor>,
        val palette: PaletteSummary,
        val pattern: PatternType,
        val patternConfidence: Float,
        val texture: TextureType,
        val motifScale: Float,
        val printTraits: List<Int>,
        val category: FabricCategory,
        val categoryConfidence: Float,
        val suggestedUses: List<Occasion>,
        val glcm: TextureMetrics.Glcm = TextureMetrics.Glcm(0f, 1f, 1f, 0f),
        val strips: TextureMetrics.StripSignal = TextureMetrics.StripSignal(0f, 0f, true, 0),
    )

    fun analyze(pixels: IntArray, width: Int, height: Int): FabricSignature {
        require(pixels.size == width * height) { "pixel array size mismatch" }
        val n = pixels.size

        val lum = FloatArray(n)
        val sat = FloatArray(n)
        val hue = FloatArray(n)
        var satSum = 0f
        var lumSum = 0f
        for (i in 0 until n) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val l = (mx + mn) / 2f
            lum[i] = l * 255f
            val d = mx - mn
            val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-4f)
            sat[i] = s
            hue[i] = if (d == 0f) -1f else hueOf(r, g, b, mx, d)
            satSum += s
            lumSum += lum[i]
        }
        val meanLum = lumSum / n
        val meanSat = satSum / n

        // Luminance variance -> contrast / solid detection.
        var varSum = 0f
        for (i in 0 until n) {
            val d = lum[i] - meanLum
            varSum += d * d
        }
        val lumStd = sqrt(varSum / n)
        val contrast = (lumStd / 90f).coerceIn(0f, 1f)

        // Dominant colours from hue/lightness buckets.
        val colors = dominantColors(pixels, hue, sat, lum)
        val palette = PaletteSummary(
            saturation = meanSat.coerceIn(0f, 1f),
            brightness = (meanLum / 255f).coerceIn(0f, 1f),
            contrast = contrast,
            colorCount = colors.size.coerceAtLeast(1),
        )

        // Row / column separation -> stripes and checks.
        val rowMean = FloatArray(height)
        val rowVar = FloatArray(height)
        for (y in 0 until height) {
            var s = 0f
            for (x in 0 until width) s += lum[y * width + x]
            val m = s / width
            rowMean[y] = m
            var v = 0f
            for (x in 0 until width) {
                val d = lum[y * width + x] - m
                v += d * d
            }
            rowVar[y] = v / width
        }
        val colMean = FloatArray(width)
        val colVar = FloatArray(width)
        for (x in 0 until width) {
            var s = 0f
            for (y in 0 until height) s += lum[y * width + x]
            val m = s / height
            colMean[x] = m
            var v = 0f
            for (y in 0 until height) {
                val d = lum[y * width + x] - m
                v += d * d
            }
            colVar[x] = v / height
        }

        val rowSep = separation(rowMean, rowVar)
        val colSep = separation(colMean, colVar)
        val edgeDensity = edgeDensity(lum, width, height)
        val periodRow = period(rowMean)
        val periodCol = period(colMean)
        val hPeriod = periodHorizontal(lum, width, height)
        val vPeriod = periodVertical(lum, width, height)

        val (pattern, patternConf, motifScale) = classifyPattern(
            lumStd = lumStd,
            rowSep = rowSep,
            colSep = colSep,
            edgeDensity = edgeDensity,
            colorCount = palette.colorCount,
            saturation = meanSat,
            contrast = contrast,
            meanLum = meanLum / 255f,
            darkFraction = darkPixelFraction(lum), 
            periodRow = periodRow,
            periodCol = periodCol,
            hPeriod = hPeriod,
            vPeriod = vPeriod,
            width = width,
            height = height,
            hueSpread = hueSpread(hue),
        )

        val glcm = TextureMetrics.glcm(lum, width, height)
        val strips = TextureMetrics.kenteStrips(pixels, width, height)

        val texture = classifyTexture(lum, width, height, edgeDensity, meanSat, meanLum / 255f, glcm)

        val traits = buildList {
            add(if (contrast > 0.45f) PrintTraits.HIGH_CONTRAST else PrintTraits.LOW_CONTRAST)
            if (motifScale > 0.6f) add(PrintTraits.LARGE_SCALE) else if (motifScale < 0.35f) add(PrintTraits.SMALL_REPEAT)
            if (palette.colorCount >= 4) add(PrintTraits.MULTICOLOUR) else if (palette.colorCount <= 2) add(PrintTraits.TWO_TONE)
            if (pattern == PatternType.STRIPED || pattern == PatternType.CHECKED || pattern == PatternType.GRADIENT) {
                add(PrintTraits.DIRECTIONAL)
            } else {
                add(PrintTraits.ALL_OVER)
            }
            if (strips.stripScore > 0.5f) add(PrintTraits.WOVEN_STRIPS)
        }

        val (category, categoryConf) = classifyCategory(pattern, palette, texture, colors, strips)

        return FabricSignature(
            colors = colors,
            palette = palette,
            pattern = pattern,
            patternConfidence = patternConf,
            texture = texture,
            motifScale = motifScale,
            printTraits = traits,
            category = category,
            categoryConfidence = categoryConf,
            suggestedUses = usesFor(category),
            glcm = glcm,
            strips = strips,
        )
    }

    // ---------- internals ----------

    private fun darkPixelFraction(lum: FloatArray): Float {
        var dark = 0
        for (i in lum.indices) if (lum[i] < 70f) dark++
        return dark.toFloat() / lum.size.coerceAtLeast(1)
    }

    private fun hueOf(r: Float, g: Float, b: Float, mx: Float, d: Float): Float {
        return when {
            mx == r -> ((g - b) / d).let { if (it < 0) it + 6 else it }
            mx == g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
    }

    private fun hueSpread(hue: FloatArray): Float {
        var min = 360f
        var max = 0f
        var counted = 0
        for (h in hue) {
            if (h < 0) continue
            counted++
            if (h < min) min = h
            if (h > max) max = h
        }
        return if (counted < 10) 0f else (max - min) / 360f
    }

    private fun dominantColors(pixels: IntArray, hue: FloatArray, sat: FloatArray, lum: FloatArray): List<AnalyzedColor> {
        // 12 hue sectors + white/grey/black.
        val count = IntArray(15)
        val rSum = LongArray(15)
        val gSum = LongArray(15)
        val bSum = LongArray(15)
        for (i in pixels.indices) {
            val bucket = when {
                lum[i] < 45f -> 12 // black
                sat[i] < 0.16f && lum[i] > 205f -> 13 // white/cream
                sat[i] < 0.16f -> 14 // grey
                else -> ((hue[i] / 30f).toInt()).coerceIn(0, 11)
            }
            count[bucket]++
            val p = pixels[i]
            rSum[bucket] += (p shr 16 and 0xFF)
            gSum[bucket] += (p shr 8 and 0xFF)
            bSum[bucket] += (p and 0xFF)
        }
        val total = pixels.size.toFloat()
        return count.indices
            .map { it to count[it] }
            .filter { it.second / total >= 0.07f }
            .sortedByDescending { it.second }
            .take(4)
            .map { (bucket, c) ->
                val argb = ((0xFFL shl 24) or
                    ((rSum[bucket] / c and 0xFF) shl 16) or
                    ((gSum[bucket] / c and 0xFF) shl 8) or
                    (bSum[bucket] / c and 0xFF))
                AnalyzedColor(argb = argb, colorName = nameFor(bucket), fraction = c / total)
            }
    }

    /** Warm ochre/brown tone from raw argb: red-dominant, real spread, not near-white. */
    private fun isEarthTone(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        return r == mx && b == mn && (mx - mn) >= 40 && mx <= 210
    }

    private fun nameFor(bucket: Int): ColorName = when (bucket) {
        0 -> ColorName.RED
        1 -> ColorName.ORANGE
        2 -> ColorName.YELLOW
        3 -> ColorName.GOLD
        4 -> ColorName.GREEN
        5 -> ColorName.TEAL
        6 -> ColorName.TEAL
        7 -> ColorName.BLUE
        8 -> ColorName.INDIGO
        9 -> ColorName.PURPLE
        10 -> ColorName.PINK
        11 -> ColorName.PINK
        12 -> ColorName.BLACK
        13 -> ColorName.CREAM
        else -> ColorName.GREY
    }

    private fun separation(mean: FloatArray, withinVar: FloatArray): Float {
        if (mean.size < 4) return 0f
        val m = mean.average()
        var between = 0.0
        for (v in mean) between += (v - m) * (v - m)
        between /= mean.size
        val within = withinVar.average().coerceAtLeast(1.0)
        return (between / within).toFloat()
    }

    private fun period(profile: FloatArray): Float {
        if (profile.size < 8) return 0f
        val m = profile.average()
        var denom = 0.0
        for (v in profile) denom += (v - m) * (v - m)
        if (denom < 1e-3) return 0f
        for (lag in 3..profile.size / 2) {
            var num = 0.0
            for (i in 0 until profile.size - lag) num += (profile[i] - m) * (profile[i + lag] - m)
            if (num / denom > 0.55) return lag.toFloat()
        }
        return 0f
    }

    /** Horizontal repeat length (px): first autocorrelation peak above threshold; 0 when aperiodic. */
    private fun periodHorizontal(lum: FloatArray, w: Int, h: Int): Float {
        val maxLag = w / 2
        if (maxLag < 3) return 0f
        val step = 7
        val m = lum.average()
        var denom = 0.0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val v = lum[y * w + x] - m
                denom += v * v
            }
        }
        if (denom < 1e-3) return 0f
        val corr = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var num = 0.0
            for (y in 0 until h step step) {
                for (x in 0 until w - lag step step) {
                    num += (lum[y * w + x] - m) * (lum[y * w + x + lag] - m)
                }
            }
            corr[lag] = num / denom
        }
        for (lag in 2..maxLag) {
            if (corr[lag] > 0.55 && corr[lag] >= corr[lag - 1] && (lag == maxLag || corr[lag] >= corr[lag + 1])) {
                return lag.toFloat()
            }
        }
        return 0f
    }

    /** Vertical repeat length (px): first autocorrelation peak above threshold; 0 when aperiodic. */
    private fun periodVertical(lum: FloatArray, w: Int, h: Int): Float {
        val maxLag = h / 2
        if (maxLag < 3) return 0f
        val step = 7
        val m = lum.average()
        var denom = 0.0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val v = lum[y * w + x] - m
                denom += v * v
            }
        }
        if (denom < 1e-3) return 0f
        val corr = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var num = 0.0
            for (y in 0 until h - lag step step) {
                for (x in 0 until w step step) {
                    num += (lum[y * w + x] - m) * (lum[(y + lag) * w + x] - m)
                }
            }
            corr[lag] = num / denom
        }
        for (lag in 2..maxLag) {
            if (corr[lag] > 0.55 && corr[lag] >= corr[lag - 1] && (lag == maxLag || corr[lag] >= corr[lag + 1])) {
                return lag.toFloat()
            }
        }
        return 0f
    }

    private fun edgeDensity(lum: FloatArray, w: Int, h: Int): Float {
        var edges = 0
        var samples = 0
        for (y in 0 until h - 1) {
            for (x in 0 until w - 1) {
                val i = y * w + x
                if (abs(lum[i] - lum[i + 1]) > 32f) edges++
                if (abs(lum[i] - lum[i + w]) > 32f) edges++
                samples += 2
            }
        }
        return if (samples == 0) 0f else edges.toFloat() / samples
    }

    private fun classifyPattern(
        lumStd: Float,
        rowSep: Float,
        colSep: Float,
        edgeDensity: Float,
        colorCount: Int,
        saturation: Float,
        contrast: Float,
        meanLum: Float,
        darkFraction: Float,
        periodRow: Float,
        periodCol: Float,
        hPeriod: Float,
        vPeriod: Float,
        width: Int,
        height: Int,
        hueSpread: Float,
    ): Triple<PatternType, Float, Float> {
        val dim = min(width, height).toFloat()
        val pxPeriod = when {
            hPeriod > 0 && vPeriod > 0 -> min(hPeriod, vPeriod)
            hPeriod > 0 -> hPeriod
            vPeriod > 0 -> vPeriod
            else -> 0f
        }
        val scale = when {
            pxPeriod > 0 -> pxPeriod * 2f / dim
            periodRow > 0 -> periodRow / dim
            periodCol > 0 -> periodCol / dim
            else -> 0.5f
        }.coerceIn(0.05f, 1f)

        if (lumStd < 9f) return Triple(PatternType.SOLID, 0.85f, 0.1f)
        if (hPeriod > 0f && vPeriod > 0f && abs(hPeriod - vPeriod) <= 0.35f * max(hPeriod, vPeriod)) {
            return Triple(PatternType.CHECKED, 0.7f, scale)
        }
        if (hPeriod > 0f || vPeriod > 0f || max(rowSep, colSep) > 2.6f) {
            return Triple(PatternType.STRIPED, 0.8f, scale)
        }
        if (edgeDensity < 0.02f && lumStd > 14f) return Triple(PatternType.GRADIENT, 0.75f, 0.9f)
        if (periodRow > 0 && periodCol > 0 && scale < 0.3f) return Triple(PatternType.DOTTED, 0.6f, scale)
        if (contrast > 0.45f && colorCount <= 3 && darkFraction in 0.08f..0.45f && meanLum > 0.4f) {
            return Triple(PatternType.SYMBOLIC, 0.55f, scale)
        }
        if (colorCount >= 3 && edgeDensity > 0.08f) return Triple(PatternType.GEOMETRIC, 0.55f, scale)
        if (colorCount >= 4 && hueSpread > 0.4f) return Triple(PatternType.FLORAL, 0.5f, scale)
        if (edgeDensity > 0.04f) return Triple(PatternType.ORGANIC, 0.45f, scale)
        return Triple(PatternType.ABSTRACT, 0.4f, scale)
    }

    private fun classifyTexture(
        lum: FloatArray,
        w: Int,
        h: Int,
        edgeDensity: Float,
        meanSat: Float,
        meanLum: Float,
        glcm: TextureMetrics.Glcm,
    ): TextureType {
        // High-frequency noise estimate: mean absolute difference from a 3x3 blur.
        var noise = 0.0
        var samples = 0
        for (y in 1 until h - 1 step 2) {
            for (x in 1 until w - 1 step 2) {
                val i = y * w + x
                val blur = (lum[i - 1] + lum[i + 1] + lum[i - w] + lum[i + w] + lum[i]) / 5f
                noise += abs(lum[i] - blur)
                samples++
            }
        }
        val noiseMean = if (samples == 0) 0.0 else noise / samples
        return when {
            noiseMean > 7 -> TextureType.SLUB
            // co-occurrence contrast: strong local grey-level transitions = woven structure
            glcm.contrast > 14f || noiseMean > 4 -> TextureType.WOVEN
            edgeDensity > 0.12f -> TextureType.EMBROIDERED
            meanLum < 0.25f && meanSat > 0.35f -> TextureType.PILE
            meanLum > 0.75f && meanSat < 0.2f -> TextureType.SHEER
            glcm.homogeneity > 0.9f -> TextureType.SMOOTH
            else -> TextureType.SMOOTH
        }
    }

    private fun classifyCategory(
        pattern: PatternType,
        palette: PaletteSummary,
        texture: TextureType,
        colors: List<AnalyzedColor>,
        strips: TextureMetrics.StripSignal,
    ): Pair<FabricCategory, Float> {
        val hasIndigo = colors.any { it.colorName == ColorName.INDIGO || it.colorName == ColorName.BLUE }
        return when {
            // Northern hand-loom: 2-3 hue narrow-strip weaves in muted palettes.
            // Gonja strips lean ochre/brown-and-white; fugu (batakari) leans indigo/black/white.
            // Earth tone is judged from raw argb warmth (not the bucketed colour name, whose
            // HSL sectoring calls off-white 'orange').
            pattern == PatternType.STRIPED && palette.colorCount in 2..3 && palette.saturation < 0.45f ->
                if (colors.any { isEarthTone(it.argb) } && palette.contrast > 0.45f) FabricCategory.GONJA to 0.5f
                else FabricCategory.FUGU to 0.48f
            pattern == PatternType.STRIPED && palette.colorCount >= 4 && palette.saturation > 0.38f ->
                // periodic multi-hue banding raises confidence; aperiodic lookalikes stay lower
                FabricCategory.KENTE to (0.55f + 0.2f * strips.stripScore).coerceAtMost(0.78f)
            pattern == PatternType.STRIPED && palette.colorCount >= 3 ->
                if (strips.stripScore > 0.5f && strips.hueBandCount >= 3) {
                    FabricCategory.KENTE to 0.55f
                } else {
                    FabricCategory.KENTE_PRINT to 0.5f
                }
            pattern == PatternType.SYMBOLIC -> FabricCategory.ADINKRA to 0.55f
            pattern == PatternType.GRADIENT || (pattern == PatternType.ORGANIC && palette.contrast < 0.35f) ->
                if (palette.saturation > 0.4f) FabricCategory.TIEDYE to 0.48f else FabricCategory.BATIK to 0.5f
            (pattern == PatternType.FLORAL || pattern == PatternType.ORGANIC || pattern == PatternType.GEOMETRIC) &&
                palette.saturation > 0.5f ->
                if (palette.colorCount >= 5) FabricCategory.ANKARA to 0.55f else FabricCategory.WAX to 0.6f
            hasIndigo && (texture == TextureType.SLUB || texture == TextureType.WOVEN) && palette.saturation < 0.5f ->
                FabricCategory.DENIM to 0.5f
            pattern == PatternType.SOLID -> when {
                palette.saturation < 0.18f -> FabricCategory.COTTON_PLAIN to 0.6f
                texture == TextureType.SLUB -> FabricCategory.LINEN to 0.55f
                palette.brightness < 0.3f && palette.saturation > 0.35f -> FabricCategory.VELVET to 0.45f
                palette.saturation > 0.4f -> FabricCategory.SILK to 0.45f
                else -> FabricCategory.COTTON_PLAIN to 0.42f
            }
            else -> FabricCategory.UNKNOWN to 0.3f
        }
    }

    private fun usesFor(category: FabricCategory): List<Occasion> = when (category) {
        FabricCategory.KENTE, FabricCategory.KENTE_PRINT -> listOf(Occasion.CEREMONY, Occasion.FESTIVAL, Occasion.WEDDING)
        FabricCategory.ADINKRA -> listOf(Occasion.MOURNING, Occasion.CEREMONY)
        FabricCategory.WAX, FabricCategory.ANKARA -> listOf(Occasion.EVERYDAY, Occasion.CHURCH, Occasion.PARTY)
        FabricCategory.BATIK, FabricCategory.TIEDYE -> listOf(Occasion.EVERYDAY, Occasion.PARTY)
        FabricCategory.LACE -> listOf(Occasion.WEDDING, Occasion.CHURCH)
        FabricCategory.BROCADE -> listOf(Occasion.WEDDING, Occasion.CEREMONY)
        FabricCategory.SILK, FabricCategory.CHIFFON -> listOf(Occasion.WEDDING, Occasion.PARTY)
        FabricCategory.VELVET -> listOf(Occasion.PARTY, Occasion.CEREMONY)
        FabricCategory.DENIM -> listOf(Occasion.EVERYDAY, Occasion.OFFICE)
        FabricCategory.LINEN -> listOf(Occasion.EVERYDAY, Occasion.OFFICE)
        FabricCategory.COTTON_PLAIN -> listOf(Occasion.EVERYDAY, Occasion.OFFICE)
        FabricCategory.FUGU, FabricCategory.GONJA -> listOf(Occasion.FESTIVAL, Occasion.CEREMONY, Occasion.EVERYDAY)
        FabricCategory.UNKNOWN -> listOf(Occasion.EVERYDAY)
    }
}
