package com.garidoctor.core.health

import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.fuel.EfficiencyTrend
import com.garidoctor.core.model.CheckResult
import com.garidoctor.core.model.L10n
import com.garidoctor.core.model.Lang
import com.garidoctor.core.model.Translations
import com.garidoctor.core.model.ReportedSymptom
import com.garidoctor.core.model.VehicleDocument
import com.garidoctor.core.model.VoltageReading
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.schedule.PartStatus
import com.garidoctor.core.template.PartCategory
import kotlin.math.min
import kotlin.math.roundToInt

enum class DeductionKind { SERVICE_OVERDUE, SERVICE_DUE_SOON, FAILED_CHECK, SYMPTOM, LOW_VOLTAGE, FAULT_CODE, EFFICIENCY_DROP, DOCUMENT, LIVE_DATA }

data class Deduction(val kind: DeductionKind, val points: Int, val reason: L10n, val partId: String? = null)

enum class HealthBand(val label: L10n) {
    EXCELLENT(L10n("Excellent", "চমৎকার")),
    GOOD(L10n("Good", "ভালো")),
    FAIR(L10n("Needs attention", "মনোযোগ দরকার")),
    POOR(L10n("Poor", "খারাপ")),
    CRITICAL(L10n("Critical", "জরুরি"));

    companion object {
        fun of(score: Int) = when {
            score >= 90 -> EXCELLENT
            score >= 75 -> GOOD
            score >= 55 -> FAIR
            score >= 35 -> POOR
            else -> CRITICAL
        }
    }
}

data class HealthReport(val score: Int, val band: HealthBand, val deductions: List<Deduction>) {
    /** "Your Pulsar is at 72/100. Engine oil is 400 km overdue (−15), ..." */
    fun summary(vehicleName: String, lang: Lang, max: Int = 3): String {
        val top = deductions.take(max).joinToString(", ") { "${it.reason.get(lang)} (−${it.points})" }
        return if (lang == Lang.BN) {
            val head = "আপনার $vehicleName এর স্কোর ${Bn.digits(score)}/১০০।"
            if (top.isEmpty()) "$head সবকিছু ঠিক আছে!" else "$head ${Bn.digits(top)}।"
        } else {
            val head = Translations.format(lang, "Your {0} is at {1}/100.", vehicleName, score)
            if (top.isEmpty()) "$head ${Translations.tr(lang, "Everything looks good!")}" else "$head $top."
        }
    }
}

/** Everything the score is computed from. */
data class HealthInputs(
    val parts: List<PartStatus>,
    val documents: List<VehicleDocument> = emptyList(),
    val checks: List<CheckResult> = emptyList(),
    val symptoms: List<ReportedSymptom> = emptyList(),
    val voltage: VoltageReading? = null,
    val faultCodes: List<DiagnosticTroubleCode> = emptyList(),
    val efficiency: EfficiencyTrend? = null,
    val todayEpochDay: Long,
    /** Latest saved live-data check and the day it was made. */
    val liveCheck: com.garidoctor.core.obd.LiveSummary? = null,
    val liveCheckEpochDay: Long? = null,
)

/**
 * Turns part condition into a 0..100 score, like a medical checkup.
 *
 * Rules (all tunable via constants):
 *  - Overdue part: 40% of its weight at the due point, rising linearly to 100% at 50% past due.
 *  - Due soon (85–100% of interval): up to 20% of weight.
 *  - Failed guided check on a part within the last 90 days: full weight (unless serviced since).
 *  - Active reported symptom on a part: 60% of weight.
 *  - Low battery voltage: up to 10.
 *  - Active fault codes: 10 each, capped at 25.
 *  - Efficiency drop vs own average: 15%+ → 6, 25%+ → 10.
 *  - Papers: expired 10, ≤7 days 5, ≤15 days 3, ≤30 days 1. Capped at 20 total.
 *  - Latest live-data check (last 30 days): red finding 8, amber 3, capped at 20.
 * A part only counts once: the worst of its deductions is kept.
 */
object HealthScoreEngine {
    const val CHECK_VALID_DAYS = 90L
    const val DOC_CAP = 20
    const val FAULT_CODE_EACH = 10
    const val FAULT_CODE_CAP = 25
    const val LIVE_VALID_DAYS = 30L
    const val LIVE_CAP = 20

    fun compute(input: HealthInputs): HealthReport {
        val perPart = mutableMapOf<String, Deduction>()
        fun keepWorst(d: Deduction) {
            val id = d.partId ?: return
            val cur = perPart[id]
            if (cur == null || d.points > cur.points) perPart[id] = d
        }

        input.parts.forEach { s -> serviceDeduction(s)?.let(::keepWorst) }

        input.checks.filter { !it.passed && input.todayEpochDay - it.epochDay <= CHECK_VALID_DAYS }
            .forEach { c ->
                val s = input.parts.firstOrNull { it.part.id == c.partId } ?: return@forEach
                val servicedAfter = (s.lastService?.epochDay ?: Long.MIN_VALUE) > c.epochDay
                if (!servicedAfter) keepWorst(
                    Deduction(DeductionKind.FAILED_CHECK, s.part.weight,
                        L10n("${s.part.name.en} failed the visual check", "${s.part.name.bn} ভিজ্যুয়াল চেকে ফেল করেছে"), s.part.id)
                )
            }

        input.symptoms.filter { !it.resolved }.forEach { sym ->
            val s = input.parts.firstOrNull { it.part.id == sym.partId } ?: return@forEach
            val servicedAfter = (s.lastService?.epochDay ?: Long.MIN_VALUE) > sym.epochDay
            if (!servicedAfter) keepWorst(
                Deduction(DeductionKind.SYMPTOM, (s.part.weight * 0.6).roundToInt().coerceAtLeast(1),
                    L10n("${s.part.name.en}: symptom reported", "${s.part.name.bn}: সমস্যার লক্ষণ জানানো হয়েছে"), s.part.id)
            )
        }

        input.voltage?.let { v -> voltageDeduction(v, input.parts)?.let(::keepWorst) }

        val others = mutableListOf<Deduction>()
        if (input.faultCodes.isNotEmpty()) {
            val pts = min(FAULT_CODE_CAP, input.faultCodes.size * FAULT_CODE_EACH)
            val codes = input.faultCodes.joinToString { it.code }
            others += Deduction(DeductionKind.FAULT_CODE, pts, L10n("Check-engine codes active: $codes", "চেক-ইঞ্জিন কোড সক্রিয়: $codes"))
        }

        input.efficiency?.let { t ->
            val drop = t.dropPercent
            val pts = when { drop >= 25.0 -> 10; drop >= 15.0 -> 6; else -> 0 }
            if (pts > 0) {
                val d = drop.roundToInt()
                others += Deduction(DeductionKind.EFFICIENCY_DROP, pts, L10n("Mileage dropped $d%", "মাইলেজ $d% কমেছে"))
            }
        }

        val live = input.liveCheck
        val liveDay = input.liveCheckEpochDay
        if (live != null && liveDay != null && input.todayEpochDay - liveDay <= LIVE_VALID_DAYS) {
            var livePts = 0
            live.flags.forEach { f ->
                val pts = minOf(if (f.level == com.garidoctor.core.obd.LiveLevel.BAD) 8 else 3, LIVE_CAP - livePts)
                if (pts > 0) { livePts += pts; others += Deduction(DeductionKind.LIVE_DATA, pts, f.message) }
            }
        }

        var docPts = 0
        input.documents.map { DocumentExpiry.status(it, input.todayEpochDay) }.sortedBy { it.daysLeft }.forEach { ds ->
            val pts = when (ds.state) {
                ExpiryState.EXPIRED -> 10
                ExpiryState.EXPIRING_7 -> 5
                ExpiryState.EXPIRING_15 -> 3
                ExpiryState.EXPIRING_30 -> 1
                ExpiryState.VALID -> 0
            }
            val allowed = min(pts, DOC_CAP - docPts)
            if (allowed > 0) {
                docPts += allowed
                val name = ds.doc.type.label
                val reason = if (ds.daysLeft < 0) L10n("${name.en} expired ${-ds.daysLeft} days ago", "${name.bn} ${Bn.digits(-ds.daysLeft)} দিন আগে মেয়াদ শেষ")
                else L10n("${name.en} expires in ${ds.daysLeft} days", "${name.bn} এর মেয়াদ ${Bn.digits(ds.daysLeft)} দিনে শেষ")
                others += Deduction(DeductionKind.DOCUMENT, allowed, reason)
            }
        }

        val all = (perPart.values + others).filter { it.points > 0 }.sortedByDescending { it.points }
        val score = (100 - all.sumOf { it.points }).coerceIn(0, 100)
        return HealthReport(score, HealthBand.of(score), all)
    }

    internal fun serviceDeduction(s: PartStatus): Deduction? {
        val w = s.part.weight
        val name = s.part.name
        return when (s.state) {
            DueState.OVERDUE -> {
                val past = ((s.ratio - 1.0) / 0.5).coerceIn(0.0, 1.0)
                val pts = (w * (0.4 + 0.6 * past)).roundToInt().coerceAtLeast(1)
                val reason = if (s.drivenBy == "km" && s.kmRemaining != null) {
                    val over = -s.kmRemaining
                    L10n("${name.en} is ${fmt(over)} km overdue", "${name.bn} ${Bn.digits(fmt(over))} কিমি পেরিয়ে গেছে")
                } else {
                    val over = -(s.daysRemaining ?: 0)
                    L10n("${name.en} is $over days overdue", "${name.bn} ${Bn.digits(over)} দিন পেরিয়ে গেছে")
                }
                Deduction(DeductionKind.SERVICE_OVERDUE, pts, reason, s.part.id)
            }
            DueState.DUE_SOON -> {
                val frac = ((s.ratio - 0.85) / 0.15).coerceIn(0.0, 1.0)
                val pts = (w * 0.2 * frac).roundToInt()
                if (pts == 0) null else {
                    val reason = if (s.drivenBy == "km" && s.kmRemaining != null)
                        L10n("${name.en} due in ${fmt(s.kmRemaining)} km", "${name.bn} ${Bn.digits(fmt(s.kmRemaining))} কিমির মধ্যে করতে হবে")
                    else L10n("${name.en} due in ${s.daysRemaining} days", "${name.bn} ${Bn.digits(s.daysRemaining ?: 0)} দিনের মধ্যে করতে হবে")
                    Deduction(DeductionKind.SERVICE_DUE_SOON, pts, reason, s.part.id)
                }
            }
            DueState.OK -> null
        }
    }

    /** Resting voltage → state of charge. 12.6V+ healthy for a 12V lead-acid; below 12.2V weak. */
    internal fun voltageDeduction(v: VoltageReading, parts: List<PartStatus>): Deduction? {
        val battery = parts.firstOrNull { it.part.category == PartCategory.BATTERY || it.part.category == PartCategory.TRACTION_BATTERY }
        val perCell = v.volts / v.nominalVolts * 12.0 // normalise to a 12V scale
        val pts = when {
            perCell < 11.9 -> 10
            perCell < 12.2 -> 6
            perCell < 12.4 -> 3
            else -> 0
        }
        if (pts == 0) return null
        return Deduction(DeductionKind.LOW_VOLTAGE, pts,
            L10n("Battery voltage low (${"%.1f".format(v.volts)} V)", "ব্যাটারি ভোল্টেজ কম (${Bn.digits("%.1f".format(v.volts))} V)"),
            battery?.part?.id ?: "battery")
    }

    private fun fmt(n: Int) = "%,d".format(n)
}

/** Bangla numeral helpers. */
object Bn {
    private const val BN = "০১২৩৪৫৬৭৮৯"
    fun digits(n: Number): String = digits(n.toString())
    fun digits(s: String): String = buildString { s.forEach { c -> append(if (c in '0'..'9') BN[c - '0'] else c) } }
    /** Convert Bangla digits back to ASCII (for OCR and user input). */
    fun toAscii(s: String): String = buildString { s.forEach { c -> val i = BN.indexOf(c); append(if (i >= 0) ('0' + i) else c) } }
}
