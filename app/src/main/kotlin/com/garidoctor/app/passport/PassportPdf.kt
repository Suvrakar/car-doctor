package com.garidoctor.app.passport

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.garidoctor.core.health.HealthBand
import com.garidoctor.core.history.History
import com.garidoctor.core.history.HistoryEntry
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.model.Lang
import com.garidoctor.core.passport.VehiclePassport
import java.io.File
import java.time.LocalDate

/**
 * Renders the Vehicle Passport to an A4 PDF using the built-in PdfDocument API (no server):
 * vehicle and driver references, health score, papers, spending and the full bill history.
 * The QR holds the same references as plain text plus a check line (date, score, fingerprint).
 */
object PassportPdf {
    private const val W = 595; private const val H = 842; private const val M = 40f

    fun write(
        context: Context, p: VehiclePassport, partName: (String) -> String, lang: Lang,
        identity: VehicleIdentity? = null, includeDriver: Boolean = true, history: List<HistoryEntry> = emptyList(),
    ): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(14, 124, 102); isAntiAlias = true }
        val h2 = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        val muted = Paint().apply { textSize = 9f; color = Color.GRAY; isAntiAlias = true }
        fun t(en: String, bn: String) = lang.pick(en, bn)
        var y = M + 10

        c.drawText(t("GariDoctor Vehicle Passport", "গাড়িডাক্তার ভেহিকল পাসপোর্ট"), M, y, title); y += 26
        c.drawText("${p.vehicle.nickname} · ${p.templateName}${p.modelName?.let { " · $it" } ?: ""}", M, y, h2); y += 18
        val idFields = if (identity != null) VehicleIdentity.VEHICLE_FIELDS.mapNotNull { f -> identity.value(f)?.let { f to it } }
            else listOfNotNull(p.vehicle.registrationNo?.let { VehicleIdentity.Field.REGISTRATION to it })
        if (idFields.isEmpty()) { c.drawText(VehicleIdentity.Field.REGISTRATION.label.get(lang) + ": -", M, y, body); y += 15 }
        idFields.forEach { (f, v) -> c.drawText(f.label.get(lang) + ": " + v, M, y, body); y += 15 }
        c.drawText(t("Odometer", "ওডোমিটার") + ": ${"%,d".format(p.vehicle.odometerKm)} km", M, y, body); y += 15
        c.drawText(t("Report date", "রিপোর্টের তারিখ") + ": ${LocalDate.ofEpochDay(p.generatedEpochDay)}", M, y, body); y += 15
        if (identity != null && includeDriver) {
            val d = listOf(VehicleIdentity.Field.DRIVER, VehicleIdentity.Field.LICENCE, VehicleIdentity.Field.PHONE).mapNotNull { f -> identity.value(f)?.let { f.label.get(lang) + ": " + it } }
            if (d.isNotEmpty()) { y += 4; c.drawText(d.joinToString("   ·   "), M, y, body); y += 15 }
        }
        y += 7

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
        val qrText = (identity?.qrText(lang, includeDriver) ?: p.qrPayload()) +
            "\n" + t("Report", "রিপোর্ট") + ": ${LocalDate.ofEpochDay(p.generatedEpochDay)} · ${p.health.score}/100 · ${p.fingerprint.take(12)}"
        val qr = Printing.qrBitmap(qrText, 300)
        c.drawBitmap(qr, null, android.graphics.RectF(W - M - 130, M - 14, W - M, M + 116), null)
        c.drawText(t("Scan with any phone camera", "যেকোনো ফোনের ক্যামেরা দিয়ে স্ক্যান করুন"), W - M - 140, M + 128, muted)
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
            val no = d.number?.let { " · " + t("No.", "নং") + " $it" } ?: ""
            c.drawText("${d.type.label.get(lang)}$no: ${LocalDate.ofEpochDay(d.expiryEpochDay)} ($st)", M, y, body); y += 14; newPageIfNeeded()
        }
        y += 8
        p.fuel?.average?.let { avg ->
            c.drawText(t("Average efficiency", "গড় মাইলেজ") + ": ${"%.1f".format(avg)}", M, y, h2); y += 22
        }

        val cols = floatArrayOf(M, M + 90, M + 330, M + 430)
        if (history.isNotEmpty()) {
            val sp = History.spend(history, p.generatedEpochDay)
            c.drawText(t("Spending", "খরচ"), M, y, h2); y += 16
            c.drawText(t("Total", "মোট") + ": ৳${"%,d".format(sp.totalBdt)}   ·   " + t("Last 12 months", "গত ১২ মাস") + ": ৳${"%,d".format(sp.last12MonthsBdt)}", M, y, body); y += 14
            c.drawText(t("Service", "সার্ভিস") + ": ৳${"%,d".format(sp.serviceBdt)}   ·   " + t("Fuel", "জ্বালানি") + ": ৳${"%,d".format(sp.fuelBdt)}   ·   " +
                t("Other bills", "অন্যান্য বিল") + ": ৳${"%,d".format(sp.otherBdt)}", M, y, body); y += 22
            newPageIfNeeded()
            c.drawText(t("History", "ইতিহাস") + " (${history.size})", M, y, h2); y += 16
            listOf(t("Date", "তারিখ"), t("Item", "বিবরণ"), t("Odometer", "ওডোমিটার"), t("Cost ৳", "খরচ ৳")).forEachIndexed { i, s -> c.drawText(s, cols[i], y, muted) }
            y += 14
            History.sorted(history).forEach { e ->
                c.drawText(LocalDate.ofEpochDay(e.epochDay).toString(), cols[0], y, body)
                c.drawText((e.title + (e.detail?.let { " · $it" } ?: "")).take(44), cols[1], y, body)
                c.drawText(e.odometerKm?.let { "%,d".format(it) } ?: "-", cols[2], y, body)
                c.drawText(e.amountBdt?.let { "%,d".format(it) } ?: "-", cols[3], y, body)
                y += 14; newPageIfNeeded()
            }
        } else {
            c.drawText(t("Service history", "সার্ভিস ইতিহাস") + " (${p.services.size})", M, y, h2); y += 16
            listOf(t("Date", "তারিখ"), t("Work done", "কাজ"), t("Odometer", "ওডোমিটার"), t("Cost ৳", "খরচ ৳")).forEachIndexed { i, s -> c.drawText(s, cols[i], y, muted) }
            y += 14
            p.services.asReversed().forEach { s ->
                c.drawText(LocalDate.ofEpochDay(s.epochDay).toString(), cols[0], y, body)
                c.drawText(partName(s.partId).take(40), cols[1], y, body)
                c.drawText("%,d".format(s.odometerKm), cols[2], y, body)
                c.drawText(s.costBdt?.let { "%,d".format(it) } ?: "-", cols[3], y, body)
                y += 14; newPageIfNeeded()
            }
        }
        y += 16; newPageIfNeeded()
        c.drawText(t("Fingerprint", "ফিঙ্গারপ্রিন্ট") + ": ${p.fingerprint.take(32)}", M, y, muted); y += 12
        c.drawText(t("Self-reported by the owner in GariDoctor. Not a garage inspection unless marked verified.",
            "মালিক নিজে গাড়িডাক্তার অ্যাপে তথ্য দিয়েছেন। 'ভেরিফায়েড' চিহ্ন না থাকলে এটি গ্যারেজ পরিদর্শন নয়।"), M, y, muted)
        doc.finishPage(page)

        val dir = Printing.outDir(context)
        val safe = p.vehicle.nickname.replace(Regex("[^A-Za-z0-9]+"), "_").ifBlank { "vehicle" }
        val f = File(dir, "GariDoctor_Passport_${safe}_${p.generatedEpochDay}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(context: Context, file: File) = Printing.share(context, file, "application/pdf", "Share passport")
}
