package com.ntoma.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.Outcome
import java.io.File

/**
 * Memory-safe image loading, validation, transformation and storage.
 * Everything the camera / photo-picker flows need, in one place.
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val MAX_PIXELS = 20_000_000L // ~20MP guard before we even decode
        const val EDIT_MAX_EDGE = 1600
        const val SAVE_MAX_EDGE = 1280
        const val JPEG_QUALITY = 88
    }

    fun validate(uri: Uri): Outcome<Uri> {
        val type = context.contentResolver.getType(uri)
        if (type != null && !type.startsWith("image/")) return Outcome.Failure(AppError.UnsupportedImage)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return Outcome.Failure(AppError.InvalidImage)
        } catch (e: Exception) {
            return Outcome.Failure(AppError.InvalidImage)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Outcome.Failure(AppError.InvalidImage)
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS * 4) return Outcome.Failure(AppError.ImageTooLarge)
        return Outcome.Success(uri)
    }

    fun load(uri: Uri, maxEdge: Int): Outcome<Bitmap> {
        return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return Outcome.Failure(AppError.InvalidImage)
        if (options.outWidth <= 0 || options.outHeight <= 0) return Outcome.Failure(AppError.InvalidImage)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return Outcome.Failure(AppError.InvalidImage)
        if (bitmap.width == 0 || bitmap.height == 0) return Outcome.Failure(AppError.InvalidImage)
        Outcome.Success(bitmap)
        } catch (oom: OutOfMemoryError) {
            Outcome.Failure(AppError.Memory)
        } catch (e: Exception) {
            Outcome.Failure(AppError.InvalidImage)
        }
    }

    /**
     * Applies rotation / scale / pan + aspect crop in view space, mirroring what the user saw
     * in the editor overlay.
     */
    fun transform(
        source: Bitmap,
        rotationDeg: Int,
        scale: Float,
        panX: Float,
        panY: Float,
        viewW: Int,
        viewH: Int,
        coverScale: Float,
    ): Bitmap {
        val matrix = Matrix()
        // Work in the view's coordinate space: centre the source, then undo the user transforms.
        matrix.postTranslate(source.width / -2f, source.height / -2f)
        matrix.postScale(1f / (coverScale * scale), 1f / (coverScale * scale))
        matrix.postRotate(-rotationDeg.toFloat())
        matrix.postTranslate(-panX, -panY)
        matrix.postTranslate(viewW / 2f, viewH / 2f)
        // The matrix above maps source->view; invert to sample source from view pixels.
        val inverse = Matrix()
        matrix.invert(inverse)

        val out = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.setMatrix(inverse)
        canvas.drawBitmap(source, 0f, 0f, null)
        return out
    }

    fun downscaleForSave(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= SAVE_MAX_EDGE) return bitmap
        val ratio = SAVE_MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    fun save(bitmap: Bitmap, prefix: String): File {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            downscaleForSave(bitmap).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return file
    }

    fun shareUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, file.name)

    fun shareUriForPath(path: String): Uri = shareUriFor(File(path))

    fun cacheForShare(bitmap: Bitmap, name: String): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, name)
    }
}
