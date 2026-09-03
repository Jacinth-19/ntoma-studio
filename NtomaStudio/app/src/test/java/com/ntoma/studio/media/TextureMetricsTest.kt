package com.ntoma.studio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureMetricsTest {

    private fun solid(w: Int, h: Int, color: Int): IntArray = IntArray(w * h) { color }

    private fun checker(w: Int, h: Int, cell: Int = 4): IntArray = IntArray(w * h) { idx ->
        val x = idx % w
        val y = idx / w
        if (((x / cell) % 2 == 0) == ((y / cell) % 2 == 0)) 0xFF14110F.toInt() else 0xFFFDF8F2.toInt()
    }

    /** Vertical woven strips: 4 hue families repeating along x with sharp band edges. */
    private fun kenteLike(w: Int, h: Int, band: Int = 8): IntArray {
        val palette = intArrayOf(0xFFC8952B.toInt(), 0xFF1F5450.toInt(), 0xFF8C2F39.toInt(), 0xFF14110F.toInt())
        return IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            palette[((x / band) + (y / (band * 2))) % 4]
        }
    }

    private fun twoToneStripes(w: Int, h: Int): IntArray = IntArray(w * h) { idx ->
        val x = idx % w
        if ((x / 8) % 2 == 0) 0xFF14110F.toInt() else 0xFFC8952B.toInt()
    }

    @Test
    fun `glcm of solid image is flat`() {
        val g = TextureMetrics.glcm(FloatArray(64 * 64) { 128f }, 64, 64)
        assertTrue(g.contrast < 1f)
        assertTrue(g.homogeneity > 0.95f)
        assertTrue(g.energy > 0.9f)
    }

    @Test
    fun `glcm of checker has high contrast and low homogeneity`() {
        val pixels = checker(64, 64)
        val lum = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            (((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3f)
        }
        val g = TextureMetrics.glcm(lum, 64, 64)
        assertTrue("contrast was ${g.contrast}", g.contrast > 10f)
        assertTrue("homogeneity was ${g.homogeneity}", g.homogeneity < 0.85f)
    }

    @Test
    fun `kente detector fires on periodic multi-hue banding`() {
        val sig = TextureMetrics.kenteStrips(kenteLike(96, 96), 96, 96)
        assertTrue("stripScore was ${sig.stripScore}", sig.stripScore > 0.5f)
        assertTrue("hueBandCount was ${sig.hueBandCount}", sig.hueBandCount >= 3)
        assertTrue(sig.periodPx > 0f)
        assertTrue("expected bands along x", sig.horizontalAxis)
    }

    @Test
    fun `kente detector stays quiet on solid cloth`() {
        val sig = TextureMetrics.kenteStrips(solid(96, 96, 0xFF8C2F39.toInt()), 96, 96)
        assertTrue(sig.stripScore < 0.3f)
        assertFalse(sig.periodPx > 0f && sig.stripScore > 0.5f)
    }

    @Test
    fun `two-tone stripes score below rich kente palette`() {
        val rich = TextureMetrics.kenteStrips(kenteLike(96, 96), 96, 96)
        val poor = TextureMetrics.kenteStrips(twoToneStripes(96, 96), 96, 96)
        assertTrue("rich=${rich.stripScore} poor=${poor.stripScore}", rich.stripScore > poor.stripScore)
        assertTrue(poor.hueBandCount <= 2)
    }

    @Test
    fun `strip period matches the synthetic band width`() {
        val sig = TextureMetrics.kenteStrips(kenteLike(96, 96, band = 8), 96, 96)
        // 4 bands of 8px repeat -> period 32
        assertEquals(32f, sig.periodPx, 4f)
    }
}
