package com.garidoctor.core.schedule

import com.garidoctor.core.model.ServiceEvent
import com.garidoctor.core.template.PartTemplate
import kotlin.math.max
import kotlin.math.roundToInt

enum class DueState { OK, DUE_SOON, OVERDUE }

/**
 * Where a part stands against its "km or months, whichever comes first" interval.
 * [ratio] is how much of the interval is used up: 1.0 = exactly due, 1.2 = 20% past due.
 */
data class PartStatus(
    val part: PartTemplate,
    val lastService: ServiceEvent?,
    val kmSince: Int,
    val daysSince: Long,
    val kmRemaining: Int?,
    val daysRemaining: Long?,
    val ratio: Double,
    val state: DueState,
    /** Which limit triggers first: "km" or "time". */
    val drivenBy: String,
)

object MaintenanceScheduler {
    const val DAYS_PER_MONTH = 30.44
    /** Fraction of the interval after which a part is "due soon". */
    const val DUE_SOON_RATIO = 0.85

    /**
     * @param baselineKm odometer when the vehicle was added (used when a part was never serviced)
     * @param baselineEpochDay day the vehicle was added
     */
    fun status(
        part: PartTemplate,
        lastService: ServiceEvent?,
        currentKm: Int,
        todayEpochDay: Long,
        baselineKm: Int,
        baselineEpochDay: Long,
    ): PartStatus {
        val fromKm = lastService?.odometerKm ?: baselineKm
        val fromDay = lastService?.epochDay ?: baselineEpochDay
        val kmSince = max(0, currentKm - fromKm)
        val daysSince = max(0L, todayEpochDay - fromDay)

        val kmRatio = part.intervalKm?.let { kmSince.toDouble() / it } ?: 0.0
        val intervalDays = part.intervalMonths?.let { (it * DAYS_PER_MONTH).roundToInt().toLong() }
        val timeRatio = intervalDays?.let { daysSince.toDouble() / it } ?: 0.0
        val ratio = max(kmRatio, timeRatio)
        val state = when {
            ratio >= 1.0 -> DueState.OVERDUE
            ratio >= DUE_SOON_RATIO -> DueState.DUE_SOON
            else -> DueState.OK
        }
        return PartStatus(
            part = part,
            lastService = lastService,
            kmSince = kmSince,
            daysSince = daysSince,
            kmRemaining = part.intervalKm?.let { it - kmSince },
            daysRemaining = intervalDays?.let { it - daysSince },
            ratio = ratio,
            state = state,
            drivenBy = if (kmRatio >= timeRatio) "km" else "time",
        )
    }

    fun statuses(
        parts: List<PartTemplate>,
        services: List<ServiceEvent>,
        currentKm: Int,
        todayEpochDay: Long,
        baselineKm: Int,
        baselineEpochDay: Long,
    ): List<PartStatus> {
        val latest = services.groupBy { it.partId }.mapValues { (_, v) -> v.maxWith(compareBy({ it.epochDay }, { it.odometerKm })) }
        return parts.map { status(it, latest[it.id], currentKm, todayEpochDay, baselineKm, baselineEpochDay) }
            .sortedByDescending { it.ratio }
    }

    /**
     * Estimate the day a part becomes due, using the vehicle's average daily km.
     * Returns null if neither limit can be projected.
     */
    fun projectedDueEpochDay(s: PartStatus, todayEpochDay: Long, avgKmPerDay: Double): Long? {
        val byTime = s.daysRemaining?.let { todayEpochDay + it }
        val byKm = if (avgKmPerDay > 0.0) s.kmRemaining?.let { todayEpochDay + (it / avgKmPerDay).roundToInt() } else null
        return listOfNotNull(byTime, byKm).minOrNull()
    }
}
