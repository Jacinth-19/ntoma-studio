package com.ntoma.studio.data.ml

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ON-DEVICE QA for the bundled DeepLabV3 segmenter — the piece no emulator/CI here can cover.
 * Run on a real phone (GPU delegate, native libs, real memory pressure):
 *   ./gradlew :app:connectedDebugAndroidTest
 * or filtered:
 *   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ntoma.studio.data.ml.PersonSegmenterDeviceTest
 *
 * The host-side contract + math QA lives in tools/qa_deeplabv3.py; this exercises the exact
 * production code path (PersonSegmenter.personMask) on device hardware.
 */
@RunWith(AndroidJUnit4::class)
class PersonSegmenterDeviceTest {

    private fun scene(w: Int = 480, h: Int = 640): Bitmap {
        // sky / grass / person-shaped blob: not photoreal, but forces the full interpreter path
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            bmp.setPixel(
                x, y,
                when {
                    y > h * 0.75 -> Color.rgb(70, 110, 60)
                    x in (w * 0.38).toInt()..(w * 0.62).toInt() &&
                        y in (h * 0.15).toInt()..(h * 0.78).toInt() -> Color.rgb(180, 130, 100)
                    else -> Color.rgb(150, 190, 235)
                },
            )
        }
        return bmp
    }

    @Test
    fun segmenterRunsOnDeviceAndReturnsMaskOfRequestedSize() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val segmenter = PersonSegmenter(context)
        val src = scene()
        val t0 = System.nanoTime()
        val mask = segmenter.personMask(src, 240, 320)
        val ms = (System.nanoTime() - t0) / 1_000_000
        // On supported devices this MUST succeed; null means the model failed to load or run.
        assertNotNull("personMask returned null — TFLite failed on this device (see logcat)", mask)
        assertEquals(240, mask!!.width)
        assertEquals(320, mask.height)
        println("PersonSegmenter on-device inference: ${ms}ms for 480x640 -> 240x320")
        // Sanity bound: a cold interpreter + resize should stay usable on any supported phone.
        assertTrue("inference suspiciously slow: ${ms}ms", ms < 15_000)
        src.recycle()
        mask.recycle()
    }

    @Test
    fun backgroundPixelsContractHoldsForFullSizeMasks() {
        val w = 64; val h = 64
        val px = IntArray(w * h) { 0xFF336699.toInt() }
        val mask = IntArray(w * h) { if (it % 2 == 0) 0xFF000000.toInt() else 0 }
        val out = PersonSegmenter.backgroundPixels(px, mask)
        for (i in px.indices) {
            val alpha = out[i] ushr 24
            assertEquals(if (i % 2 == 0) 0 else 255, alpha)
            assertEquals("rgb must survive", 0x99, out[i] and 0xFF)
        }
    }
}
