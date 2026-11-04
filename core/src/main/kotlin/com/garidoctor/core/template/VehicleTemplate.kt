package com.garidoctor.core.template

import com.garidoctor.core.model.DocumentType
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.L10n
import kotlinx.serialization.Serializable

/** Health-score category a part belongs to (used for grouping and for icons in the UI). */
@Serializable
enum class PartCategory { ENGINE_OIL, BRAKES, TYRES, BATTERY, DRIVE, FILTER, IGNITION, COOLING, ELECTRICAL, TRACTION_BATTERY, CNG_KIT, BODY, OTHER }

/**
 * A maintainable part. Intervals are "whichever comes first". [weight] is the maximum
 * number of points this part can take off the health score.
 */
@Serializable
data class PartTemplate(
    val id: String,
    val name: L10n,
    val category: PartCategory,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val weight: Int,
    /** Id of a guided visual check for this part, if any. */
    val checkId: String? = null,
    /** Rough service cost in BDT shown next to reminders. */
    val costMinBdt: Int? = null,
    val costMaxBdt: Int? = null,
)

/** Per-model override of a part's interval (e.g. Yamaha FZ oil every 2,000 km). */
@Serializable
data class IntervalOverride(val intervalKm: Int? = null, val intervalMonths: Int? = null)

@Serializable
data class ModelPreset(
    val id: String,
    val brand: String,
    val model: String,
    val energyType: EnergyType? = null,
    val overrides: Map<String, IntervalOverride> = emptyMap(),
)

@Serializable
data class GuidedCheck(
    val id: String,
    val title: L10n,
    val steps: List<L10n>,
    /** Reference states the user compares against; each maps to pass or fail. */
    val references: List<CheckReference>,
)

@Serializable
data class CheckReference(val label: L10n, val description: L10n, val passes: Boolean, val image: String? = null)

/**
 * A vehicle-type template. Adding a vehicle type means adding one of these JSON files
 * (bundled under resources/templates, overridable via Firebase Remote Config) — no code.
 */
@Serializable
data class VehicleTemplate(
    val id: String,
    val version: Int = 1,
    val name: L10n,
    val defaultEnergy: EnergyType,
    val parts: List<PartTemplate>,
    val documents: List<DocumentType>,
    val models: List<ModelPreset> = emptyList(),
    val checks: List<GuidedCheck> = emptyList(),
    val symptomTreeId: String? = null,
    val supportsObd: Boolean = false,
    val fleetFriendly: Boolean = false,
) {
    fun part(id: String): PartTemplate? = parts.firstOrNull { it.id == id }
    fun model(id: String?): ModelPreset? = id?.let { mid -> models.firstOrNull { it.id == mid } }
    fun check(id: String?): GuidedCheck? = id?.let { cid -> checks.firstOrNull { it.id == cid } }

    /** Effective interval for a part after applying model overrides. */
    fun effectivePart(partId: String, modelId: String?): PartTemplate? {
        val base = part(partId) ?: return null
        val o = model(modelId)?.overrides?.get(partId) ?: return base
        return base.copy(
            intervalKm = o.intervalKm ?: base.intervalKm,
            intervalMonths = o.intervalMonths ?: base.intervalMonths,
        )
    }

    fun effectiveParts(modelId: String?): List<PartTemplate> = parts.map { effectivePart(it.id, modelId)!! }
}
