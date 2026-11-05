package com.garidoctor.app.passport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.model.L10n
import com.garidoctor.core.model.Lang
import java.io.File

/**
 * The shareable Vehicle ID card: a PNG with the QR and the same references written underneath,
 * so it is useful even to someone who can't scan it. Also printable as a one-page PDF.
 */
object IdCard {
    private const val W = 1080
    private val TEAL = Color.rgb(0, 107, 88)
    val SCAN_HINT = L10n("Scan with any phone camera", "যেকোনো ফোনের ক্যামেরা দিয়ে স্ক্যান করুন")

    fun render(id: VehicleIdentity, lang: Lang, includeDriver: Boolean): Bitmap {
        val fields = (if (includeDriver) VehicleIdentity.Field.entries else VehicleIdentity.VEHICLE_FIELDS).mapNotNull { f -> id.value(f)?.let { f to it } }
        val qrSize = 680
        val rowH = 104
        val h = 220 + qrSize + 60 + fields.size * rowH + 120
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val rtl = lang.rtl
        val x0 = if (rtl) W - 72f else 72f
        val align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align
        }
        // header band
        c.drawRect(0f, 0f, W.toFloat(), 150f, Paint().apply { color = TEAL })
        c.drawText(VehicleIdentity.HEADER.get(lang), x0, 95f, paint(44f, true, Color.WHITE))
        c.drawText(id.nickname, x0, 215f, paint(52f, true))
        val kind = listOfNotNull(id.vehicleType, id.makeModel).filter { it.isNotBlank() }.joinToString(" · ")
        c.drawText(kind, x0, 268f, paint(34f, color = Color.DKGRAY))
        // QR
        val qr = Printing.qrBitmap(id.qrText(lang, includeDriver), qrSize)
        val qx = (W - qrSize) / 2f; val qy = 300f
        c.drawRoundRect(RectF(qx - 16, qy - 16, qx + qrSize + 16, qy + qrSize + 16), 24f, 24f, Paint().apply { color = TEAL; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true })
        c.drawBitmap(qr, qx, qy, null)
        var y = qy + qrSize + 80
        val label = paint(30f, color = Color.DKGRAY); val value = paint(38f, true)
        fields.forEach { (f, v) ->
            // Label above value: long labels (German, Bangla) never collide with long numbers.
            c.drawText(f.label.get(lang), x0, y, label)
            c.drawText(v, x0, y + 44, value)
            y += rowH
        }
        c.drawText(SCAN_HINT.get(lang), x0, h - 50f, paint(28f, color = Color.GRAY))
        return bmp
    }

    fun png(context: Context, id: VehicleIdentity, lang: Lang, includeDriver: Boolean): File {
        val f = File(Printing.outDir(context), "CarDoctorAI_ID_${safe(id.nickname)}.png")
        f.outputStream().use { render(id, lang, includeDriver).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** One A4 page with the card centred, for printing and laminating. */
    fun pdf(context: Context, id: VehicleIdentity, lang: Lang, includeDriver: Boolean): File {
        val card = render(id, lang, includeDriver)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val scale = minOf(420f / card.width, 760f / card.height)
        val w = card.width * scale; val h = card.height * scale
        val left = (595 - w) / 2; val top = 40f
        page.canvas.drawBitmap(card, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        page.canvas.drawRect(RectF(left, top, left + w, top + h), Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.5f })
        doc.finishPage(page)
        val f = File(Printing.outDir(context), "CarDoctorAI_ID_${safe(id.nickname)}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "vehicle" }
}
