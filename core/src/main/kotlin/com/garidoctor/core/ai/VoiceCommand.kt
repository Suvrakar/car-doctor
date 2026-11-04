package com.garidoctor.core.ai

import com.garidoctor.core.health.Bn
import kotlinx.serialization.Serializable

/** What a spoken sentence asks the app to log. */
@Serializable
sealed class VoiceAction {
    @Serializable data class Service(val partIds: List<String>, val costBdt: Int? = null, val odometerKm: Int? = null) : VoiceAction()
    @Serializable data class Fuel(val amount: Double?, val costBdt: Int? = null, val odometerKm: Int? = null, val cng: Boolean = false, val full: Boolean = true) : VoiceAction()
    @Serializable data class Odometer(val km: Int) : VoiceAction()
    @Serializable data object Unknown : VoiceAction()
}

/**
 * Turns a spoken Bangla or English sentence (from Android speech recognition) into a log entry:
 *   "আজ ৪০০ টাকায় তেল বদলালাম, মিটার ২৪৫০০"  → Service(engine_oil, 400, 24500)
 *   "filled 5 litres octane 650 taka at 24600"   → Fuel(5.0, 650, 24600)
 *   "সিএনজি নিলাম ৮ ঘনমিটার ৩৫০ টাকা"            → Fuel(8.0, 350, cng = true)
 *   "meter 25100"                                → Odometer(25100)
 */
object VoiceCommandParser {
    private val moneyWords = listOf("টাকা", "taka", "tk", "৳", "টাকায়")
    private val kmWords = listOf("কিমি", "কিলোমিটার", "km", "kilometer", "kilometre", "মিটার", "meter", "odometer", "odo", "রিডিং", "reading")
    private val litreWords = listOf("লিটার", "litre", "liter", "ltr", "l ")
    private val m3Words = listOf("ঘনমিটার", "কিউবিক", "cubic", "m3", "m³")
    private val fuelWords = listOf("পেট্রোল", "পেট্রল", "অকটেন", "অক্টেন", "ডিজেল", "petrol", "octane", "diesel", "fuel", "refuel", "filled", "fill up", "তেল নিলাম", "তেল ভরলাম", "তেল নিয়েছি", "ফুয়েল")
    private val cngWords = listOf("সিএনজি", "cng", "গ্যাস নিলাম", "gas")
    private val chargeWords = listOf("চার্জ", "charge", "charged")
    private val serviceVerbs = listOf("বদল", "change", "changed", "replace", "replaced", "লাগালাম", "লাগিয়েছি", "সার্ভিস", "service", "serviced", "ঠিক করলাম", "fixed", "adjust", "টাইট", "clean", "পরিষ্কার")

    private data class Num(val value: Double, val start: Int, val end: Int)

    fun parse(transcript: String, availableParts: Set<String>): VoiceAction {
        val t = " " + Bn.toAscii(transcript).lowercase().replace(",", "") + " "
        val nums = Regex("""\d+(?:\.\d+)?""").findAll(t).map { Num(it.value.toDouble(), it.range.first, it.range.last + 1) }.toList()
        fun tagged(words: List<String>): Num? = nums.firstOrNull { n ->
            val after = t.substring(n.end, (n.end + 12).coerceAtMost(t.length))
            val before = t.substring((n.start - 12).coerceAtLeast(0), n.start)
            words.any { w -> after.trimStart().startsWith(w.trim()) || before.trimEnd().endsWith(w.trim()) }
        }
        val money = tagged(moneyWords)?.value?.toInt()
        val km = tagged(kmWords)?.value?.toInt()?.takeIf { it >= 50 }
        val isCng = cngWords.any { it in t }
        val amount = tagged(if (isCng) m3Words + litreWords else litreWords)?.value

        val parts = PartWords.find(t, availableParts)
        val hasServiceVerb = serviceVerbs.any { it in t }
        val isFuel = fuelWords.any { it in t } || isCng || chargeWords.any { it in t } || amount != null
        return when {
            // "তেল বদল" / "oil change" is a service even though it contains the word for fuel.
            parts.isNotEmpty() && (hasServiceVerb || !isFuel) -> VoiceAction.Service(parts, money, km)
            isFuel -> VoiceAction.Fuel(amount ?: if (chargeWords.any { it in t }) 1.0 else null, money, km, isCng, full = !("half" in t || "অর্ধেক" in t))
            km != null -> VoiceAction.Odometer(km)
            nums.size == 1 && nums[0].value >= 100 -> VoiceAction.Odometer(nums[0].value.toInt())
            else -> VoiceAction.Unknown
        }
    }
}
