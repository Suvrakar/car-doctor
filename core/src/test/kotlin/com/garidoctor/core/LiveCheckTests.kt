package com.garidoctor.core

import com.garidoctor.core.health.DeductionKind
import com.garidoctor.core.health.HealthInputs
import com.garidoctor.core.health.HealthScoreEngine
import com.garidoctor.core.obd.*
import kotlin.test.*

class LiveCheckTests {
    private val warmIdle = mapOf("0C" to 720.0, "0D" to 0.0, "05" to 88.0, "11" to 15.0)

    @Test fun batteryDependsOnEngineState() {
        val off = LiveContext.of(emptyMap(), 12.2)
        assertFalse(off.running)
        assertEquals(LiveLevel.WARN, LiveRules.judge("RV", 12.2, off, EngineProfile.PETROL).level)
        val on = LiveContext.of(warmIdle, 12.9)
        assertTrue(on.running)
        // 12.9 V is fine with the engine off but means "not charging" with it running.
        assertEquals(LiveLevel.BAD, LiveRules.judge("RV", 12.9, on, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.OK, LiveRules.judge("RV", 14.1, on, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.BAD, LiveRules.judge("RV", 15.4, on, EngineProfile.PETROL).level)
        assertEquals("13.6–14.8 V", LiveRules.range("RV", on, EngineProfile.PETROL))
    }

    @Test fun coolantAndIdle() {
        val ctx = LiveContext.of(warmIdle, 14.0)
        assertTrue(ctx.idle && ctx.warm)
        assertEquals(LiveLevel.WARN, LiveRules.judge("05", 99.0, ctx, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.BAD, LiveRules.judge("05", 106.0, ctx, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.WARN, LiveRules.judge("0C", 1150.0, ctx, EngineProfile.PETROL).level)
        // A cold engine idles high on purpose: no flag.
        val cold = LiveContext.of(warmIdle + ("05" to 40.0), 14.0)
        assertEquals(LiveLevel.OK, LiveRules.judge("0C", 1250.0, cold, EngineProfile.PETROL).level)
        // Not warming after 10 minutes of running.
        assertEquals(LiveLevel.WARN, LiveRules.judge("05", 60.0, cold.copy(runSeconds = 700), EngineProfile.PETROL).level)
    }

    @Test fun fuelTrimsOnlyOnWarmPetrol() {
        val ctx = LiveContext.of(warmIdle, 14.0)
        assertEquals(LiveLevel.WARN, LiveRules.judge("07", 14.0, ctx, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.BAD, LiveRules.judge("07", -23.0, ctx, EngineProfile.PETROL).level)
        assertEquals(LiveLevel.OK, LiveRules.judge("07", 14.0, ctx, EngineProfile.GAS).level)
        assertEquals(LiveLevel.OK, LiveRules.judge("07", 14.0, ctx, EngineProfile.DIESEL).level)
    }

    @Test fun monitorWaitsBeforeFlagging() {
        val m = LiveMonitor(EngineProfile.PETROL)
        val hot = warmIdle + ("05" to 101.0)
        // 0–4 s: still shown OK (could be a blip)
        for (s in 0..4) assertEquals(LiveLevel.OK, m.feed(hot, 14.0, s * 1000L).first { it.key == "05" }.level)
        assertEquals(LiveLevel.WARN, m.feed(hot, 14.0, 5000L).first { it.key == "05" }.level)
        // Lean trim needs 30 s.
        val lean = warmIdle + ("07" to 15.0)
        val m2 = LiveMonitor(EngineProfile.PETROL)
        for (s in 0..29) assertEquals(LiveLevel.OK, m2.feed(lean, 14.0, s * 1000L).first { it.key == "07" }.level)
        assertEquals(LiveLevel.WARN, m2.feed(lean, 14.0, 30_000L).first { it.key == "07" }.level)
        // A blip that recovers is never recorded.
        val m3 = LiveMonitor(EngineProfile.PETROL)
        m3.feed(hot, 14.0, 0); m3.feed(warmIdle, 14.0, 1000); m3.feed(hot, 14.0, 2000)
        assertTrue(m3.summary(3000).flags.isEmpty())
    }

    @Test fun huntingIdle() {
        val m = LiveMonitor(EngineProfile.PETROL)
        var last: List<LiveVerdict> = emptyList()
        for (s in 0 until 12) last = m.feed(warmIdle + ("0C" to if (s % 2 == 0) 600.0 else 950.0), 14.0, s * 1000L)
        assertEquals(LiveRules.HUNTING, last.first { it.key == "0C" }.message)
    }

    @Test fun summaryStatsAndJsonRoundTrip() {
        val m = LiveMonitor(EngineProfile.PETROL)
        for (s in 0..20) m.feed(warmIdle + ("05" to 90.0 + s / 2), 12.8, s * 1000L) // running + 12.8 V = not charging
        val sum = m.summary(21_000)
        assertEquals(21, sum.durationSec)
        assertEquals(100.0, sum.stat("05")!!.max)
        assertEquals(LiveLevel.BAD, sum.worst)
        assertTrue(sum.flags.any { it.key == "RV" && it.level == LiveLevel.BAD })
        assertTrue(sum.flags.any { it.key == "05" && it.level == LiveLevel.WARN })
        assertEquals(sum, LiveSummary.fromJson(sum.toJson()))
    }

    @Test fun healthScoreUsesRecentLiveCheck() {
        val sum = LiveSummary(0, 60, emptyList(), listOf(
            LiveFlag("05", LiveLevel.BAD, "Overheating: stop safely and let the engine cool", "", 106.0),
            LiveFlag("07", LiveLevel.WARN, "Running lean: possible vacuum leak or weak fuel supply", "", 14.0)))
        val r = HealthScoreEngine.compute(HealthInputs(emptyList(), todayEpochDay = 100, liveCheck = sum, liveCheckEpochDay = 95))
        assertEquals(89, r.score)
        assertEquals(2, r.deductions.count { it.kind == DeductionKind.LIVE_DATA })
        // Older than 30 days: ignored.
        assertEquals(100, HealthScoreEngine.compute(HealthInputs(emptyList(), todayEpochDay = 200, liveCheck = sum, liveCheckEpochDay = 95)).score)
    }
}
