package com.ntoma.studio.ui.screens.profile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.ntoma.studio.domain.model.FabricCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContributionOpsTest {

    private fun ctx() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun seed(category: FabricCategory, name: String): File {
        val dir = File(ctx().filesDir, "contributions/${category.name}").apply { mkdirs() }
        return File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `recategorize moves file and rewrites the category prefix`() {
        val f = seed(FabricCategory.WAX, "WAX_20260901_120000.jpg")
        assertTrue(recategorizeContribution(ctx(), f, FabricCategory.KENTE))
        val moved = File(ctx().filesDir, "contributions/KENTE/KENTE_20260901_120000.jpg")
        assertTrue("expected renamed file at ${moved.path}", moved.exists())
        assertFalse("original must be gone", f.exists())
    }

    @Test
    fun `recategorize never overwrites an existing file`() {
        seed(FabricCategory.LACE, "LACE_20260901_120000.jpg")
        val f = seed(FabricCategory.WAX, "WAX_20260901_120000.jpg")
        assertTrue(recategorizeContribution(ctx(), f, FabricCategory.LACE))
        val dir = File(ctx().filesDir, "contributions/LACE")
        val names = dir.list()?.sorted() ?: emptyList()
        assertEquals(listOf("LACE_20260901_120000.jpg", "LACE_20260901_120000_2.jpg"), names)
    }

    @Test
    fun `export zip contains a manifest listing every file`() {
        seed(FabricCategory.WAX, "WAX_a.jpg")
        seed(FabricCategory.KENTE, "KENTE_b.jpg")
        val zip = exportContributions(ctx())
        assertNotNull("export should produce a zip", zip)
        java.util.zip.ZipFile(zip!!).use { z ->
            val names = z.entries().toList().map { it.name }
            assertEquals(3, names.size)
            assertTrue(names.contains("manifest.json"))
            val json = org.json.JSONObject(
                z.getInputStream(z.getEntry("manifest.json")).reader().readText()
            )
            assertTrue(json.getString("exportedAt").isNotBlank())
            assertEquals(2, json.getJSONArray("files").length())
            val first = json.getJSONArray("files").getJSONObject(0)
            assertTrue(first.has("category"))
            assertTrue(first.has("file"))
        }
    }

    private fun gradient(shift: Int, flip: Boolean): Bitmap {
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val sx = if (flip) 15 - x else x
                val v = (sx * 16 + y + shift) and 0xFF
                b.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return b
    }

    @Test
    fun `identical images produce zero hash distance`() {
        val h1 = dHash(gradient(0, flip = false))
        val h2 = dHash(gradient(0, flip = false))
        assertEquals(0, hammingDistance(h1, h2))
    }

    @Test
    fun `horizontally flipped gradient exceeds the duplicate threshold`() {
        val h1 = dHash(gradient(0, flip = false))
        val h2 = dHash(gradient(0, flip = true))
        assertTrue(
            "flipped image should differ, got distance ${hammingDistance(h1, h2)}",
            hammingDistance(h1, h2) > 6,
        )
    }
}
