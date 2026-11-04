package com.garidoctor.core.passport

import com.garidoctor.core.fuel.FuelStats
import com.garidoctor.core.health.HealthReport
import com.garidoctor.core.model.ServiceEvent
import com.garidoctor.core.model.Vehicle
import com.garidoctor.core.model.VehicleDocument
import java.security.MessageDigest

/**
 * Everything printed in the Vehicle Passport PDF. [fingerprint] is a SHA-256 over the
 * canonical content, embedded in the QR code so a buyer can see the report wasn't edited
 * (scan → app recomputes from the shared summary). A "garage-verified" badge can later sign it.
 */
data class VehiclePassport(
    val vehicle: Vehicle,
    val templateName: String,
    val modelName: String?,
    val health: HealthReport,
    val services: List<ServiceEvent>,
    val documents: List<VehicleDocument>,
    val fuel: FuelStats?,
    val generatedEpochDay: Long,
    val fingerprint: String,
) {
    /** Compact text payload for the QR code. */
    fun qrPayload(): String = buildString {
        append("GARIDOCTOR1|")
        append(vehicle.registrationNo ?: "-").append('|')
        append(vehicle.odometerKm).append('|')
        append(health.score).append('|')
        append(services.size).append('|')
        append(generatedEpochDay).append('|')
        append(fingerprint.take(16))
    }

    companion object {
        fun build(
            vehicle: Vehicle, templateName: String, modelName: String?, health: HealthReport,
            services: List<ServiceEvent>, documents: List<VehicleDocument>, fuel: FuelStats?, today: Long,
        ): VehiclePassport {
            val sorted = services.sortedWith(compareBy({ it.epochDay }, { it.odometerKm }))
            val canonical = buildString {
                append(vehicle.registrationNo).append(vehicle.templateId).append(vehicle.modelPresetId).append(vehicle.odometerKm)
                append(health.score)
                sorted.forEach { append('|').append(it.partId).append(',').append(it.odometerKm).append(',').append(it.epochDay) }
                append(today)
            }
            return VehiclePassport(vehicle, templateName, modelName, health, sorted, documents, fuel, today, sha256(canonical))
        }

        fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Parse a scanned QR payload. */
        fun parseQr(payload: String): Map<String, String>? {
            val p = payload.split('|')
            if (p.size != 7 || p[0] != "GARIDOCTOR1") return null
            return mapOf("reg" to p[1], "odometer" to p[2], "score" to p[3], "services" to p[4], "day" to p[5], "fp" to p[6])
        }
    }
}
