package com.ntoma.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.Measurements
import java.io.File

/**
 * One-page order-ready tailor brief as a PDF: design, fabric photo, customizations and the
 * user's OWN measurements (clearly labelled approximate, user-provided). No invented sewing
 * specs. Rendered with android.graphics.PdfDocument — no dependencies.
 */
object BriefPdfWriter {

    data class Brief(
        val tailorName: String,
        val designTitle: String?,
        val fabricName: String?,
        val fabricImageUri: String?,
        val customizations: List<String>,
        val measurements: Measurements?,
        val notes: String?,
    )

    /** Writes cacheDir/shared/brief.pdf and returns the file (shared via FileProvider). */
    fun write(context: Context, brief: Brief): File? = try {
        val pageW = 595
        val pageH = 842
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        val canvas: Canvas = page.canvas
        val margin = 48f
        var y = 64f

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = 0xFF14110F.toInt()
            isFakeBoldText = true
        }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = 0xFF8C2F39.toInt(); isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF14110F.toInt() }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF6B625B.toInt() }

        canvas.drawText(context.getString(R.string.app_name) + " — " + context.getString(R.string.tailor_quote_title), margin, y, title)
        y += 22f
        canvas.drawText(context.getString(R.string.tailors_title) + ": " + brief.tailorName, margin, y, body)
        y += 24f

        brief.designTitle?.let {
            canvas.drawText(context.getString(R.string.brief_pdf_design), margin, y, h2)
            y += 16f
            canvas.drawText(it, margin, y, body)
            y += 22f
        }

        brief.fabricImageUri?.let { uri ->
            val bmp = try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                canvas.drawText(context.getString(R.string.brief_pdf_fabric), margin, y, h2)
                y += 8f
                val targetW = 180f
                val scale = targetW / bmp.width
                val targetH = (bmp.height * scale).coerceAtMost(200f)
                canvas.drawBitmap(bmp, null, android.graphics.RectF(margin, y, margin + targetW, y + targetH), null)
                brief.fabricName?.let { canvas.drawText(it, margin + targetW + 12, y + 14, body) }
                y += targetH + 20f
            }
        }

        if (brief.customizations.isNotEmpty()) {
            canvas.drawText(context.getString(R.string.brief_pdf_custom), margin, y, h2)
            y += 16f
            brief.customizations.forEach { line ->
                canvas.drawText("• " + line, margin, y, body)
                y += 15f
            }
            y += 8f
        }

        brief.measurements?.let { m ->
            canvas.drawText(context.getString(R.string.brief_pdf_measurements), margin, y, h2)
            y += 16f
            canvas.drawText(m.summaryForPdf(context), margin, y, body)
            y += 14f
            canvas.drawText(context.getString(R.string.measurements_disclaimer), margin, y, small)
            y += 18f
        }

        brief.notes?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(context.getString(R.string.brief_notes, ""), margin, y, h2)
            y += 16f
            canvas.drawText(it, margin, y, body)
            y += 18f
        }

        canvas.drawText(context.getString(R.string.brief_pdf_footer), margin, 800f, small)

        doc.finishPage(page)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ntoma_brief.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    } catch (t: Throwable) {
        null
    }

    private fun Measurements.summaryForPdf(context: Context): String = buildList {
        heightCm?.let { add(context.getString(R.string.measurement_height) + ": $it") }
        chestCm?.let { add(context.getString(R.string.measurement_chest) + ": $it") }
        waistCm?.let { add(context.getString(R.string.measurement_waist) + ": $it") }
        hipCm?.let { add(context.getString(R.string.measurement_hip) + ": $it") }
        shoulderCm?.let { add(context.getString(R.string.measurement_shoulder) + ": $it") }
        sleeveCm?.let { add(context.getString(R.string.measurement_sleeve) + ": $it") }
        inseamCm?.let { add(context.getString(R.string.measurement_inseam) + ": $it") }
        neckCm?.let { add(context.getString(R.string.measurement_neck) + ": $it") }
    }.joinToString("   ")
}
