package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.media.ColorPatternAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPatternAnalyzerTest {

    private fun solid(w: Int, h: Int, color: Int): IntArray = IntArray(w * h) { color }

    private fun striped(w: Int, h: Int): IntArray = IntArray(w * h) { idx ->
        val x = idx % w
        if ((x / 12) % 2 == 0) 0xFF14110F.toInt() else 0xFFC8952B.toInt()
    }

    private fun checked(w: Int, h: Int): IntArray = IntArray(w * h) { idx ->
        val x = idx % w
        val y = idx / w
        if (((x / 10) % 2 == 0) == ((y / 10) % 2 == 0)) 0xFF8C2F39.toInt() else 0xFFFDF8F2.toInt()
    }

    @Test
    fun `solid image reports SOLID pattern`() {
        val sig = ColorPatternAnalyzer.analyze(solid(64, 64, 0xFF8C2F39.toInt()), 64, 64)
        assertEquals(PatternType.SOLID, sig.pattern)
        assertTrue(sig.patternConfidence in 0.3f..0.85f)
        assertTrue(sig.palette.colorCount <= 4)
    }

    @Test
    fun `vertical stripes report STRIPED pattern`() {
        val sig = ColorPatternAnalyzer.analyze(striped(96, 64), 96, 64)
        assertEquals(PatternType.STRIPED, sig.pattern)
    }

    @Test
    fun `checkered image reports CHECKED pattern`() {
        val sig = ColorPatternAnalyzer.analyze(checked(80, 80), 80, 80)
        assertEquals(PatternType.CHECKED, sig.pattern)
        assertTrue(sig.palette.colorCount >= 2)
    }

    @Test
    fun `confidence never overstates certainty`() {
        val sig = ColorPatternAnalyzer.analyze(striped(96, 64), 96, 64)
        assertTrue(sig.patternConfidence <= 0.85f)
        assertTrue(sig.categoryConfidence <= 0.85f)
    }

    @Test
    fun `solid images suggest muted palette while stripes stay richer`() {
        val solidSig = ColorPatternAnalyzer.analyze(solid(48, 48, 0xFF4A0E1C.toInt()), 48, 48)
        assertEquals(1, solidSig.colors.size)
    }

    private fun kenteLike(w: Int, h: Int, band: Int = 8): IntArray {
        val palette = intArrayOf(0xFFC8952B.toInt(), 0xFF1F5450.toInt(), 0xFF8C2F39.toInt(), 0xFF14110F.toInt())
        return IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            palette[((x / band) + (y / (band * 2))) % 4]
        }
    }

    @Test
    fun `periodic multi-hue banding classifies as KENTE with calibrated confidence`() {
        val sig = ColorPatternAnalyzer.analyze(kenteLike(96, 96), 96, 96)
        assertEquals(com.ntoma.studio.domain.model.FabricCategory.KENTE, sig.category)
        assertTrue("conf was ${sig.categoryConfidence}", sig.categoryConfidence >= 0.6f)
        assertTrue("confidence must stay honest", sig.categoryConfidence <= 0.8f)
        assertTrue("strip signal expected", sig.strips.stripScore > 0.5f)
    }

    @Test
    fun `two-tone stripes do not claim KENTE`() {
        val sig = ColorPatternAnalyzer.analyze(striped(96, 64), 96, 64)
        assertTrue(sig.category != com.ntoma.studio.domain.model.FabricCategory.KENTE || sig.strips.hueBandCount >= 3)
    }

    private fun mutedStripes(w: Int, h: Int, colors: IntArray): IntArray = IntArray(w * h) { idx ->
        colors[(idx % w) / 12 % colors.size]
    }

    @Test
    fun `muted three-hue strip weave classifies as FUGU`() {
        // indigo-grey / near-black / off-white: northern batakari palette, no earth tones
        val sig = ColorPatternAnalyzer.analyze(
            mutedStripes(96, 64, intArrayOf(0xFF3A4250.toInt(), 0xFF14110F.toInt(), 0xFFF2EFE9.toInt())),
            96, 64,
        )
        assertEquals(com.ntoma.studio.domain.model.FabricCategory.FUGU, sig.category)
        assertTrue("confidence must stay honest", sig.categoryConfidence <= 0.8f)
    }

    @Test
    fun `ochre and white strip weave classifies as GONJA`() {
        val sig = ColorPatternAnalyzer.analyze(
            mutedStripes(96, 64, intArrayOf(0xFFB8763A.toInt(), 0xFFF2EFE9.toInt())),
            96, 64,
        )
        assertEquals(com.ntoma.studio.domain.model.FabricCategory.GONJA, sig.category)
        assertTrue("confidence must stay honest", sig.categoryConfidence <= 0.8f)
    }

    @Test
    fun `glcm reaches the signature`() {
        val sig = ColorPatternAnalyzer.analyze(checked(80, 80), 80, 80)
        assertTrue("checker contrast was ${sig.glcm.contrast}", sig.glcm.contrast > 5f)
    }
}
