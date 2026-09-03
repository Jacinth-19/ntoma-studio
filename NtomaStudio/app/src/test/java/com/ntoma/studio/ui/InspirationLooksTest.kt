package com.ntoma.studio.ui

import androidx.test.core.app.ApplicationProvider
import com.ntoma.studio.data.remote.AssetCatalogDataSource
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.InspirationLook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InspirationLooksTest {

    @Test
    fun `catalog loads every look with a real bundled asset`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val looks = AssetCatalogDataSource(context).fetchInspirationLooks()
        assertTrue("expected the bundled look catalog", looks.size >= 12)
        looks.forEach { look ->
            val stream = context.assets.open(look.asset)
            stream.close() // throws if the asset is missing
        }
        // Both genders and the four main Ghanaian fabric families are represented.
        assertTrue(looks.any { it.gender == GenderCategory.MEN })
        assertTrue(looks.any { it.gender == GenderCategory.WOMEN })
        val fabrics = looks.flatMap { it.fabrics }.toSet()
        listOf(FabricCategory.KENTE, FabricCategory.ANKARA, FabricCategory.WAX, FabricCategory.LACE)
            .forEach { assertTrue("$it should have reference looks", it in fabrics) }
    }

    @Test
    fun `matcher honours fabric tag and gender filter`() {
        val look = InspirationLook(
            id = "x",
            titleKey = "x",
            gender = GenderCategory.WOMEN,
            fabrics = listOf(FabricCategory.KENTE),
            asset = "a",
        )
        assertTrue(InspirationLook.matches(look, FabricCategory.KENTE, GenderCategory.WOMEN))
        assertTrue(InspirationLook.matches(look, FabricCategory.KENTE, null))
        assertTrue(InspirationLook.matches(look, null, null))
        // Unknown fabric means "show everything" rather than nothing.
        assertTrue(InspirationLook.matches(look, FabricCategory.UNKNOWN, null))
        assertFalse(InspirationLook.matches(look, FabricCategory.DENIM, null))
        assertFalse(InspirationLook.matches(look, FabricCategory.KENTE, GenderCategory.MEN))
        assertEquals(
            "gender filter alone must not fabric-gate",
            true,
            InspirationLook.matches(look, null, GenderCategory.WOMEN),
        )
    }
}
