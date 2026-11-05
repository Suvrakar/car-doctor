package com.garidoctor.core.obd

import com.garidoctor.core.model.L10n
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class DtcInfo(val code: String, val title: L10n, val severity: Int, val advice: L10n)

data class DiagnosticTroubleCode(val code: String, val info: DtcInfo?) {
    /** Offline description; generic if the code isn't in the dictionary. */
    fun describe(): L10n = info?.title ?: L10n("Unknown fault (${code}) — ask a mechanic to scan", "অজানা ত্রুটি (${code}) — মেকানিক দিয়ে স্ক্যান করান")
}

/** Mode 01 PID 01: is the check-engine light on, and how many codes are stored. */
data class MonitorStatus(val milOn: Boolean, val storedCodes: Int)

/** Live data values decoded from ELM327 mode-01 responses. */
data class LiveData(val pid: String, val name: L10n, val value: Double, val unit: String)

/**
 * Pure ELM327 / OBD-II protocol decoding. The Android layer owns the Bluetooth socket and
 * feeds raw response strings here, so all of this is unit-testable.
 */
object ObdDecoder {
    val INIT_COMMANDS = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")

    private fun bytes(raw: String): List<Int> = raw
        .replace(">", "").replace("SEARCHING...", "")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }
        .flatMap { line ->
            val clean = line.substringAfter(':', line).replace(" ", "")
            if (clean.length % 2 != 0 || !clean.all { it.isLetterOrDigit() }) emptyList()
            else clean.chunked(2).mapNotNull { it.toIntOrNull(16) }
        }

    /**
     * Decode a fault-code response: mode 03 stored ("43 01 33 00 00 00 00"), mode 07 pending
     * (0x47) or mode 0A permanent (0x4A), chosen by [responseByte].
     */
    fun decodeDtcs(raw: String, dictionary: Map<String, DtcInfo> = emptyMap(), responseByte: Int = 0x43): List<DiagnosticTroubleCode> {
        if (raw.contains("NO DATA", ignoreCase = true)) return emptyList()
        val b = bytes(raw)
        val start = b.indexOf(responseByte)
        if (start < 0) return emptyList()
        var data = b.drop(start + 1)
        // CAN responses include a count byte after 0x43 when the payload length is odd.
        if (data.size % 2 == 1) data = data.drop(1)
        return data.chunked(2).filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }.map { (a, c) ->
            val letter = "PCBU"[(a shr 6) and 0x3]
            val code = letter + ((a shr 4) and 0x3).toString() + "%01X".format(a and 0xF) + "%02X".format(c)
            DiagnosticTroubleCode(code, dictionary[code])
        }.distinctBy { it.code }
    }

    /** Decode a single mode-01 response ("41 0C 1A F8" → RPM 1726). */
    fun decodeLive(raw: String): LiveData? {
        val b = bytes(raw)
        val i = b.indexOf(0x41)
        if (i < 0 || i + 2 >= b.size + 1) return null
        val pid = b.getOrNull(i + 1) ?: return null
        val a = b.getOrNull(i + 2) ?: return null
        val c = b.getOrNull(i + 3) ?: 0
        return when (pid) {
            0x04 -> LiveData("04", L10n("Engine load", "ইঞ্জিন লোড"), a * 100.0 / 255, "%")
            0x05 -> LiveData("05", L10n("Coolant temp", "কুল্যান্ট তাপমাত্রা"), a - 40.0, "°C")
            0x0C -> LiveData("0C", L10n("Engine RPM", "ইঞ্জিন RPM"), (a * 256 + c) / 4.0, "rpm")
            0x0D -> LiveData("0D", L10n("Speed", "গতি"), a.toDouble(), "km/h")
            0x0F -> LiveData("0F", L10n("Intake air temp", "ইনটেক বাতাসের তাপমাত্রা"), a - 40.0, "°C")
            0x11 -> LiveData("11", L10n("Throttle", "থ্রটল"), a * 100.0 / 255, "%")
            0x42 -> LiveData("42", L10n("Module voltage", "মডিউল ভোল্টেজ"), (a * 256 + c) / 1000.0, "V")
            0x06 -> LiveData("06", L10n("Short-term fuel trim", "স্বল্পমেয়াদি ফুয়েল ট্রিম"), (a - 128) * 100.0 / 128, "%")
            0x07 -> LiveData("07", L10n("Long-term fuel trim", "দীর্ঘমেয়াদি ফুয়েল ট্রিম"), (a - 128) * 100.0 / 128, "%")
            0x0B -> LiveData("0B", L10n("Intake pressure", "ইনটেক চাপ"), a.toDouble(), "kPa")
            0x0E -> LiveData("0E", L10n("Ignition timing", "ইগনিশন টাইমিং"), a / 2.0 - 64, "°")
            0x10 -> LiveData("10", L10n("Air flow (MAF)", "বাতাস প্রবাহ (MAF)"), (a * 256 + c) / 100.0, "g/s")
            0x1F -> LiveData("1F", L10n("Run time", "চলার সময়"), (a * 256 + c).toDouble(), "s")
            0x2F -> LiveData("2F", L10n("Fuel level", "জ্বালানির মাত্রা"), a * 100.0 / 255, "%")
            0x33 -> LiveData("33", L10n("Air pressure", "বায়ুচাপ"), a.toDouble(), "kPa")
            0x46 -> LiveData("46", L10n("Outside temp", "বাইরের তাপমাত্রা"), a - 40.0, "°C")
            0x5C -> LiveData("5C", L10n("Oil temp", "অয়েলের তাপমাত্রা"), a - 40.0, "°C")
            else -> null
        }
    }

    /** Everything [decodeLive] understands, most useful first. Ask only for the ones the car supports. */
    val LIVE_PIDS = listOf("010C", "010D", "0105", "0104", "0111", "0142", "010F", "0106", "0107", "010B", "0110", "010E", "012F", "015C", "0146", "0133", "011F")

    /**
     * Mode 01 PIDs 00 / 20 / 40 answer with a 32-bit mask of the PIDs the car supports
     * ("41 00 BE 3E B8 11"). Returns PID numbers; several ECUs may answer, so masks are merged.
     */
    fun decodeSupported(raw: String): Set<Int> {
        val b = bytes(raw)
        val out = HashSet<Int>()
        var i = 0
        while (i + 5 < b.size) {
            if (b[i] == 0x41 && b[i + 1] % 0x20 == 0) {
                val base = b[i + 1]
                for (k in 0 until 4) for (bit in 0 until 8) if ((b[i + 2 + k] shr (7 - bit)) and 1 == 1) out += base + k * 8 + bit + 1
                i += 6
            } else i++
        }
        return out
    }

    /** The live-data requests worth sending to a car that reported [supported] (all of them if unknown). */
    fun livePidsFor(supported: Set<Int>): List<String> =
        if (supported.isEmpty()) LIVE_PIDS else LIVE_PIDS.filter { it.substring(2).toInt(16) in supported }

    /** Mode 01 PID 01 ("41 01 83 07 65 04"): bit 7 of A is the check-engine light, bits 0–6 the code count. */
    fun decodeStatus(raw: String): MonitorStatus? {
        val b = bytes(raw)
        val i = (0 until b.size - 2).firstOrNull { b[it] == 0x41 && b[it + 1] == 0x01 } ?: return null
        val a = b[i + 2]
        return MonitorStatus(milOn = a and 0x80 != 0, storedCodes = a and 0x7F)
    }

    /**
     * Mode 09 PID 02: the 17-character VIN, which on most cars is also the chassis number.
     * Handles CAN multi-frame ("0: 49 02 01 4D 41 54") and older one-line-per-frame replies
     * ("49 02 01 00 00 00 4D"). Null when the car doesn't report it (common before ~2005).
     */
    fun decodeVin(raw: String): String? {
        if (raw.contains("NO DATA", true) || raw.contains("ERROR", true)) return null
        val lines = raw.replace(">", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val chars = StringBuilder()
        lines.forEach { line ->
            val body = line.substringAfter(':', line).replace(" ", "")
            if (body.length % 2 != 0 || !body.all { it.isLetterOrDigit() }) return@forEach
            var b = body.chunked(2).mapNotNull { it.toIntOrNull(16) }
            val h = b.indexOfFirst { it == 0x49 }
            if (h >= 0 && b.getOrNull(h + 1) == 0x02) b = b.drop(h + 3) // 49 02 <frame or count>
            b.filter { it in 0x30..0x5A }.forEach { chars.append(it.toChar()) }
        }
        val vin = chars.toString()
        return if (vin.length >= 17) vin.takeLast(17).takeIf { it.none { c -> c == 'I' || c == 'O' || c == 'Q' } } else null
    }

    /** ELM327 "ATRV" reads the voltage at the OBD socket directly ("12.6V"); works on any car. */
    fun decodeAdapterVoltage(raw: String): Double? =
        Regex("""(\d{1,2}[.,]\d)\s*V""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 5.0..30.0 }

    /** "ATDP" → "AUTO, ISO 15765-4 (CAN 11/500)"; strip the AUTO prefix for display. */
    fun protocolName(raw: String): String? = raw.replace(">", "").trim().removePrefix("AUTO,").removePrefix("AUTO").trim().takeIf { it.isNotEmpty() && !it.contains('?') }

    /** Display name and unit for a live key: a PID ("05") or "RV" (adapter voltage). */
    fun label(key: String): Pair<L10n, String> =
        if (key == "RV") L10n("Battery", "ব্যাটারি") to "V"
        else decodeLive("41 $key 00 00")?.let { it.name to it.unit } ?: (L10n(key, key) to "")

    fun loadDictionary(jsonText: String): Map<String, DtcInfo> =
        Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(DtcInfo.serializer()), jsonText).associateBy { it.code }
}
