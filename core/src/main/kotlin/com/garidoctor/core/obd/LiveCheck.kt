package com.garidoctor.core.obd

import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.L10n
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class LiveLevel { OK, WARN, BAD }

/** Which rule set applies. CNG engines skip fuel-trim checks: gas kits upset the petrol trims. */
enum class EngineProfile {
    PETROL, DIESEL, GAS;
    companion object {
        fun of(e: EnergyType) = when (e) {
            EnergyType.DIESEL -> DIESEL
            EnergyType.CNG, EnergyType.PETROL_CNG -> GAS
            else -> PETROL
        }
    }
}

/** What the engine is doing right now; rules depend on it (a cold engine idles high, a running one charges). */
data class LiveContext(val running: Boolean, val warm: Boolean, val idle: Boolean, val runSeconds: Long = 0) {
    companion object {
        /** [values] are keyed by PID hex ("0C" RPM, "0D" speed, "05" coolant, "11" throttle); [volts] from ATRV. */
        fun of(values: Map<String, Double>, volts: Double?, runSeconds: Long = 0): LiveContext {
            val rpm = values["0C"]
            val running = rpm?.let { it > 350 } ?: ((volts ?: 0.0) > 13.3)
            val warm = (values["05"] ?: 0.0) >= 80
            val idle = running && (values["0D"] ?: 0.0) < 1 && (rpm ?: 0.0) < 1300 && (values["11"] ?: 0.0) < 25
            return LiveContext(running, warm, idle, runSeconds)
        }
    }
}

/** One reading judged against its safe range. [message] says what to do when it isn't OK. */
data class LiveVerdict(val key: String, val value: Double, val level: LiveLevel, val message: L10n? = null, val range: String? = null)

/**
 * Safe ranges for live data. Numbers are for a typical 12 V petrol car (Toyota/Daihatsu, Suzuki,
 * Honda…), with diesel and CNG differences. Key "RV" is the adapter's own voltage reading.
 */
object LiveRules {
    private val NOT_CHARGING = L10n("Not charging: check the alternator and its belt", "চার্জ হচ্ছে না: অল্টারনেটর ও বেল্ট দেখান")
    private val WEAK_CHARGING = L10n("Charging is weak: check the belt and battery terminals", "চার্জিং দুর্বল: বেল্ট ও ব্যাটারি টার্মিনাল দেখুন")
    private val OVERCHARGING = L10n("Overcharging: the voltage regulator may be faulty", "অতিরিক্ত চার্জ: ভোল্টেজ রেগুলেটর খারাপ হতে পারে")
    private val FLAT = L10n("Battery is flat or failing: charge and test it", "ব্যাটারি ডাউন বা খারাপ: চার্জ দিয়ে টেস্ট করান")
    private val WEAK_BATTERY = L10n("Battery is weak: charge it or have it tested", "ব্যাটারি দুর্বল: চার্জ দিন বা টেস্ট করান")
    private val OVERHEATING = L10n("Overheating: stop safely and let the engine cool", "ইঞ্জিন অতিরিক্ত গরম: নিরাপদে থামিয়ে ঠান্ডা হতে দিন")
    private val RUNNING_HOT = L10n("Running hot: check coolant level, radiator fan and radiator", "গরম চলছে: কুল্যান্ট, রেডিয়েটর ফ্যান ও রেডিয়েটর দেখুন")
    val NOT_WARMING = L10n("Not warming up: the thermostat may be stuck open", "গরম হচ্ছে না: থার্মোস্ট্যাট খোলা আটকে থাকতে পারে")
    private val HIGH_IDLE = L10n("High idle: clean the throttle body or check for an air leak", "আইডল বেশি: থ্রটল বডি পরিষ্কার করুন বা এয়ার লিক দেখুন")
    private val LOW_IDLE = L10n("Low idle: risk of stalling, clean the throttle body", "আইডল কম: বন্ধ হয়ে যেতে পারে, থ্রটল বডি পরিষ্কার করুন")
    val HUNTING = L10n("Idle is unsteady: clean the throttle body or idle valve", "আইডল ওঠানামা করছে: থ্রটল বডি বা আইডল ভালভ পরিষ্কার করুন")
    private val VERY_LEAN = L10n("Running very lean: check for vacuum leaks, fuel pump and injectors", "খুব লিন চলছে: ভ্যাকুয়াম লিক, ফুয়েল পাম্প ও ইনজেক্টর দেখান")
    private val LEAN = L10n("Running lean: possible vacuum leak or weak fuel supply", "লিন চলছে: ভ্যাকুয়াম লিক বা জ্বালানি সরবরাহ দুর্বল হতে পারে")
    private val VERY_RICH = L10n("Running very rich: check injectors, O2 sensor and air filter", "খুব রিচ চলছে: ইনজেক্টর, O2 সেন্সর ও এয়ার ফিল্টার দেখান")
    private val RICH = L10n("Running rich: check the air filter, injectors and O2 sensor", "রিচ চলছে: এয়ার ফিল্টার, ইনজেক্টর ও O2 সেন্সর দেখুন")
    private val HIGH_LOAD = L10n("High load at idle: check for a misfire, dragging brakes or AC strain", "আইডলে লোড বেশি: মিসফায়ার, ব্রেক আটকানো বা এসি চাপ দেখুন")
    private val HOT_INTAKE = L10n("Intake air is very hot: check the air intake and sensor", "ইনটেক বাতাস খুব গরম: এয়ার ইনটেক ও সেন্সর দেখুন")
    private val RETARDED = L10n("Ignition timing is retarded: possible knock or bad fuel", "ইগনিশন টাইমিং পিছিয়ে: নকিং বা খারাপ জ্বালানি হতে পারে")
    private val OIL_OVERHEAT = L10n("Oil is overheating: stop and check the oil level", "অয়েল অতিরিক্ত গরম: থামিয়ে অয়েলের মাত্রা দেখুন")
    private val OIL_HOT = L10n("Oil is hot: check the oil level and cooling", "অয়েল গরম: অয়েলের মাত্রা ও কুলিং দেখুন")
    private val WEAK_VACUUM = L10n("Weak intake vacuum: possible air leak or worn engine", "ইনটেক ভ্যাকুয়াম দুর্বল: এয়ার লিক বা ইঞ্জিন ক্ষয় হতে পারে")
    private val LOW_FUEL = L10n("Fuel is low", "জ্বালানি কম")

    /** The safe range to print under a reading, or null when there isn't a meaningful one right now. */
    fun range(key: String, ctx: LiveContext, p: EngineProfile): String? = when (key) {
        "RV", "42" -> if (ctx.running) "13.6–14.8 V" else "12.4–12.8 V"
        "05" -> "80–95 °C"
        "0C" -> if (ctx.idle && ctx.warm) (if (p == EngineProfile.DIESEL) "600–950 rpm" else "600–1,000 rpm") else null
        "06", "07" -> if (p == EngineProfile.PETROL) "−10 … +10 %" else null
        "04" -> if (ctx.idle && ctx.warm) "15–50 %" else null
        "0F" -> "< 65 °C"
        "0E" -> if (ctx.idle && ctx.warm && p != EngineProfile.DIESEL) "0–20°" else null
        "5C" -> "80–120 °C"
        "0B" -> if (ctx.idle && ctx.warm && p != EngineProfile.DIESEL) "25–55 kPa" else null
        "2F" -> "> 10 %"
        else -> null
    }

    fun judge(key: String, v: Double, ctx: LiveContext, p: EngineProfile): LiveVerdict {
        fun out(level: LiveLevel, msg: L10n?) = LiveVerdict(key, v, level, msg, range(key, ctx, p))
        val ok = out(LiveLevel.OK, null)
        return when (key) {
            "RV", "42" -> if (ctx.running) when {
                v < 13.2 -> out(LiveLevel.BAD, NOT_CHARGING)
                v < 13.6 -> out(LiveLevel.WARN, WEAK_CHARGING)
                v > 15.0 -> out(LiveLevel.BAD, OVERCHARGING)
                else -> ok
            } else when {
                v < 12.0 -> out(LiveLevel.BAD, FLAT)
                v < 12.4 -> out(LiveLevel.WARN, WEAK_BATTERY)
                else -> ok
            }
            "05" -> when {
                v > 104 -> out(LiveLevel.BAD, OVERHEATING)
                v > 96 -> out(LiveLevel.WARN, RUNNING_HOT)
                ctx.running && ctx.runSeconds > 600 && v < 70 -> out(LiveLevel.WARN, NOT_WARMING)
                else -> ok
            }
            "0C" -> if (ctx.idle && ctx.warm) {
                val high = if (p == EngineProfile.DIESEL) 950.0 else 1000.0
                when { v > high -> out(LiveLevel.WARN, HIGH_IDLE); v in 1.0..550.0 -> out(LiveLevel.WARN, LOW_IDLE); else -> ok }
            } else ok
            "07" -> if (p == EngineProfile.PETROL && ctx.warm && ctx.running) when {
                v > 20 -> out(LiveLevel.BAD, VERY_LEAN); v > 10 -> out(LiveLevel.WARN, LEAN)
                v < -20 -> out(LiveLevel.BAD, VERY_RICH); v < -10 -> out(LiveLevel.WARN, RICH)
                else -> ok
            } else ok
            "06" -> if (p == EngineProfile.PETROL && ctx.warm && ctx.idle) when {
                v > 25 -> out(LiveLevel.WARN, LEAN); v < -25 -> out(LiveLevel.WARN, RICH); else -> ok
            } else ok
            "04" -> if (ctx.idle && ctx.warm && v > 50) out(LiveLevel.WARN, HIGH_LOAD) else ok
            "0F" -> if (v > 65) out(LiveLevel.WARN, HOT_INTAKE) else ok
            "0E" -> if (ctx.idle && ctx.warm && p != EngineProfile.DIESEL && v < 0) out(LiveLevel.WARN, RETARDED) else ok
            "5C" -> when { v > 135 -> out(LiveLevel.BAD, OIL_OVERHEAT); v > 120 -> out(LiveLevel.WARN, OIL_HOT); else -> ok }
            "0B" -> if (ctx.idle && ctx.warm && p != EngineProfile.DIESEL && v > 55) out(LiveLevel.WARN, WEAK_VACUUM) else ok
            "2F" -> if (v < 10) out(LiveLevel.WARN, LOW_FUEL) else ok
            else -> ok
        }
    }
}

@Serializable data class LiveStat(val key: String, val min: Double, val max: Double, val avg: Double, val samples: Int)
@Serializable data class LiveFlag(val key: String, val level: LiveLevel, val en: String, val bn: String, val value: Double) {
    val message: L10n get() = L10n(en, bn)
}

/** What a live session found, stored with the vehicle's history. */
@Serializable
data class LiveSummary(
    val startedAtMillis: Long,
    val durationSec: Long,
    val stats: List<LiveStat>,
    val flags: List<LiveFlag>,
) {
    val worst: LiveLevel get() = flags.maxOfOrNull { it.level } ?: LiveLevel.OK
    fun stat(key: String) = stats.firstOrNull { it.key == key }
    fun toJson(): String = JSON.encodeToString(serializer(), this)
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        fun fromJson(s: String): LiveSummary? = runCatching { JSON.decodeFromString(serializer(), s) }.getOrNull()
    }
}

/**
 * Feeds once a second during live data. A reading only turns amber/red after it has stayed out of
 * range for a while (fuel trims 30 s, idle 15 s, battery 10 s, coolant 5 s), so one blip on a
 * bump or gear change never raises an alarm. Also watches for an idle that hunts up and down.
 */
class LiveMonitor(private val profile: EngineProfile) {
    private class Acc(var min: Double = Double.MAX_VALUE, var max: Double = -Double.MAX_VALUE, var sum: Double = 0.0, var n: Int = 0)
    private val acc = LinkedHashMap<String, Acc>()
    private val since = HashMap<String, Long>()          // key → when it first went out of range
    private val flags = LinkedHashMap<String, LiveFlag>() // key|message → worst occurrence
    private val idleRpm = ArrayDeque<Pair<Long, Double>>()
    private var start = -1L
    private var runningSince = -1L
    var samples = 0; private set

    private fun holdMs(key: String) = when (key) { "06", "07" -> 30_000L; "0C", "04" -> 15_000L; "RV", "42" -> 10_000L; "05", "5C" -> 5_000L; else -> 10_000L }

    /** Returns the verdict for every reading in [values] (plus "RV" if [volts] is known), as shown now. */
    fun feed(values: Map<String, Double>, volts: Double?, nowMillis: Long): List<LiveVerdict> {
        if (start < 0) start = nowMillis
        samples++
        val pre = LiveContext.of(values, volts)
        if (pre.running) { if (runningSince < 0) runningSince = nowMillis } else runningSince = -1
        val ctx = pre.copy(runSeconds = if (runningSince < 0) 0 else (nowMillis - runningSince) / 1000)
        val all = values + (volts?.let { mapOf("RV" to it) } ?: emptyMap())
        val out = all.map { (k, v) ->
            acc.getOrPut(k) { Acc() }.apply { min = minOf(min, v); max = maxOf(max, v); sum += v; n++ }
            confirm(LiveRules.judge(k, v, ctx, profile), nowMillis)
        }.toMutableList()
        // Hunting idle: RPM swinging more than 250 within 20 s while idling warm.
        val rpm = values["0C"]
        if (rpm != null && ctx.idle && ctx.warm) {
            idleRpm.addLast(nowMillis to rpm)
            while (idleRpm.isNotEmpty() && nowMillis - idleRpm.first().first > 20_000) idleRpm.removeFirst()
            val span = idleRpm.maxOf { it.second } - idleRpm.minOf { it.second }
            if (idleRpm.size >= 10 && span > 250) {
                val i = out.indexOfFirst { it.key == "0C" }
                val v = LiveVerdict("0C", rpm, LiveLevel.WARN, LiveRules.HUNTING, LiveRules.range("0C", ctx, profile))
                if (i >= 0 && out[i].level == LiveLevel.OK) out[i] = v
                record(v)
            }
        } else idleRpm.clear()
        return out
    }

    private fun confirm(v: LiveVerdict, now: Long): LiveVerdict {
        if (v.level == LiveLevel.OK) { since.remove(v.key); return v }
        val first = since.getOrPut(v.key) { now }
        return if (now - first >= holdMs(v.key)) v.also(::record) else v.copy(level = LiveLevel.OK, message = null)
    }

    private fun record(v: LiveVerdict) {
        val m = v.message ?: return
        val id = v.key + "|" + m.en
        val cur = flags[id]
        val worse = cur == null || v.level > cur.level ||
            (v.level == cur.level && kotlin.math.abs(v.value) > kotlin.math.abs(cur.value))
        if (worse) flags[id] = LiveFlag(v.key, v.level, m.en, m.bn, (v.value * 10).roundToInt() / 10.0)
    }

    fun summary(nowMillis: Long): LiveSummary = LiveSummary(
        startedAtMillis = start.coerceAtLeast(0),
        durationSec = if (start < 0) 0 else (nowMillis - start) / 1000,
        stats = acc.map { (k, a) -> LiveStat(k, round1(a.min), round1(a.max), round1(a.sum / a.n), a.n) },
        flags = flags.values.sortedByDescending { it.level },
    )

    private fun round1(d: Double) = (d * 10).roundToInt() / 10.0
}
