package com.ntoma.studio.data.ml

import android.content.Context
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device person segmentation (DeepLabV3-MobileNet, 257x257, PASCAL 21 classes).
 * Returns a person-probability mask the size of the input, or null when the model cannot run —
 * the try-on compositor then falls back to the labelled demo overlay. Nothing is uploaded.
 */
class PersonSegmenter(private val context: Context) {

    companion object {
        private const val MODEL = "deeplabv3_257_mv.tflite"
        private const val SIZE = 257
        const val PERSON_LABEL = 15

        /** Pure: zeroes the alpha of [pixels] wherever [maskAlpha] says "person", leaving background. */
        fun backgroundPixels(pixels: IntArray, maskAlpha: IntArray): IntArray {
            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                val person = maskAlpha.getOrElse(i) { 0 }
                val keep = 255 - person // keep = background strength
                out[i] = (pixels[i] and 0x00FFFFFF) or (keep shl 24)
            }
            return out
        }
    }

    private val interpreter by lazy {
        val afd = context.assets.openFd(MODEL)
        FileInputStream(afd.fileDescriptor).channel.use { ch: FileChannel ->
            ch.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
        }.let { org.tensorflow.lite.Interpreter(it) }
    }

    /** Person-probability mask (alpha channel) at [target] size; null when unavailable. */
    fun personMask(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? = try {
        val scaled = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).apply {
            val px = IntArray(SIZE * SIZE)
            scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            for (p in px) {
                putFloat((((p shr 16) and 0xFF) / 127.5f) - 1f)
                putFloat((((p shr 8) and 0xFF) / 127.5f) - 1f)
                putFloat(((p and 0xFF) / 127.5f) - 1f)
            }
            rewind()
        }
        val classes = 21
        val out = ByteBuffer.allocateDirect(SIZE * SIZE * classes * 4)
        interpreter.run(input, out)
        out.rewind()
        val mask = IntArray(SIZE * SIZE)
        for (i in mask.indices) {
            var best = 0
            var bestV = -Float.MAX_VALUE
            for (c in 0 until classes) {
                val v = out.float
                if (v > bestV) {
                    bestV = v
                    best = c
                }
            }
            mask[i] = if (best == PERSON_LABEL) 0xFF000000.toInt() else 0
        }
        val small = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(mask, 0, SIZE, 0, 0, SIZE, SIZE)
        }
        Bitmap.createScaledBitmap(small, targetWidth, targetHeight, true)
    } catch (t: Throwable) {
        null
    }
}
