package com.garidoctor.core.battery

import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.L10n
import kotlin.math.roundToInt

/** A full-charge range observation for an electric vehicle (easy-bike / e-scooter). */
data class RangeSample(val epochDay: Long, val rangeKm: Double)

data class BatteryReport(
    val healthPercent: Int,
    val estimatedCycles: Int,
    val ratedCycles: Int,
    val cyclesLeft: Int,
    val currentRangeKm: Double?,
    val ratedRangeKm: Double,
    val advice: List<L10n>,
)

/**
 * Battery-set health for electric easy-bikes. Owners lose a lot of money when a battery set
 * dies early; this estimates state-of-health from range fade and charge cycles.
 */
object BatteryHealth {
    /** Typical cycle life before ~70-80% capacity. */
    fun ratedCycles(type: EnergyType) = when (type) {
        EnergyType.ELECTRIC_LITHIUM -> 1500
        else -> 400 // deep-cycle lead-acid used in easy-bikes
    }

    fun report(
        type: EnergyType,
        ratedRangeKm: Double,
        chargeCount: Int,
        partialChargeFractions: Double,
        samples: List<RangeSample>,
        daysSinceWaterTopUp: Long?,
    ): BatteryReport {
        val rated = ratedCycles(type)
        val cycles = (chargeCount + partialChargeFractions).roundToInt()
        // Use the median of the latest 3 samples to dampen one-off bad days.
        val latest = samples.sortedBy { it.epochDay }.takeLast(3).map { it.rangeKm }.sorted()
        val current = if (latest.isEmpty()) null else latest[latest.size / 2]
        val byRange = current?.let { (it / ratedRangeKm * 100).coerceIn(0.0, 100.0) }
        // Linear fade to 70% at rated cycles if we have no range data.
        val byCycles = (100.0 - 30.0 * cycles / rated).coerceIn(0.0, 100.0)
        val health = (byRange ?: byCycles).roundToInt()
        val advice = mutableListOf<L10n>()
        if (type == EnergyType.ELECTRIC_LEAD_ACID && (daysSinceWaterTopUp == null || daysSinceWaterTopUp > 30)) {
            advice += L10n("Top up distilled water in each cell (not tap water)", "প্রতিটি সেলে ডিস্টিলড পানি দিন (কলের পানি নয়)")
        }
        if (health < 70) advice += L10n("Battery set is near end of life. Test each battery individually; replacing one weak battery can save the set.", "ব্যাটারি সেট প্রায় শেষ। প্রতিটি ব্যাটারি আলাদা টেস্ট করুন; একটি দুর্বল ব্যাটারি বদলালে পুরো সেট বাঁচতে পারে।")
        else if (health < 85) advice += L10n("Range fading. Avoid running below 20% and avoid overnight overcharging.", "রেঞ্জ কমছে। ২০% এর নিচে চালাবেন না এবং সারারাত ওভারচার্জ করবেন না।")
        if (byRange != null && byRange < byCycles - 15) advice += L10n("Range is lower than battery age suggests: check charger, terminals and tyre pressure.", "ব্যাটারির বয়সের তুলনায় রেঞ্জ কম: চার্জার, টার্মিনাল ও টায়ারের হাওয়া চেক করুন।")
        return BatteryReport(health, cycles, rated, (rated - cycles).coerceAtLeast(0), current, ratedRangeKm, advice)
    }
}
