package com.garidoctor.core.ai

import com.garidoctor.core.health.Bn
import com.garidoctor.core.model.DocumentType
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** What a paper scan found. Any field may be missing; the user always reviews before saving. */
@Serializable
data class ScannedDocument(
    val type: DocumentType? = null,
    val number: String? = null,
    val expiryEpochDay: Long? = null,
    /** 0..1: how sure we are, used to decide whether to offer an AI read. */
    val confidence: Double = 0.0,
    val source: String = "device",
) {
    val complete: Boolean get() = type != null && expiryEpochDay != null
}

/**
 * On-device paper reader: takes ML Kit OCR text from a registration certificate, tax token,
 * fitness certificate, insurance, licence, route permit or CNG retest certificate and finds
 * the type, number and expiry date. Free and offline; Gemini is only the fallback.
 */
object DocumentTextParser {
    private val typeKeywords: List<Pair<DocumentType, List<String>>> = listOf(
        DocumentType.CNG_CYLINDER_RETEST to listOf("cylinder", "retest", "re-test", "সিলিন্ডার", "রিটেস্ট"),
        DocumentType.ROUTE_PERMIT to listOf("route permit", "রুট পারমিট", "রুট পার্মিট"),
        DocumentType.FITNESS to listOf("fitness", "ফিটনেস", "উপযুক্ততা"),
        DocumentType.TAX_TOKEN to listOf("tax token", "tax-token", "ট্যাক্স টোকেন", "ট্যাক্স", "কর টোকেন"),
        DocumentType.INSURANCE to listOf("insurance", "policy no", "cover note", "insured", "ইন্স্যুরেন্স", "ইনস্যুরেন্স", "বীমা"),
        DocumentType.DRIVING_LICENCE to listOf("driving licence", "driving license", "licence no", "license no", "dl no", "ড্রাইভিং লাইসেন্স", "লাইসেন্স"),
        DocumentType.REGISTRATION to listOf("registration certificate", "certificate of registration", "blue book", "registration no", "reg. no", "রেজিস্ট্রেশন", "নিবন্ধন"),
    )
    /** Words that mark the end of a validity period. */
    private val expiryKeys = listOf("valid till", "valid until", "valid upto", "valid up to", "validity", "expiry", "expires", "expire", "exp.", "date of expiry",
        "to ", "till", "upto", "মেয়াদ", "মেয়াদোত্তীর্ণ", "পর্যন্ত", "শেষ")
    private val issueKeys = listOf("issue", "issued", "from", "date of issue", "ইস্যু", "প্রদানের", "হইতে", "থেকে", "registration date")
    private val numberPatterns = listOf(
        // Vehicle registration: "DHAKA METRO-LA-12-3456", "Dhaka Metro Ga 11-2233", "ঢাকা মেট্রো-ল ১২-৩৪৫৬"
        Regex("""([A-Za-z]+\s+METRO[\s\-]*[A-Za-z]{1,3}[\s\-]*\d{2}[\s\-]*\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""((?:ঢাকা|চট্ট|খুলনা|রাজশাহী|সিলেট|বরিশাল|রংপুর|ময়মনসিংহ)[^\n]{0,12}?মেট্রো[\s\-]*\S{1,3}[\s\-]*\d{2}[\s\-]*\d{4})"""),
        // "Policy No: MC-DHK-0099812" — same line only, and the value must contain a digit.
        Regex("""(?:policy|licen[cs]e|dl|token|certificate|reg(?:istration)?|cover[ \t]*note|serial)[ \t]*(?:no|number|#)?\.?[ \t]*[:\-]?[ \t]*((?=[A-Z0-9/\-]*\d)[A-Z0-9][A-Z0-9/\-]{4,24})""", RegexOption.IGNORE_CASE),
        Regex("""(?:নং|নম্বর|নাম্বার)[ \t]*[:\-]?[ \t]*((?=[A-Za-z0-9/\-]*\d)[A-Za-z0-9/\-]{4,24})"""),
    )

    fun parse(text: String, todayEpochDay: Long): ScannedDocument {
        val lower = text.lowercase()
        val type = typeKeywords.maxByOrNull { (_, keys) -> keys.count { it in lower } }?.takeIf { (_, keys) -> keys.any { it in lower } }?.first
        val number = numberPatterns.firstNotNullOfOrNull { p -> p.find(Bn.toAscii(text))?.groupValues?.get(1)?.trim()?.trimEnd('-', '/') }
        val expiry = pickExpiry(text, todayEpochDay)
        var c = 0.0
        if (type != null) c += 0.4
        if (expiry != null) c += 0.45
        if (number != null) c += 0.15
        return ScannedDocument(type, number, expiry, c)
    }

    /** Prefer a date right after an expiry keyword; then the later date of a from–to pair; then the latest plausible date. */
    fun pickExpiry(text: String, todayEpochDay: Long): Long? {
        val t = Bn.toAscii(text).lowercase()
        val dates = Dates.find(text).filter { it.date.toEpochDay() > todayEpochDay - 365 * 3 && it.date.toEpochDay() < todayEpochDay + 365 * 12 }
        if (dates.isEmpty()) return null
        fun near(d: FoundDate, keys: List<String>) = keys.any { k -> t.substring((d.start - 28).coerceAtLeast(0), d.start).contains(k) }
        dates.firstOrNull { near(it, expiryKeys) && !near(it, issueKeys.filter { k -> k != "from" }) }?.let { return it.date.toEpochDay() }
        if (dates.size >= 2) return dates.maxOf { it.date.toEpochDay() }
        val only = dates.single()
        // A lone date marked as an issue date is not an expiry.
        return if (near(only, issueKeys)) null else only.date.toEpochDay()
    }

    fun today(): Long = LocalDate.now().toEpochDay()
}
