package com.garidoctor.core.docs

import com.garidoctor.core.model.VehicleDocument

enum class ExpiryState { VALID, EXPIRING_30, EXPIRING_15, EXPIRING_7, EXPIRED }

data class DocumentStatus(val doc: VehicleDocument, val daysLeft: Long, val state: ExpiryState)

object DocumentExpiry {
    /** Days before expiry on which a reminder notification fires. */
    val REMINDER_DAYS = setOf(30L, 15L, 7L, 3L, 1L, 0L)

    fun status(doc: VehicleDocument, todayEpochDay: Long): DocumentStatus {
        val left = doc.expiryEpochDay - todayEpochDay
        val state = when {
            left < 0 -> ExpiryState.EXPIRED
            left <= 7 -> ExpiryState.EXPIRING_7
            left <= 15 -> ExpiryState.EXPIRING_15
            left <= 30 -> ExpiryState.EXPIRING_30
            else -> ExpiryState.VALID
        }
        return DocumentStatus(doc, left, state)
    }

    /** True if today is a reminder day for this doc (or it is expired: remind weekly). */
    fun shouldRemind(doc: VehicleDocument, todayEpochDay: Long): Boolean {
        val left = doc.expiryEpochDay - todayEpochDay
        return left in REMINDER_DAYS || (left < 0 && (-left) % 7 == 0L)
    }
}
