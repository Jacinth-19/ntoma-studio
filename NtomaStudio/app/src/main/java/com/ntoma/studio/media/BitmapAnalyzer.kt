package com.ntoma.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.media.ColorPatternAnalyzer.FabricSignature

/**
 * Bridges Android bitmaps into the pure [ColorPatternAnalyzer].
 * Decodes a downsampled copy only — we never load the full-resolution image for analysis.
 */
class BitmapAnalyzer(private val context: Context) {

    companion object {
        private const val ANALYSIS_EDGE = 96
    }

    /** Returns null when the image cannot be decoded. */
    fun signature(uri: Uri): FabricSignature? {
        val small = decodeSampled(uri, ANALYSIS_EDGE) ?: return null
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small.isRecycled.not()) small.recycle()
        return ColorPatternAnalyzer.analyze(pixels, w, h)
    }

    /** Downsampled JPEG bytes for optional cloud upload; null when undecodable. */
    fun jpegBytes(uri: Uri, maxEdge: Int = 720, quality: Int = 82): ByteArray? {
        val bmp = decodeSampled(uri, maxEdge) ?: return null
        return try {
            java.io.ByteArrayOutputStream().also { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun decodeSampled(uri: Uri, maxEdge: Int): Bitmap? {
        return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
            ?: return null
        val rotation = readExifRotation(uri)
        if (rotation != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        bitmap
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readExifRotation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    fun toFabric(signature: FabricSignature, imageUri: String, engine: com.ntoma.studio.domain.model.AnalysisEngine): Fabric =
        Fabric(
            imageUri = imageUri,
            name = null,
            category = signature.category,
            colors = signature.colors,
            pattern = signature.pattern,
            texture = signature.texture,
            confidence = signature.categoryConfidence,
            palette = signature.palette,
            motifScale = signature.motifScale,
            printCharacteristics = signature.printTraits,
            suggestedUses = signature.suggestedUses,
            engine = engine,
            createdAt = System.currentTimeMillis(),
        )
}
