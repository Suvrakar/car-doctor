package com.garidoctor.app.passport

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.model.Lang
import com.garidoctor.core.passport.VehiclePassport
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.time.LocalDate

/** Renders the Vehicle Passport to an A4 PDF using the built-in PdfDocument API (no server). */
object PassportPdf {
    private const val W = 595; private const val H = 842; private const val M = 40f

    fun write(context: Context, p: VehiclePassport, partName: (String) -> String, lang: Lang): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(14, 124, 102); isAntiAlias = true }
        val h2 = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        val muted = Paint().apply { textSize = 9f; color = Color.GRAY; isAntiAlias = true }
        fun t(en: String, bn: String) = if (lang == Lang.BN) bn else en
        var y = M + 10

        c.drawText(t("GariDoctor Vehicle Passport", "গাড়িডাক্তার ভেহিকল পাসপোর্ট"), M, y, title); y += 26
        c.drawText("${p.vehicle.nickname} · ${p.templateName}${p.modelName?.let { " · $it" } ?: ""}", M, y, h2); y += 18
        c.drawText(t("Registration", "রেজিস্ট্রেশন") + ": ${p.vehicle.registrationNo ?: "-"}", M, y, body); y += 15
        c.drawText(t("Odometer", "ওডোমিটার") + ": ${"%,d".format(p.vehicle.odometerKm)} km", M, y, body); y += 15
        c.drawText(t("Report date", "রিপোর্টের তারিখ") + ": ${LocalDate.ofEpochDay(p.generatedEpochDay)}", M, y, body); y += 22

        // health score badge
        val band = p.health.band
        val bandColor = when (band) { HealthBand.EXCELLENT, HealthBand.GOOD -> Color.rgb(30, 142, 62); HealthBand.FAIR -> Color.rgb(230, 150, 0); else -> Color.rgb(200, 40, 40) }
        val ring = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = bandColor; isAntiAlias = true }
        c.drawCircle(M + 40, y + 40, 34f, ring)
        val big = Paint().apply { textSize = 24f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        c.drawText("${p.health.score}", M + 40, y + 48, big)
        c.drawText(t("Health score", "হেলথ স্কোর") + " · ${band.label.get(lang)}", M + 95, y + 30, h2)
        p.health.deductions.take(3).forEachIndexed { i, d -> c.drawText("• ${d.reason.get(lang)} (−${d.points})", M + 95, y + 48 + i * 14, body) }

        // QR code
        val qr = qrBitmap(p.qrPayload(), 120)
        c.drawBitmap(qr, W - M - 120, M - 10, null)
        c.drawText(t("Scan to verify", "যাচাই করতে স্ক্যান করুন"), W - M - 110, M + 122, muted)
        y += 100

        fun newPageIfNeeded() {
            if (y > H - 60) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); c = page.canvas; y = M + 10
            }
        }

        c.drawText(t("Papers", "কাগজপত্র"), M, y, h2); y += 16
        if (p.documents.isEmpty()) { c.drawText("-", M, y, body); y += 14 }
        p.documents.forEach { d ->
            val left = d.expiryEpochDay - p.generatedEpochDay
            val st = if (left < 0) t("EXPIRED", "মেয়াদোত্তীর্ণ") else t("valid", "বৈধ")
            c.drawText("${d.type.label.get(lang)}: ${LocalDate.ofEpochDay(d.expiryEpochDay)} ($st)", M, y, body); y += 14; newPageIfNeeded()
        }
        y += 8
        p.fuel?.average?.let { avg ->
            c.drawText(t("Average efficiency", "গড় মাইলেজ") + ": ${"%.1f".format(avg)}", M, y, h2); y += 22
        }

        c.drawText(t("Service history", "সার্ভিস ইতিহাস") + " (${p.services.size})", M, y, h2); y += 16
        val cols = floatArrayOf(M, M + 90, M + 330, M + 430)
        listOf(t("Date", "তারিখ"), t("Work done", "কাজ"), t("Odometer", "ওডোমিটার"), t("Cost ৳", "খরচ ৳")).forEachIndexed { i, s -> c.drawText(s, cols[i], y, muted) }
        y += 14
        p.services.asReversed().forEach { s ->
            c.drawText(LocalDate.ofEpochDay(s.epochDay).toString(), cols[0], y, body)
            c.drawText(partName(s.partId).take(40), cols[1], y, body)
            c.drawText("%,d".format(s.odometerKm), cols[2], y, body)
            c.drawText(s.costBdt?.let { "%,d".format(it) } ?: "-", cols[3], y, body)
            y += 14; newPageIfNeeded()
        }
        y += 16; newPageIfNeeded()
        c.drawText(t("Fingerprint", "ফিঙ্গারপ্রিন্ট") + ": ${p.fingerprint.take(32)}", M, y, muted); y += 12
        c.drawText(t("Self-reported by the owner in GariDoctor. Not a garage inspection unless marked verified.",
            "মালিক নিজে গাড়িডাক্তার অ্যাপে তথ্য দিয়েছেন। 'ভেরিফায়েড' চিহ্ন না থাকলে এটি গ্যারেজ পরিদর্শন নয়।"), M, y, muted)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "passports").apply { mkdirs() }
        val safe = p.vehicle.nickname.replace(Regex("[^A-Za-z0-9]+"), "_").ifBlank { "vehicle" }
        val f = File(dir, "GariDoctor_Passport_${safe}_${p.generatedEpochDay}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "Share passport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun qrBitmap(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}
