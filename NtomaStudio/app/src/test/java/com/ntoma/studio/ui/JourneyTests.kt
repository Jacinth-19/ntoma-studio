package com.ntoma.studio.ui

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ntoma.studio.DeepLinks
import com.ntoma.studio.MainActivity
import com.ntoma.studio.data.ml.PersonSegmenter
import com.ntoma.studio.data.remote.CloudAnalysisClient
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.AnalysisEngine
import com.ntoma.studio.ui.screens.fabric.AnalysisViewModel
import com.ntoma.studio.ui.screens.tryon.TryOnViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import android.graphics.Bitmap
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeepLinkIntentTest {

    @Test
    fun `launch intent with deep link is posted to the bus`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("app://look/42")
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        assertEquals("app://look/42", DeepLinks.pending.value)
        activity.finish()
        DeepLinks.consume()
    }

    @Test
    fun `onNewIntent replaces a pending deep link`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = Intent(context, MainActivity::class.java)
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        assertNull(DeepLinks.pending.value)
        controller.newIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("app://design/kaba_slit"), context, MainActivity::class.java),
        )
        assertEquals("app://design/kaba_slit", DeepLinks.pending.value)
        DeepLinks.consume()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewModelJourneyTest {

    @Test
    fun `analysis view model completes a full on-device analysis`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer.get(context)
        // synthetic kente-like sample written to the cache
        val w = 96
        val h = 96
        val palette = intArrayOf(0xFFC8952B.toInt(), 0xFF1F5450.toInt(), 0xFF8C2F39.toInt(), 0xFF14110F.toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h) { i -> palette[((i % w) / 8 + (i / w) / 16) % 4] }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val file = java.io.File(context.cacheDir, "journey_kente.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Drive the repository flow directly (real delays) — the VM layer is a thin mapper.
        val result = try {
            runBlocking {
                container.fabricAnalysis.analyze(Uri.fromFile(file).toString())
                    .collect { e ->
                        if (e is com.ntoma.studio.domain.repository.AnalysisEvent.Completed ||
                            e is com.ntoma.studio.domain.repository.AnalysisEvent.Failed
                        ) throw JourneyResult(e)
                    }
            }
            null
        } catch (j: JourneyResult) {
            j.event
        }
        assertTrue("expected Completed, got $result", result is com.ntoma.studio.domain.repository.AnalysisEvent.Completed)
        val fabric = (result as com.ntoma.studio.domain.repository.AnalysisEvent.Completed).fabric
        assertEquals(AnalysisEngine.ON_DEVICE_DEMO, fabric.engine)
        assertTrue("honest confidence range", fabric.confidence in 0.3f..0.8f)
    }

    @Test
    fun `try-on view model ignores start until every input is set`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer.get(context)
        val vm = TryOnViewModel(container, null, null, null)
        vm.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(!vm.state.value.running)
        assertNull(vm.state.value.fabricId)
        vm.setFabric(7L)
        vm.setStyle("kaba_slit")
        vm.setPerson("/tmp/person.jpg")
        assertEquals(7L, vm.state.value.fabricId)
        assertEquals("kaba_slit", vm.state.value.styleId)
        assertEquals("/tmp/person.jpg", vm.state.value.personPath)
    }
}

class PersonMaskTest {

    @Test
    fun `background pixels keep their colour while person pixels are dropped`() {
        val pixels = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 0xFFAABBCC.toInt())
        val mask = intArrayOf(0x00, 0xFF, 0xFF, 0x00)
        val out = PersonSegmenter.backgroundPixels(pixels, mask)
        assertEquals(0xFF112233.toInt(), out[0]) // background kept
        assertEquals(0x00445566, out[1]) // person erased
        assertEquals(0x00778899, out[2])
        assertEquals(0xFFAABBCC.toInt(), out[3])
    }

    @Test
    fun `missing mask entries count as background`() {
        val out = PersonSegmenter.backgroundPixels(intArrayOf(0xFF101010.toInt()), IntArray(0))
        assertEquals(0xFF101010.toInt(), out[0])
    }
}

class CloudEngineTest {

    @Test
    fun `blank url never selects the cloud engine`() {
        val client = CloudAnalysisClient()
        assertEquals(AnalysisEngine.ON_DEVICE_DEMO, client.engineFor(""))
        assertEquals(AnalysisEngine.ON_DEVICE_DEMO, client.engineFor("   "))
        assertEquals(AnalysisEngine.CLOUD, client.engineFor("https://cv.example.com/"))
    }

    @Test
    fun `unreachable backend resolves to null without throwing`() = runBlocking {
        val client = CloudAnalysisClient()
        assertNull(client.analyze("http://127.0.0.1:9/", byteArrayOf(1, 2, 3)))
    }
}

private class JourneyResult(val event: com.ntoma.studio.domain.repository.AnalysisEvent) : Exception()

