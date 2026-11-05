package com.garidoctor.core.history

import java.time.LocalDate

enum class HistoryKind { SERVICE, EXPENSE, FUEL, PAPER, ODOMETER, CHECK, PROBLEM, LIVE_CHECK }

/** One line in a vehicle's history: a bill, a fill-up, a paper renewal, a reading... */
data class HistoryEntry(
    val epochDay: Long,
    val kind: HistoryKind,
    val title: String,
    val detail: String? = null,
    val amountBdt: Int? = null,
    val odometerKm: Int? = null,
    /** Row id in its own table, so the UI can open or delete it. */
    val refId: Long? = null,
    val receiptPath: String? = null,
)

data class HistoryMonth(val year: Int, val month: Int, val entries: List<HistoryEntry>) {
    val spentBdt: Int get() = entries.sumOf { it.amountBdt ?: 0 }
}

data class SpendSummary(val totalBdt: Int, val last12MonthsBdt: Int, val serviceBdt: Int, val fuelBdt: Int, val otherBdt: Int, val bills: Int)

object History {
    /** Newest first; same-day entries keep a stable order (by kind, then by odometer, newest first). */
    fun sorted(entries: List<HistoryEntry>): List<HistoryEntry> =
        entries.sortedWith(compareByDescending<HistoryEntry> { it.epochDay }.thenBy { it.kind.ordinal }.thenByDescending { it.odometerKm ?: 0 })

    fun byMonth(entries: List<HistoryEntry>): List<HistoryMonth> =
        sorted(entries).groupBy { LocalDate.ofEpochDay(it.epochDay).let { d -> d.year to d.monthValue } }
            .map { (ym, list) -> HistoryMonth(ym.first, ym.second, list) }

    fun filter(entries: List<HistoryEntry>, kinds: Set<HistoryKind>?): List<HistoryEntry> =
        if (kinds.isNullOrEmpty()) entries else entries.filter { it.kind in kinds }

    fun spend(entries: List<HistoryEntry>, todayEpochDay: Long): SpendSummary {
        val paid = entries.filter { (it.amountBdt ?: 0) > 0 }
        val yearAgo = LocalDate.ofEpochDay(todayEpochDay).minusMonths(12).toEpochDay()
        return SpendSummary(
            totalBdt = paid.sumOf { it.amountBdt!! },
            last12MonthsBdt = paid.filter { it.epochDay > yearAgo }.sumOf { it.amountBdt!! },
            serviceBdt = paid.filter { it.kind == HistoryKind.SERVICE }.sumOf { it.amountBdt!! },
            fuelBdt = paid.filter { it.kind == HistoryKind.FUEL }.sumOf { it.amountBdt!! },
            otherBdt = paid.filter { it.kind != HistoryKind.SERVICE && it.kind != HistoryKind.FUEL }.sumOf { it.amountBdt!! },
            bills = paid.size,
        )
    }
}
