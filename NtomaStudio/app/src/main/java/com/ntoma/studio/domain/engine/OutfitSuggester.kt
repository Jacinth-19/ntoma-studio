package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.WardrobeCategory
import com.ntoma.studio.domain.model.WardrobeItem
import kotlin.random.Random

/**
 * Deterministic "surprise me" outfit picker.
 *
 * Core rule: one DRESS, **or** one TOP + one BOTTOM (both required). If neither core is
 * possible the result is empty — the wardrobe simply can't form an outfit yet.
 * Optional slots (one each, only when present): SHOES, BAG, ACCESSORY.
 *
 * Selection is seeded so the same wardrobe + seed always yields the same outfit
 * (testable; "surprise" comes from a fresh time-based seed at the call site).
 */
fun suggestOutfitIds(items: List<WardrobeItem>, seed: Long): List<Long> {
    val rng = Random(seed)
    fun pick(cat: WardrobeCategory): WardrobeItem? =
        items.filter { it.category == cat }
            .let { pool -> if (pool.isEmpty()) null else pool[rng.nextInt(pool.size)] }

    val core: List<WardrobeItem> = when {
        items.any { it.category == WardrobeCategory.DRESS } ->
            listOfNotNull(pick(WardrobeCategory.DRESS))
        items.any { it.category == WardrobeCategory.TOP } &&
            items.any { it.category == WardrobeCategory.BOTTOM } ->
            listOfNotNull(pick(WardrobeCategory.TOP), pick(WardrobeCategory.BOTTOM))
        else -> emptyList()
    }
    if (core.isEmpty()) return emptyList()

    val extras = listOfNotNull(
        pick(WardrobeCategory.SHOES),
        pick(WardrobeCategory.BAG),
        pick(WardrobeCategory.ACCESSORY),
    )
    return (core + extras).map { it.id }
}
