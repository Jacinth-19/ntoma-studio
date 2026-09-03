package com.ntoma.studio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemoverTest {

    private fun canvas(w: Int, h: Int, bg: Int): IntArray = IntArray(w * h) { bg }

    private fun rect(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        for (y in y0..y1) for (x in x0..x1) px[y * w + x] = color
    }

    @Test
    fun `clears plain background and keeps the subject opaque`() {
        val w = 24; val h = 24
        val px = canvas(w, h, 0xFFFFFFFF.toInt())
        rect(px, w, 8, 8, 15, 15, 0xFFCC2222.toInt())
        val out = BackgroundRemover.remove(px, w, h)
        // corner = background -> transparent, rgb preserved
        assertEquals(0, out[0] ushr 24)
        assertEquals(0xFF, out[0] and 0xFF)
        // subject centre stays opaque with its colour
        val c = out[11 * w + 11]
        assertEquals(0xFF, c ushr 24)
        assertEquals(0xFFCC2222.toInt(), c)
    }

    @Test
    fun `keeps only the largest region so specks vanish`() {
        val w = 30; val h = 30
        val px = canvas(w, h, 0xFFEFEFEF.toInt())
        rect(px, w, 4, 4, 20, 20, 0xFF224488.toInt())   // big subject
        rect(px, w, 25, 25, 27, 27, 0xFF993311.toInt()) // small speck
        val out = BackgroundRemover.remove(px, w, h)
        assertEquals(0xFF, out[10 * w + 10] ushr 24)    // subject kept
        assertEquals(0, out[26 * w + 26] ushr 24)       // speck removed
    }

    @Test
    fun `noisy border does not eat the subject`() {
        val w = 20; val h = 20
        val px = canvas(w, h, 0xFFF5F5F5.toInt())
        // jitter a few border pixels
        px[0] = 0xFF333333.toInt(); px[w - 1] = 0xFF444444.toInt()
        px[(h - 1) * w] = 0xFF555555.toInt()
        rect(px, w, 6, 6, 13, 13, 0xFF339944.toInt())
        val out = BackgroundRemover.remove(px, w, h)
        assertEquals(0xFF, out[9 * w + 9] ushr 24)
        assertEquals("border noise is an isolated speck and gets removed", 0, out[0] ushr 24)
    }
}
