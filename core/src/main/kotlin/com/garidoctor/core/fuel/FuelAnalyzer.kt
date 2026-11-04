package com.garidoctor.core.fuel

import com.garidoctor.core.model.EfficiencyUnit
import com.garidoctor.core.model.L10n
import kotlin.math.roundToInt

/**
 * One fill-up or charge. [amount] is litres, m³ of CNG, or fraction of a full charge (1.0 = full).
 * Efficiency is only computed between two *full* fills (the classic tank-to-tank method);
 * partial fills are accumulated into the next full one.
 */
data class FuelEntry(
    val odometerKm: Int,
    val amount: Double,
    val priceBdt: Int?,
    val full: Boolean,
    val epochDay: Long,
)

data class EfficiencyPoint(val epochDay: Long, val km: Int, val amount: Double, val efficiency: Double)

data class EfficiencyTrend(
    val unit: EfficiencyUnit,
    val baseline: Double,
    val recent: Double,
    /** Positive = got worse. */
    val dropPercent: Double,
)

data class FuelAlert(val title: L10n, val causes: List<L10n>, val dropPercent: Int)

data class FuelStats(
    val points: List<EfficiencyPoint>,
    val average: Double?,
    val trend: EfficiencyTrend?,
    val totalSpentBdt: Int,
    val costPerKmBdt: Double?,
    val avgKmPerDay: Double?,
)

object FuelAnalyzer {
    const val ALERT_DROP_PERCENT = 15.0
    const val RECENT_DAYS = 30L
    const val MIN_BASELINE_POINTS = 3

    fun points(entries: List<FuelEntry>): List<EfficiencyPoint> {
        val sorted = entries.sortedWith(compareBy({ it.odometerKm }, { it.epochDay }))
        val out = mutableListOf<EfficiencyPoint>()
        var lastFull: FuelEntry? = null
        var acc = 0.0
        for (e in sorted) {
            if (lastFull == null) { if (e.full) lastFull = e; continue }
            acc += e.amount
            if (e.full) {
                val km = e.odometerKm - lastFull.odometerKm
                if (km > 0 && acc > 0) out += EfficiencyPoint(e.epochDay, km, acc, km / acc)
                lastFull = e
                acc = 0.0
            }
        }
        return out
    }

    fun stats(entries: List<FuelEntry>, unit: EfficiencyUnit, todayEpochDay: Long): FuelStats {
        val pts = points(entries)
        val totalKm = pts.sumOf { it.km }
        val totalAmt = pts.sumOf { it.amount }
        val spent = entries.sumOf { it.priceBdt ?: 0 }
        val sorted = entries.sortedBy { it.epochDay }
        val kmPerDay = if (sorted.size >= 2) {
            val days = (sorted.last().epochDay - sorted.first().epochDay).coerceAtLeast(1)
            (sorted.maxOf { it.odometerKm } - sorted.minOf { it.odometerKm }).toDouble() / days
        } else null
        val spanKm = if (sorted.size >= 2) sorted.maxOf { it.odometerKm } - sorted.minOf { it.odometerKm } else 0
        return FuelStats(
            points = pts,
            average = if (totalAmt > 0) totalKm / totalAmt else null,
            trend = trend(pts, unit, todayEpochDay),
            totalSpentBdt = spent,
            costPerKmBdt = if (spanKm > 0 && spent > 0) {
                // exclude the first fill: its fuel was burned before tracking began
                val firstPrice = sorted.first().priceBdt ?: 0
                (spent - firstPrice).toDouble() / spanKm
            } else null,
            avgKmPerDay = kmPerDay,
        )
    }

    /** Compare last 30 days' efficiency with the vehicle's own earlier average. */
    fun trend(points: List<EfficiencyPoint>, unit: EfficiencyUnit, todayEpochDay: Long): EfficiencyTrend? {
        val cutoff = todayEpochDay - RECENT_DAYS
        val recent = points.filter { it.epochDay > cutoff }
        val base = points.filter { it.epochDay <= cutoff }
        if (recent.isEmpty() || base.size < MIN_BASELINE_POINTS) return null
        val b = base.sumOf { it.km } / base.sumOf { it.amount }
        val r = recent.sumOf { it.km } / recent.sumOf { it.amount }
        return EfficiencyTrend(unit, b, r, (b - r) / b * 100.0)
    }

    fun alert(trend: EfficiencyTrend?, electric: Boolean): FuelAlert? {
        if (trend == null || trend.dropPercent < ALERT_DROP_PERCENT) return null
        val d = trend.dropPercent.roundToInt()
        val causes = if (electric) listOf(
            L10n("Ageing or sulphated battery cells", "ব্যাটারির সেল পুরনো বা সালফেটেড"),
            L10n("Low electrolyte (water) in lead-acid batteries", "লেড-অ্যাসিড ব্যাটারিতে পানি কম"),
            L10n("Loose or corroded terminals", "টার্মিনাল ঢিলা বা মরিচা ধরা"),
            L10n("Faulty charger not charging fully", "চার্জার ঠিকমতো ফুল চার্জ দিচ্ছে না"),
            L10n("Overloading / low tyre pressure", "অতিরিক্ত লোড / টায়ারে হাওয়া কম"),
        ) else listOf(
            L10n("Low tyre pressure", "টায়ারে হাওয়া কম"),
            L10n("Dirty air filter", "এয়ার ফিল্টার ময়লা"),
            L10n("Dragging brakes", "ব্রেক লেগে থাকছে"),
            L10n("Worn spark plug / ignition problem", "স্পার্ক প্লাগ ক্ষয় / ইগনিশন সমস্যা"),
            L10n("Fuel quality or leak; for CNG, kit tuning or leak", "জ্বালানির মান বা লিক; সিএনজিতে কিট টিউনিং বা লিক"),
        )
        return FuelAlert(
            L10n("Mileage dropped $d% this month", "এই মাসে মাইলেজ $d% কমেছে"),
            causes, d,
        )
    }
}
