package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.StyleCategory

/**
 * Tiny on-device natural-language layer for search: maps phrases like "something for a wedding"
 * or "simple men's kaftan" onto structured filters. Deliberately transparent (keyword rules, no
 * black box); a backend NLU can replace it later through the same [SearchIntent] contract.
 */
object SearchIntentParser {

    data class SearchIntent(
        val occasions: Set<Occasion> = emptySet(),
        val gender: GenderCategory? = null,
        val category: StyleCategory? = null,
        /** Fabric-affinity keywords, e.g. "kente" -> filter designs that suit kente. */
        val fabricKeywords: Set<String> = emptySet(),
        /** Style adjectives for ranking/copy: simple, elegant, modern… */
        val adjectives: Set<String> = emptySet(),
        /** Leftover text for plain title/description matching. */
        val remainder: String = "",
    )

    private val occasionWords = mapOf(
        "wedding" to Occasion.WEDDING, "bride" to Occasion.WEDDING, "engagement" to Occasion.WEDDING,
        "church" to Occasion.CHURCH, "service" to Occasion.CHURCH,
        "funeral" to Occasion.MOURNING, "mourning" to Occasion.MOURNING,
        "office" to Occasion.OFFICE, "work" to Occasion.OFFICE, "interview" to Occasion.OFFICE,
        "party" to Occasion.PARTY, "birthday" to Occasion.PARTY, "date" to Occasion.PARTY,
        "everyday" to Occasion.EVERYDAY, "daily" to Occasion.EVERYDAY, "casual wear" to Occasion.EVERYDAY,
        "graduation" to Occasion.CEREMONY, "ceremony" to Occasion.CEREMONY, "naming" to Occasion.CEREMONY,
        "outdooring" to Occasion.CEREMONY,
        "festival" to Occasion.FESTIVAL, "homowo" to Occasion.FESTIVAL, "odwira" to Occasion.FESTIVAL,
        "akwasidae" to Occasion.FESTIVAL, "christmas" to Occasion.FESTIVAL, "eid" to Occasion.FESTIVAL,
    )

    private val adjectiveWords = setOf(
        "simple", "plain", "minimal", "elegant", "flashy", "bold", "modern", "contemporary", "classic",
    )

    private val fabricWords = setOf("kente", "adinkra", "wax", "ankara", "batik", "lace", "brocade", "linen", "silk", "denim", "fugu", "smock", "batakari", "gonja")

    fun parse(raw: String): SearchIntent {
        val text = raw.lowercase().trim()
        if (text.isEmpty()) return SearchIntent()
        val tokens = text.split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        var working = text

        val occasions = mutableSetOf<Occasion>()
        occasionWords.forEach { (word, occ) ->
            if (working.contains(word)) {
                occasions += occ
                working = working.replace(word, " ")
            }
        }

        val gender = when {
            tokens.any { it in setOf("men", "man", "male", "him", "his", "mens", "men's") } -> GenderCategory.MEN
            tokens.any { it in setOf("women", "woman", "female", "ladies", "lady", "her", "hers", "womens", "women's") } -> GenderCategory.WOMEN
            tokens.any { it in setOf("unisex", "anyone") } -> GenderCategory.UNISEX
            else -> null
        }

        val category = when {
            tokens.any { it in setOf("formal", "elegant", "official") } -> StyleCategory.FORMAL
            tokens.any { it in setOf("traditional", "culture", "customary") } -> StyleCategory.TRADITIONAL
            tokens.any { it in setOf("casual", "relaxed", "everyday") } -> StyleCategory.CASUAL
            else -> null
        }

        val fabrics = fabricWords.filter { working.contains(it) }.toSet()
        fabrics.forEach { working = working.replace(it, " ") }

        val adjectives = adjectiveWords.filter { tokens.contains(it) || working.contains(it) }.toSet()
        adjectives.forEach { working = working.replace(it, " ") }

        val stop = setOf("show", "me", "something", "find", "search", "for", "a", "an", "the", "of", "some", "any", "suitable", "look", "looks", "design", "designs", "dress", "dresses", "outfit", "outfits", "to", "my", "i", "want", "need", "but", "not", "too")
        val remainder = working.split(Regex("[^a-z']+"))
            .filter { it.isNotBlank() && it !in stop && it.length > 2 }
            .joinToString(" ")

        return SearchIntent(
            occasions = occasions,
            gender = gender,
            category = category,
            fabricKeywords = fabrics,
            adjectives = adjectives,
            remainder = remainder,
        )
    }
}
