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

    /** Decode a mode-03 (stored DTC) response such as "43 01 33 00 00 00 00". */
    fun decodeDtcs(raw: String, dictionary: Map<String, DtcInfo> = emptyMap()): List<DiagnosticTroubleCode> {
        if (raw.contains("NO DATA", ignoreCase = true)) return emptyList()
        val b = bytes(raw)
        val start = b.indexOf(0x43)
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
            else -> null
        }
    }

    val LIVE_PIDS = listOf("010C", "010D", "0105", "0104", "0111", "010F", "0142")

    fun loadDictionary(jsonText: String): Map<String, DtcInfo> =
        Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(DtcInfo.serializer()), jsonText).associateBy { it.code }
}
