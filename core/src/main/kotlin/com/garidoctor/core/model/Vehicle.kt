package com.garidoctor.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EnergyType { PETROL, OCTANE, DIESEL, CNG, PETROL_CNG, ELECTRIC_LEAD_ACID, ELECTRIC_LITHIUM }

/** Unit the efficiency tracker reports in, derived from [EnergyType]. */
enum class EfficiencyUnit(val label: L10n) {
    KM_PER_LITRE(L10n("km/L", "কিমি/লিটার")),
    KM_PER_M3(L10n("km/m³", "কিমি/ঘনমিটার")),
    KM_PER_CHARGE(L10n("km/charge", "কিমি/চার্জ"));

    companion object {
        fun of(e: EnergyType): EfficiencyUnit = when (e) {
            EnergyType.CNG -> KM_PER_M3
            EnergyType.ELECTRIC_LEAD_ACID, EnergyType.ELECTRIC_LITHIUM -> KM_PER_CHARGE
            else -> KM_PER_LITRE
        }
    }
}

/** A vehicle as the engines see it. Storage-agnostic (the app maps Room entities to this). */
data class Vehicle(
    val id: Long,
    val templateId: String,
    val modelPresetId: String?,
    val nickname: String,
    val registrationNo: String?,
    val odometerKm: Int,
    val energyType: EnergyType,
    val createdAtEpochDay: Long,
)

/** Last time a part was serviced (oil changed, pads replaced, chain adjusted...). */
data class ServiceEvent(
    val partId: String,
    val odometerKm: Int,
    val epochDay: Long,
    val costBdt: Int? = null,
    val note: String? = null,
)

/** Result of a guided visual check (tread, chain slack, pad thickness...). */
data class CheckResult(val partId: String, val passed: Boolean, val epochDay: Long)

/** A symptom the user reported that implicates a part ("spongy brake" -> brakes). */
data class ReportedSymptom(val partId: String, val symptomId: String, val epochDay: Long, val resolved: Boolean = false)

/** Battery voltage reading (12V starter battery or traction pack). */
data class VoltageReading(val volts: Double, val nominalVolts: Double, val epochDay: Long)
