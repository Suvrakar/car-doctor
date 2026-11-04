package com.garidoctor.core.ai

import com.garidoctor.core.health.Bn
import kotlinx.serialization.Serializable

@Serializable
data class ReceiptItem(val label: String, val partId: String? = null, val amountBdt: Int? = null)

@Serializable
data class ScannedReceipt(
    val items: List<ReceiptItem> = emptyList(),
    val totalBdt: Int? = null,
    val epochDay: Long? = null,
    val odometerKm: Int? = null,
    val garage: String? = null,
    val confidence: Double = 0.0,
    val source: String = "device",
) {
    /** Items the app can log as services (matched to a part of this vehicle). */
    val serviceItems: List<ReceiptItem> get() = items.filter { it.partId != null }
}

/**
 * On-device garage-bill reader. Works line by line on ML Kit OCR text: a line that names a
 * part becomes an item with the amount at its end; "total/মোট" lines give the total; "km",
 * "odo", "কিমি" give the odometer; the first date is the bill date.
 */
object ReceiptTextParser {
    private val totalWords = listOf("grand total", "total", "net amount", "net payable", "amount payable", "মোট", "সর্বমোট", "টোটাল")
    private val odoWords = listOf("odo", "odometer", "km", "k.m", "kms", "mileage", "reading", "কিমি", "কি.মি", "মিটার")
    private val skipAmountWords = listOf("phone", "mobile", "cell", "tel", "invoice", "bill no", "memo", "মোবাইল", "ফোন", "মেমো")

    fun parse(text: String, availableParts: Set<String>): ScannedReceipt {
        val lines = Bn.toAscii(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<ReceiptItem>()
        var total: Int? = null
        var odo: Int? = null
        for (line in lines) {
            val l = line.lowercase()
            if (skipAmountWords.any { it in l }) continue
            val nums = Numbers.number.findAll(line).map { it.groupValues[1] }.mapNotNull { Numbers.parseInt(it) }.toList()
            if (odoWords.any { w -> Regex("""(?<![a-z])${Regex.escape(w)}(?![a-z])""").containsMatchIn(l) }) {
                nums.filter { it in 100..1_999_999 }.maxOrNull()?.let { if (odo == null) odo = it }
                if (totalWords.none { it in l }) continue
            }
            if (totalWords.any { it in l }) { nums.lastOrNull()?.takeIf { it in 1..10_000_000 }?.let { total = it }; continue }
            val parts = PartWords.find(line, availableParts)
            if (parts.isEmpty()) continue
            // Amount = last number on the line that looks like money (not a quantity like "1" or "4L").
            val amount = Numbers.number.findAll(line).toList().lastOrNull { m ->
                val v = Numbers.parseInt(m.groupValues[1]) ?: 0
                val after = line.substring(m.range.last + 1).trimStart().lowercase()
                v >= 20 && !after.startsWith("l") && !after.startsWith("ml") && !after.startsWith("pcs") && !after.startsWith("লিটার")
            }?.let { Numbers.parseInt(it.groupValues[1]) }
            val label = line.replace(Regex("""[৳]|\btk\.?|\btaka\b|টাকা"""), "").replace(Regex("""\s{2,}"""), " ").trim().take(60)
            parts.forEach { pid -> items += ReceiptItem(label, pid, if (parts.size == 1) amount else null) }
        }
        if (total == null && items.isNotEmpty()) total = items.mapNotNull { it.amountBdt }.takeIf { it.isNotEmpty() }?.sum()
        val date = Dates.find(text).firstOrNull()?.date?.toEpochDay()
        var c = 0.0
        if (items.isNotEmpty()) c += 0.55
        if (total != null) c += 0.2
        if (date != null) c += 0.15
        if (odo != null) c += 0.1
        return ScannedReceipt(items.distinctBy { it.partId }, total, date, odo, null, c)
    }
}
