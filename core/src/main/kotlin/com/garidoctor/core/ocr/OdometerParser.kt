package com.garidoctor.core.ocr

import com.garidoctor.core.health.Bn

data class OdometerCandidate(val km: Int, val confidence: Double, val source: String)

/**
 * Picks the odometer reading out of raw on-device OCR text (ML Kit). Meters show a lot of
 * other numbers (trip meter, clock, fuel bars, speed scale), so we score candidates.
 */
object OdometerParser {
    private val number = Regex("""[\dOoIlSB][\dOoIlSB ,.]*[\dOoIlSB]|\d""")
    private fun fixOcr(s: String) = s.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1').replace('S', '5').replace('B', '8')
    private val speedScale = setOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 180, 200, 220, 240)

    fun candidates(ocrText: String, previousKm: Int?): List<OdometerCandidate> {
        val text = Bn.toAscii(ocrText)
        val out = mutableListOf<OdometerCandidate>()
        text.lines().forEach { line ->
            val lower = line.lowercase()
            val isTrip = "trip" in lower || "trp" in lower
            val hasOdo = "odo" in lower || "km" in lower || "total" in lower
            number.findAll(line).forEach { m ->
                val raw = m.value.trim()
                // Mostly letters ("SOB", "BOIL") are words, not numbers.
                if (raw.count { it.isDigit() } * 2 < raw.count { it.isLetterOrDigit() }) return@forEach
                if (line.getOrNull(m.range.last + 1) == ':' || line.getOrNull(m.range.first - 1) == ':') return@forEach
                // Drop decimals: "12345.6" (tenths digit) → 12345
                val intPart = fixOcr(raw).replace(" ", "").replace(",", "").substringBefore('.')
                val v = intPart.toIntOrNull() ?: return@forEach
                var c = 0.3
                if (intPart.length in 4..7) c += 0.3
                if (intPart.length < 3) c -= 0.4
                if (v in speedScale) c -= 0.3
                if (hasOdo) c += 0.15
                if (isTrip) c -= 0.4
                if (previousKm != null) {
                    val diff = v - previousKm
                    c += when {
                        diff < 0 -> -0.5
                        diff <= 5_000 -> 0.35
                        diff <= 20_000 -> 0.1
                        else -> -0.2
                    }
                }
                out += OdometerCandidate(v, c.coerceIn(0.0, 1.0), raw)
            }
        }
        return out.groupBy { it.km }.map { (_, list) -> list.maxBy { it.confidence } }.sortedByDescending { it.confidence }
    }

    fun best(ocrText: String, previousKm: Int?): OdometerCandidate? = candidates(ocrText, previousKm).firstOrNull { it.confidence >= 0.4 }
}
