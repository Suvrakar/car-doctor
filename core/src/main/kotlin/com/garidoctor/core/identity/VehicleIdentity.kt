package com.garidoctor.core.identity

import com.garidoctor.core.health.Bn
import com.garidoctor.core.model.L10n
import com.garidoctor.core.model.Lang

/** The unique references of a vehicle and who drives it. Printed on the passport and put in the ID QR. */
data class VehicleIdentity(
    val nickname: String,
    val vehicleType: String,
    val makeModel: String? = null,
    val registrationNo: String? = null,
    val engineNo: String? = null,
    val chassisNo: String? = null,
    val driverName: String? = null,
    val driverLicenceNo: String? = null,
    val driverPhone: String? = null,
) {
    enum class Field(val label: L10n) {
        REGISTRATION(L10n("Registration no.", "রেজিস্ট্রেশন নম্বর")),
        ENGINE(L10n("Engine no.", "ইঞ্জিন নম্বর")),
        CHASSIS(L10n("Chassis no.", "চেসিস নম্বর")),
        DRIVER(L10n("Driver", "চালক")),
        LICENCE(L10n("Driving licence no.", "ড্রাইভিং লাইসেন্স নম্বর")),
        PHONE(L10n("Phone", "ফোন")),
    }

    fun value(f: Field): String? = when (f) {
        Field.REGISTRATION -> registrationNo; Field.ENGINE -> engineNo; Field.CHASSIS -> chassisNo
        Field.DRIVER -> driverName; Field.LICENCE -> driverLicenceNo; Field.PHONE -> driverPhone
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** The references that identify the vehicle itself (not the driver). */
    val hasVehicleReference: Boolean get() = VEHICLE_FIELDS.any { value(it) != null }

    /**
     * Plain text, so any phone camera or QR app shows it without GariDoctor installed. Blank fields
     * are left out; driver details only when [includeDriver]. Kept short so the QR stays easy to scan.
     */
    fun qrText(lang: Lang, includeDriver: Boolean = true): String = buildString {
        append(HEADER.get(lang)).append('\n')
        append(nickname.trim())
        val kind = listOfNotNull(vehicleType.takeIf { it.isNotBlank() }, makeModel?.takeIf { it.isNotBlank() }).joinToString(" · ")
        if (kind.isNotEmpty()) append(" — ").append(kind)
        val fields = if (includeDriver) Field.entries else VEHICLE_FIELDS
        fields.forEach { f -> value(f)?.let { append('\n').append(f.label.get(lang)).append(": ").append(it) } }
    }

    companion object {
        val HEADER = L10n("Car Doctor AI · Vehicle ID", "কার ডক্টর AI · গাড়ির পরিচয়")
        val VEHICLE_FIELDS = listOf(Field.REGISTRATION, Field.ENGINE, Field.CHASSIS)

        /** "Dhaka Metro-GA 12-3456", "DHAKA METRO GA 123456" and "ঢাকা মেট্রো..." with Bangla digits compare equal. */
        fun normalize(s: String?): String? = s?.let { Bn.toAscii(it) }?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }

        /**
         * Which of [candidate]'s vehicle references are already used by one of [others] (other vehicles
         * in the same garage). Registration, engine and chassis numbers must each be unique.
         */
        fun duplicates(candidate: VehicleIdentity, others: List<VehicleIdentity>): Map<Field, String> {
            val out = LinkedHashMap<Field, String>()
            VEHICLE_FIELDS.forEach { f ->
                val mine = normalize(candidate.value(f)) ?: return@forEach
                others.firstOrNull { normalize(it.value(f)) == mine }?.let { out[f] = it.nickname }
            }
            return out
        }
    }
}
