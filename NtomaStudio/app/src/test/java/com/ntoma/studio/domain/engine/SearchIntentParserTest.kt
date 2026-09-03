package com.ntoma.studio.domain.engine

import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.StyleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIntentParserTest {

    @Test
    fun `wedding phrase maps to the WEDDING occasion`() {
        val intent = SearchIntentParser.parse("Show me something for a wedding")
        assertTrue(Occasion.WEDDING in intent.occasions)
    }

    @Test
    fun `mens simple designs map to MEN gender and simple adjective`() {
        val intent = SearchIntentParser.parse("Find simple men's designs")
        assertEquals(GenderCategory.MEN, intent.gender)
        assertTrue("simple" in intent.adjectives)
    }

    @Test
    fun `modern kente dresses extract the kente keyword`() {
        val intent = SearchIntentParser.parse("Show modern Kente dresses")
        assertTrue("kente" in intent.fabricKeywords)
        assertTrue("modern" in intent.adjectives)
    }

    @Test
    fun `church suitability maps to CHURCH occasion`() {
        val intent = SearchIntentParser.parse("Something suitable for church")
        assertTrue(Occasion.CHURCH in intent.occasions)
    }

    @Test
    fun `elegant but not flashy stays structured with adjectives`() {
        val intent = SearchIntentParser.parse("Something elegant but not too flashy")
        assertTrue("elegant" in intent.adjectives)
        assertEquals(StyleCategory.FORMAL, intent.category)
    }

    @Test
    fun `Ghanaian occasion words are understood`() {
        assertTrue(Occasion.MOURNING in SearchIntentParser.parse("outfit for a funeral").occasions)
        assertTrue(Occasion.FESTIVAL in SearchIntentParser.parse("Homowo celebration dress").occasions)
        assertTrue(Occasion.CEREMONY in SearchIntentParser.parse("baby outdooring kaba").occasions)
    }

    @Test
    fun `empty input yields an empty intent`() {
        val intent = SearchIntentParser.parse("   ")
        assertTrue(intent.occasions.isEmpty())
        assertNull(intent.gender)
        assertEquals("", intent.remainder)
    }

    @Test
    fun `plain text falls through to remainder for title matching`() {
        val intent = SearchIntentParser.parse("mermaid gown")
        assertTrue(intent.remainder.contains("mermaid"))
    }
}
