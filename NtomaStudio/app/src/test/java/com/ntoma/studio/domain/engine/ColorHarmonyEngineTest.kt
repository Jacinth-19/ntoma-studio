package com.ntoma.studio.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorHarmonyEngineTest {

    private val madder = 0xFF8C2F39 // deep red
    private val gold = 0xFFC8952B

    @Test
    fun `suggest returns complementary, analogous, neutral and metallic roles`() {
        val roles = ColorHarmonyEngine.suggest(listOf(madder, gold)).map { it.role }.toSet()
        assertTrue(ColorHarmonyEngine.Role.COMPLEMENTARY in roles)
        assertTrue(ColorHarmonyEngine.Role.ANALOGOUS in roles)
        assertTrue(ColorHarmonyEngine.Role.NEUTRAL in roles)
        assertTrue(ColorHarmonyEngine.Role.METALLIC in roles)
    }

    @Test
    fun `complementary suggestion sits roughly opposite on the hue wheel`() {
        val base = ColorHarmonyEngine.toHsl(madder)[0]
        val comp = ColorHarmonyEngine.suggest(listOf(madder))
            .first { it.role == ColorHarmonyEngine.Role.COMPLEMENTARY }
        val compHue = ColorHarmonyEngine.toHsl(comp.argb)[0]
        val rawDelta = ((compHue - base) % 360f + 360f) % 360f // 0..360
        val delta = abs(rawDelta - 180f) // 0 when perfectly opposite
        assertTrue("hue delta was $delta", delta < 15f)
    }

    @Test
    fun `warm fabrics get gold metallics and cool fabrics get silver`() {
        val warm = ColorHarmonyEngine.suggest(listOf(gold))
        assertTrue(warm.any { it.role == ColorHarmonyEngine.Role.METALLIC && it.argb == 0xFFC8952BL })
        val cool = ColorHarmonyEngine.suggest(listOf(0xFF1F5450))
        assertTrue(cool.any { it.role == ColorHarmonyEngine.Role.METALLIC && it.argb == 0xFFB9BEC4L })
    }

    @Test
    fun `suggestions honour the limit and stay opaque`() {
        val out = ColorHarmonyEngine.suggest(listOf(madder), limit = 4)
        assertEquals(4, out.size)
        out.forEach { assertTrue(it.argb ushr 24 == 0xFFL) }
    }

    @Test
    fun `empty input yields no suggestions`() {
        assertTrue(ColorHarmonyEngine.suggest(emptyList()).isEmpty())
    }
}
