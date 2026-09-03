package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.WardrobeCategory
import com.ntoma.studio.domain.model.WardrobeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitSuggesterTest {

    private fun item(id: Long, cat: WardrobeCategory) = WardrobeItem(
        id = id,
        name = "i$id",
        category = cat,
        imageUri = "",
        notes = null,
        createdAt = 0L,
    )

    @Test
    fun `dress wins over top and pulls in available extras`() {
        val items = listOf(
            item(1, WardrobeCategory.DRESS),
            item(2, WardrobeCategory.TOP),
            item(3, WardrobeCategory.SHOES),
            item(4, WardrobeCategory.BAG),
        )
        val picked = suggestOutfitIds(items, seed = 42).toSet()
        assertTrue("dress must be included", 1L in picked)
        assertFalse("top must not be included when a dress is picked", 2L in picked)
        assertEquals(setOf(1L, 3L, 4L), picked)
    }

    @Test
    fun `top without bottom cannot form an outfit`() {
        val items = listOf(item(1, WardrobeCategory.TOP), item(2, WardrobeCategory.SHOES))
        assertTrue(suggestOutfitIds(items, seed = 7).isEmpty())
    }

    @Test
    fun `top plus bottom plus accessory forms a complete outfit`() {
        val items = listOf(
            item(1, WardrobeCategory.TOP),
            item(2, WardrobeCategory.BOTTOM),
            item(3, WardrobeCategory.ACCESSORY),
        )
        assertEquals(setOf(1L, 2L, 3L), suggestOutfitIds(items, seed = 1).toSet())
    }

    @Test
    fun `same seed yields the same outfit`() {
        val items = (1..6).map {
            item(
                it.toLong(),
                if (it % 2 == 0) WardrobeCategory.TOP else WardrobeCategory.BOTTOM,
            )
        }
        assertEquals(suggestOutfitIds(items, seed = 99), suggestOutfitIds(items, seed = 99))
    }
}
