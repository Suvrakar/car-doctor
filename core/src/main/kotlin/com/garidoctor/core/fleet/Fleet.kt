package com.garidoctor.core.fleet

import com.garidoctor.core.docs.DocumentStatus
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.health.HealthReport
import com.garidoctor.core.model.Vehicle
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.schedule.PartStatus

data class FleetVehicleRow(
    val vehicle: Vehicle,
    val driverName: String?,
    val health: HealthReport,
    val overdue: List<PartStatus>,
    val expiringDocs: List<DocumentStatus>,
)

data class FleetSummary(
    val rows: List<FleetVehicleRow>,
    val averageScore: Int,
    val overdueServices: Int,
    val expiredPapers: Int,
    val papersExpiringIn30: Int,
    val needAttention: Int,
)

/** One-screen overview for CNG owners, easy-bike garages, delivery fleets. */
object Fleet {
    fun summarize(rows: List<FleetVehicleRow>): FleetSummary {
        val sorted = rows.sortedBy { it.health.score }
        return FleetSummary(
            rows = sorted,
            averageScore = if (rows.isEmpty()) 0 else rows.sumOf { it.health.score } / rows.size,
            overdueServices = rows.sumOf { r -> r.overdue.count { it.state == DueState.OVERDUE } },
            expiredPapers = rows.sumOf { r -> r.expiringDocs.count { it.state == ExpiryState.EXPIRED } },
            papersExpiringIn30 = rows.sumOf { r -> r.expiringDocs.count { it.state != ExpiryState.EXPIRED && it.state != ExpiryState.VALID } },
            needAttention = rows.count { it.health.score < 75 },
        )
    }
}
