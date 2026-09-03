package com.ntoma.studio.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Outcome
import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.domain.repository.GenerationInput
import com.ntoma.studio.domain.repository.ImageGenerationRepository
import com.ntoma.studio.media.ImageProcessor
import com.ntoma.studio.media.garment.GarmentGeometry
import java.io.File
import kotlin.math.roundToInt

/**
 * Honest demo compositor: drapes the detected colours/pattern onto the garment outline over the
 * person photo. It demonstrates scale + palette, and the UI labels the output accordingly.
 */
class DemoLookGenerator(
    private val imageProcessor: ImageProcessor,
    private val segmenter: com.ntoma.studio.data.ml.PersonSegmenter? = null,
) : ImageGenerationRepository {

    override suspend fun generate(input: GenerationInput): Outcome<File> {
        val person = when (val loaded = imageProcessor.load(android.net.Uri.parse(input.personImagePath), input.maxDimension)) {
            is Outcome.Success -> loaded.data
            is Outcome.Failure -> return Outcome.Failure(loaded.error)
        }
        return try {
            val result = composite(person, input.fabric, input.style, input.customization, input.variationSeed)
            Outcome.Success(imageProcessor.save(result, "look"))
        } catch (oom: OutOfMemoryError) {
            Outcome.Failure(com.ntoma.studio.domain.model.AppError.Memory)
        } catch (e: Exception) {
            Outcome.Failure(com.ntoma.studio.domain.model.AppError.Generation)
        } finally {
            if (!person.isRecycled) person.recycle()
        }
    }

    /**
     * Honest demo compositing: fabric placement and contrast accents are reflected visually;
     * sleeve/neckline/length choices travel with the request to a real backend and appear in the
     * tailor brief (the demo render does not reshape the silhouette, and never claims to).
     */
    private fun composite(
        person: Bitmap,
        fabric: Fabric,
        style: DressStyle,
        customization: com.ntoma.studio.domain.model.DesignCustomization = com.ntoma.studio.domain.model.DesignCustomization(),
        variationSeed: Int = 0,
    ): Bitmap {
        val out = Bitmap.createBitmap(person.width, person.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(person, 0f, 0f, null)
        // If the on-device segmenter runs, background pixels are kept pristine so the fabric
        // draping only touches the person; otherwise the labelled demo overlay covers the frame.
        val mask = segmenter?.personMask(person, person.width, person.height)
        if (mask != null) {
            val p = IntArray(person.width * person.height)
            person.getPixels(p, 0, person.width, 0, 0, person.width, person.height)
            val m = IntArray(person.width * person.height)
            mask.getPixels(m, 0, person.width, 0, 0, person.width, person.height)
            val ma = IntArray(m.size) { i -> (m[i] ushr 24) and 0xFF }
            val restored = Bitmap.createBitmap(
                com.ntoma.studio.data.ml.PersonSegmenter.backgroundPixels(p, ma),
                person.width, person.height, Bitmap.Config.ARGB_8888,
            )
            canvas.drawBitmap(restored, 0f, 0f, null)
        }

        val pattern = patternBitmap(fabric)
        val shader = BitmapShader(pattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        // Variation seeds deterministically explore placements so "generate variations" yields
        // visibly different (but reproducible) results.
        val placement = if (variationSeed > 0 && customization.placement == com.ntoma.studio.domain.model.FabricPlacement.FULL) {
            when (variationSeed % 3) {
                1 -> com.ntoma.studio.domain.model.FabricPlacement.PANELS
                2 -> com.ntoma.studio.domain.model.FabricPlacement.ACCENTS
                else -> com.ntoma.studio.domain.model.FabricPlacement.TOP_ONLY
            }
        } else {
            customization.placement
        }
        val accent = customization.accentArgb?.toInt()
            ?: darken(fabric.colors.lastOrNull()?.argb?.toInt() ?: 0xFF14110F.toInt())
        val neutral = darken(fabric.colors.firstOrNull()?.argb?.toInt() ?: 0xFF14110F.toInt())

        val rect = garmentRect(out.width, out.height, style.gender)
        val polys = GarmentGeometry.shapesFor(style.silhouette)
        val shapes = polys.map { poly ->
            val path = Path().apply {
                poly.forEachIndexed { i, (x, y) ->
                    val px = rect.left + x * rect.width()
                    val py = rect.top + y * rect.height()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            val centroidY = poly.map { it.second }.average().toFloat()
            Pair(path, centroidY)
        }

        val fabricFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            alpha = 232
        }
        val solidFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 235 }
        val accentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 235 }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            this.style = Paint.Style.STROKE
            strokeWidth = out.width / 220f
        }
        val shade = Paint().apply {
            this.shader = android.graphics.LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                Color.argb(40, 0, 0, 0), Color.argb(0, 0, 0, 0),
                Shader.TileMode.CLAMP,
            )
        }

        shapes.forEachIndexed { index, (path, centroidY) ->
            val useFabric = when (placement) {
                com.ntoma.studio.domain.model.FabricPlacement.FULL -> true
                com.ntoma.studio.domain.model.FabricPlacement.TOP_ONLY -> centroidY < 0.5f
                com.ntoma.studio.domain.model.FabricPlacement.SKIRT_ONLY -> centroidY >= 0.5f
                com.ntoma.studio.domain.model.FabricPlacement.PANELS -> index % 2 == 0
                com.ntoma.studio.domain.model.FabricPlacement.ACCENTS -> false
            }
            when {
                useFabric -> canvas.drawPath(path, fabricFill)
                placement == com.ntoma.studio.domain.model.FabricPlacement.PANELS -> canvas.drawPath(path, accentFill)
                else -> {
                    solidFill.color = neutral
                    canvas.drawPath(path, solidFill)
                }
            }
            canvas.drawPath(path, shade)
            canvas.drawPath(path, outline)
        }

        if (placement == com.ntoma.studio.domain.model.FabricPlacement.ACCENTS) {
            // Fabric band across the garment as the accent.
            val band = Path().apply {
                addRect(
                    RectF(rect.left, rect.top + rect.height() * 0.44f, rect.right, rect.top + rect.height() * 0.54f),
                    Path.Direction.CW,
                )
            }
            val combined = Path()
            shapes.forEach { (path, _) -> combined.op(path, band, android.graphics.Path.Op.INTERSECT) }
            canvas.drawPath(combined, fabricFill)
        }
        return out
    }

    private fun garmentRect(w: Int, h: Int, gender: GenderCategory): RectF {
        val left = if (gender == GenderCategory.MEN) 0.22f else 0.2f
        val right = if (gender == GenderCategory.MEN) 0.78f else 0.8f
        return RectF(w * left, h * 0.26f, w * right, h * 0.96f)
    }

    /** Builds a small tiling swatch from the analysed palette + pattern type. */
    private fun patternBitmap(fabric: Fabric): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = fabric.colors.map { it.argb.toInt() }.ifEmpty { listOf(0xFFC8952B.toInt(), 0xFF8C2F39.toInt()) }
        val base = colors.first()
        canvas.drawColor(base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun colorAt(i: Int) = colors.getOrElse(i) { colors[i % colors.size] }

        when (fabric.pattern) {
            PatternType.STRIPED -> {
                val band = size / (colors.size * 2).coerceAtLeast(4)
                var y = 0
                var i = 0
                while (y < size) {
                    paint.color = colorAt(i % colors.size)
                    canvas.drawRect(0f, y.toFloat(), size.toFloat(), (y + band).toFloat(), paint)
                    y += band
                    i++
                }
            }
            PatternType.CHECKED -> {
                val cell = size / 4
                for (cy in 0 until 4) for (cx in 0 until 4) {
                    paint.color = if ((cx + cy) % 2 == 0) colorAt(0) else colorAt(1)
                    canvas.drawRect((cx * cell).toFloat(), (cy * cell).toFloat(), ((cx + 1) * cell).toFloat(), ((cy + 1) * cell).toFloat(), paint)
                }
            }
            PatternType.DOTTED, PatternType.FLORAL, PatternType.ORGANIC -> {
                val cell = size / 4
                for (cy in 0 until 4) for (cx in 0 until 4) {
                    paint.color = colorAt((cx * 3 + cy) % colors.size)
                    canvas.drawCircle(cx * cell + cell / 2f, cy * cell + cell / 2f, cell / 3f, paint)
                }
            }
            PatternType.GRADIENT -> {
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, size.toFloat(), colorAt(0), colorAt(1), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            }
            else -> {
                val cell = size / 3
                for (cy in 0 until 3) for (cx in 0 until 3) {
                    paint.color = colorAt((cx + cy * 2) % colors.size)
                    canvas.drawRect((cx * cell).toFloat(), (cy * cell).toFloat(), ((cx + 1) * cell).toFloat(), ((cy + 1) * cell).toFloat(), paint)
                }
            }
        }
        return bmp
    }

    private fun darken(argb: Int): Int {
        val r = ((argb shr 16 and 0xFF) * 0.55f).roundToInt()
        val g = ((argb shr 8 and 0xFF) * 0.55f).roundToInt()
        val b = ((argb and 0xFF) * 0.55f).roundToInt()
        return Color.argb(255, r, g, b)
    }

}
