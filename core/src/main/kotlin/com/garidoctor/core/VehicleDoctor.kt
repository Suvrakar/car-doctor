package com.garidoctor.core

import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.fuel.FuelEntry
import com.garidoctor.core.fuel.FuelStats
import com.garidoctor.core.health.HealthInputs
import com.garidoctor.core.health.HealthReport
import com.garidoctor.core.health.HealthScoreEngine
import com.garidoctor.core.model.CheckResult
import com.garidoctor.core.model.EfficiencyUnit
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.ReportedSymptom
import com.garidoctor.core.model.ServiceEvent
import com.garidoctor.core.model.Vehicle
import com.garidoctor.core.model.VehicleDocument
import com.garidoctor.core.model.VoltageReading
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.schedule.MaintenanceScheduler
import com.garidoctor.core.schedule.PartStatus
import com.garidoctor.core.template.VehicleTemplate

/** All records for one vehicle, as loaded from the local database. */
data class VehicleRecords(
    val vehicle: Vehicle,
    val baselineKm: Int,
    val services: List<ServiceEvent> = emptyList(),
    val documents: List<VehicleDocument> = emptyList(),
    val fuel: List<FuelEntry> = emptyList(),
    val checks: List<CheckResult> = emptyList(),
    val symptoms: List<ReportedSymptom> = emptyList(),
    val voltage: VoltageReading? = null,
    val faultCodes: List<DiagnosticTroubleCode> = emptyList(),
)

data class Checkup(val parts: List<PartStatus>, val fuel: FuelStats, val health: HealthReport)

/** Facade: one call from records + template to a full checkup. */
object VehicleDoctor {
    fun checkup(t: VehicleTemplate, r: VehicleRecords, today: Long): Checkup {
        val parts = MaintenanceScheduler.statuses(
            t.effectiveParts(r.vehicle.modelPresetId), r.services, r.vehicle.odometerKm, today, r.baselineKm, r.vehicle.createdAtEpochDay,
        )
        val fuel = FuelAnalyzer.stats(r.fuel, EfficiencyUnit.of(r.vehicle.energyType), today)
        val health = HealthScoreEngine.compute(
            HealthInputs(parts, r.documents, r.checks, r.symptoms, r.voltage, r.faultCodes, fuel.trend, today),
        )
        return Checkup(parts, fuel, health)
    }

    fun isElectric(e: EnergyType) = e == EnergyType.ELECTRIC_LEAD_ACID || e == EnergyType.ELECTRIC_LITHIUM
}
