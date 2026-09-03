package com.ntoma.studio.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.MlHint
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * A real on-device neural network (MobileNetV2 1.0 224, int8-quantized, ImageNet-1k) bundled as an
 * app asset. It gives a second, honest opinion on a fabric photo: the raw ImageNet labels it sees
 * most strongly, plus a fabric hint when a label maps to a textile class we know about.
 *
 * Nothing leaves the device. The model is a coarse general-purpose vision model, not a fabric
 * specialist, and the UI says exactly that.
 */
class OnDeviceMlClassifier(private val context: Context) {

    companion object {
        private const val MODEL = "mobilenet_v2_1.0_224_quant.tflite"
        private const val LABELS = "imagenet_labels.txt"
        private const val INPUT = 224
        private const val CLASSES = 1001

        // ImageNet keywords -> fabric categories we can speak about.
        private val KEYWORD_MAP: List<Pair<Regex, FabricCategory>> = listOf(
            Regex("jean|denim") to FabricCategory.DENIM,
            Regex("velvet|velour") to FabricCategory.VELVET,
            Regex("quilt|comforter|patchwork") to FabricCategory.BROCADE,
            Regex("wool|woolen|woollen|knit|jersey, t-shirt") to FabricCategory.COTTON_PLAIN,
            Regex("tapestry|poncho|kilt|abaya") to FabricCategory.KENTE,
            Regex("handkerchief|bandanna|bandana") to FabricCategory.WAX,
            Regex("shower curtain|tablecloth|window shade|lampshade") to FabricCategory.CHIFFON,
            Regex("bath towel|towel") to FabricCategory.COTTON_PLAIN,
        )

        /** Pure keyword -> fabric mapping, public for unit tests. */
        fun hintFor(label: String): FabricCategory? {
            val lower = label.lowercase()
            return KEYWORD_MAP.firstOrNull { it.first.containsMatchIn(lower) }?.second
        }
    }

    private val interpreter by lazy {
        val file = context.assets.openFd(MODEL).fileDescriptor
        val offset = context.assets.openFd(MODEL).startOffset
        val length = context.assets.openFd(MODEL).length
        FileInputStream(file).channel.use { ch: FileChannel ->
            ch.map(FileChannel.MapMode.READ_ONLY, offset, length)
        }.let { buffer -> org.tensorflow.lite.Interpreter(buffer) }
    }

    private val labels by lazy {
        context.assets.open(LABELS).bufferedReader().use { it.readLines() }
    }

    /** Top-[max] ImageNet labels with softmax scores; empty list if the model cannot run. */
    fun classify(uri: Uri, max: Int = 3): List<MlHint> = try {
        val bitmap = decode(uri) ?: return emptyList()
        val cropped = centerCropSquare(bitmap)
        val scaled = Bitmap.createScaledBitmap(cropped, INPUT, INPUT, true)
        val input = ByteBuffer.allocateDirect(INPUT * INPUT * 3).apply {
            val px = IntArray(INPUT * INPUT)
            scaled.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
            px.forEach { p ->
                put(((p shr 16) and 0xFF).toByte())
                put(((p shr 8) and 0xFF).toByte())
                put((p and 0xFF).toByte())
            }
            rewind()
        }
        val raw = ByteArray(CLASSES)
        val output = ByteBuffer.wrap(raw)
        interpreter.run(input, output)
        val q = interpreter.getOutputTensor(0).quantizationParams()
        val logits = FloatArray(CLASSES) { i -> q.scale * (raw[i].toInt() - q.zeroPoint) }
        val maxLogit = logits.max()
        val exps = FloatArray(CLASSES) { exp((logits[it] - maxLogit).toDouble()).toFloat() }
        val sum = exps.sum()
        val top = (0 until CLASSES)
            .map { it to (exps[it] / sum) }
            .sortedByDescending { it.second }
            .take(max)
            .filter { it.second > 0.01f }
            .map { (idx, score) -> MlHint(labels.getOrElse(idx) { "?" }, score, hintFor(labels.getOrElse(idx) { "" })) }
        top
    } catch (t: Throwable) {
        emptyList()
    }

    private fun decode(uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        var sample = 1
        while (opts.outWidth / sample > 640 || opts.outHeight / sample > 640) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) }
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }
}
