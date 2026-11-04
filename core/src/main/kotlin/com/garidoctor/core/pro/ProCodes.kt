package com.garidoctor.core.pro

import java.security.MessageDigest

/** Features gated behind the one-time Pro unlock. */
enum class ProFeature { MULTIPLE_VEHICLES, PASSPORT_PDF, LIVE_OBD_GRAPHS, NO_ADS, FLEET_MODE }

object ProGate {
    const val FREE_VEHICLE_LIMIT = 1
    fun allowed(feature: ProFeature, isPro: Boolean, vehicleCount: Int = 0): Boolean = isPro || when (feature) {
        ProFeature.MULTIPLE_VEHICLES -> vehicleCount < FREE_VEHICLE_LIMIT
        else -> false
    }
}

/**
 * Offline-verifiable promo codes (for Facebook-group admins, dongle bundles, mechanic referrals).
 * Format: GD-XXXX-XXXX-CCCC where CCCC is a truncated hash of the body with a build-time salt.
 * This deters casual guessing; it is not cryptographic DRM. Real purchases go through Play Billing.
 */
object ProCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun checksum(body: String, salt: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$salt:$body".toByteArray())
        return (0 until 4).map { ALPHABET[(d[it].toInt() and 0xFF) % ALPHABET.length] }.joinToString("")
    }

    fun generate(serial: Int, salt: String): String {
        var n = serial.toLong() * 2654435761L % 1_099_511_627_776L // spread serials
        val body = buildString { repeat(8) { append(ALPHABET[(n % ALPHABET.length).toInt()]); n /= ALPHABET.length } }
        val b = body.chunked(4).joinToString("-")
        return "GD-$b-${checksum(b, salt)}"
    }

    fun isValid(code: String, salt: String): Boolean {
        val c = code.trim().uppercase().replace(" ", "")
        val m = Regex("GD-([A-Z0-9]{4}-[A-Z0-9]{4})-([A-Z0-9]{4})").matchEntire(c) ?: return false
        return checksum(m.groupValues[1], salt) == m.groupValues[2]
    }
}
