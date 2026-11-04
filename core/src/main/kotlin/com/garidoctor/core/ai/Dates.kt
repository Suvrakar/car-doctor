package com.garidoctor.core.ai

import com.garidoctor.core.health.Bn
import java.time.LocalDate

/** A date found in text, with where it was found so callers can look at nearby keywords. */
data class FoundDate(val date: LocalDate, val start: Int, val end: Int)

/**
 * Finds dates in OCR or speech text, in the formats Bangladeshi papers and bills actually use:
 * 12/01/2026, 12-01-26, 12.01.2026, 2026-01-12, 12 Jan 2026, January 12, 2026, ১২ জানুয়ারি ২০২৬.
 * Day-first is assumed for numeric dates (the local convention).
 */
object Dates {
    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "জানুয়ারি" to 1, "জানুয়ারী" to 1, "ফেব্রুয়ারি" to 2, "ফেব্রুয়ারী" to 2, "মার্চ" to 3, "এপ্রিল" to 4, "মে" to 5, "জুন" to 6,
        "জুলাই" to 7, "আগস্ট" to 8, "অগাস্ট" to 8, "সেপ্টেম্বর" to 9, "অক্টোবর" to 10, "নভেম্বর" to 11, "ডিসেম্বর" to 12,
    )
    private val monthAlt = months.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private val numericDmy = Regex("""(?<!\d)(\d{1,2})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{4}|\d{2})(?!\d)""")
    private val numericYmd = Regex("""(?<!\d)(\d{4})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{1,2})(?!\d)""")
    private val dMonY = Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-.,]*($monthAlt)[a-z]*[\s\-.,]*(\d{4}|\d{2})(?!\d)""", RegexOption.IGNORE_CASE)
    private val monDY = Regex("""($monthAlt)[a-z]*[\s\-.,]*(\d{1,2})(?:st|nd|rd|th)?[\s,]+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    private fun year(s: String): Int = s.toInt().let { if (it < 100) 2000 + it else it }
    private fun month(s: String): Int? = months[s.lowercase()] ?: months.entries.firstOrNull { s.lowercase().startsWith(it.key) }?.value
    private fun of(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()?.takeIf { y in 1990..2100 }

    fun find(text: String): List<FoundDate> {
        val t = Bn.toAscii(text)
        val out = mutableListOf<FoundDate>()
        val taken = BooleanArray(t.length)
        fun add(d: LocalDate?, r: IntRange) {
            if (d == null || (r.first..r.last).any { taken[it] }) return
            (r.first..r.last).forEach { taken[it] = true }
            out += FoundDate(d, r.first, r.last + 1)
        }
        numericYmd.findAll(t).forEach { m -> add(of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), m.range) }
        dMonY.findAll(t).forEach { m -> month(m.groupValues[2])?.let { mo -> add(of(year(m.groupValues[3]), mo, m.groupValues[1].toInt()), m.range) } }
        monDY.findAll(t).forEach { m -> month(m.groupValues[1])?.let { mo -> add(of(year(m.groupValues[3]), mo, m.groupValues[2].toInt()), m.range) } }
        numericDmy.findAll(t).forEach { m -> add(of(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()), m.range) }
        return out.sortedBy { it.start }
    }
}

/** Numbers in OCR/speech text: Bangla digits, thousands separators ("1,250"), and taka markers. */
object Numbers {
    val number = Regex("""(?<![\d.])(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d+))?""")
    fun parseInt(s: String): Int? = s.replace(",", "").toIntOrNull()
}
