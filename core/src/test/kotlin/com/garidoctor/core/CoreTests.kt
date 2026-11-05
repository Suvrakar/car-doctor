package com.garidoctor.core

import com.garidoctor.core.battery.BatteryHealth
import com.garidoctor.core.battery.RangeSample
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.fuel.FuelEntry
import com.garidoctor.core.health.Bn
import com.garidoctor.core.health.DeductionKind
import com.garidoctor.core.health.HealthInputs
import com.garidoctor.core.health.HealthScoreEngine
import com.garidoctor.core.model.*
import com.garidoctor.core.obd.ObdDecoder
import com.garidoctor.core.ocr.OdometerParser
import com.garidoctor.core.passport.VehiclePassport
import com.garidoctor.core.pro.ProCodes
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.schedule.MaintenanceScheduler
import com.garidoctor.core.symptom.SymptomSession
import com.garidoctor.core.symptom.Urgency
import com.garidoctor.core.template.TemplateRegistry
import kotlin.test.*

class TemplateTests {
    private val reg = TemplateRegistry.fromClasspath()

    @Test fun allBundledTemplatesLoadAndValidate() {
        assertEquals(TemplateRegistry.BUNDLED_TEMPLATES.toSet(), reg.all.map { it.id }.toSet())
        reg.all.forEach { assertTrue(TemplateRegistry.validate(it).isEmpty(), it.id) }
        assertTrue(reg.all.sumOf { it.models.size } >= 20)
    }

    @Test fun everyKindOfVehicleCanBeAdded() {
        val reg = TemplateRegistry.fromClasspath()
        listOf("motorcycle", "scooter", "car", "suv", "cng_auto", "easy_bike", "e_scooter", "microbus", "bus", "pickup", "truck", "other")
            .forEach { assertNotNull(reg.template(it), "missing vehicle type $it") }
        assertTrue(reg.template("other")!!.models.isEmpty(), "Other vehicle has no presets, so the model step is skipped")
        assertEquals("বাস", reg.template("bus")!!.name.get(com.garidoctor.core.model.Lang.BN))
    }

    @Test fun everyTemplateHasItsSymptomTree() {
        reg.all.forEach { t -> assertNotNull(reg.tree(t.symptomTreeId), "tree for ${t.id}") }
        reg.allTrees.forEach { assertTrue(it.validate().isEmpty(), "${it.id}: ${it.validate()}") }
    }

    @Test fun diagnosisPartIdsExistInTemplatesUsingTree() {
        reg.all.forEach { t ->
            val tree = reg.tree(t.symptomTreeId)!!
            val partIds = tree.nodes.flatMap { n -> n.options.mapNotNull { it.diagnosis?.partId } }.toSet()
            // Unknown part ids are tolerated (ignored by score) but most should match.
            val matched = partIds.count { t.part(it) != null }
            assertTrue(matched > 0, "no matching parts for ${t.id}")
        }
    }

    @Test fun modelOverrideApplies() {
        val moto = reg.template("motorcycle")!!
        assertEquals(1500, moto.part("engine_oil")!!.intervalKm)
        assertEquals(2000, moto.effectivePart("engine_oil", "yamaha_fzs_v3")!!.intervalKm)
        assertEquals(4, moto.effectivePart("engine_oil", "yamaha_fzs_v3")!!.intervalMonths)
    }

    @Test fun userOverrideWinsOverModel() {
        val moto = reg.template("motorcycle")!!
        val p = moto.effectivePart("engine_oil", "yamaha_fzs_v3", mapOf("engine_oil" to com.garidoctor.core.template.IntervalOverride(intervalKm = 1200)))!!
        assertEquals(1200, p.intervalKm)
        assertEquals(4, p.intervalMonths)
    }

    @Test fun remoteTemplateNewerVersionReplacesAndInvalidRejected() {
        val r = TemplateRegistry.fromClasspath()
        val issues = r.applyRemoteTemplate("""{"id":"motorcycle","version":2,"name":{"en":"Bike","bn":"বাইক"},"defaultEnergy":"OCTANE","parts":[{"id":"engine_oil","name":{"en":"Oil","bn":"তেল"},"category":"ENGINE_OIL","intervalKm":1200,"weight":20}],"documents":["REGISTRATION"]}""")
        assertTrue(issues.isEmpty())
        assertEquals(1200, r.template("motorcycle")!!.part("engine_oil")!!.intervalKm)
        val bad = r.applyRemoteTemplate("""{"id":"x","name":{"en":"x","bn":"x"},"defaultEnergy":"OCTANE","parts":[{"id":"a","name":{"en":"a","bn":"a"},"category":"OTHER","weight":99}],"documents":[]}""")
        assertTrue(bad.isNotEmpty())
        assertNull(r.template("x"))
    }
}

class HealthTests {
    private val reg = TemplateRegistry.fromClasspath()
    private val today = 20_000L

    @Test fun pulsarExampleFromPitch() {
        val t = reg.template("motorcycle")!!
        val v = Vehicle(1, "motorcycle", "bajaj_pulsar_150", "Pulsar", "DHAKA METRO-LA 12-3456", 21_900, EnergyType.OCTANE, today - 60)
        val services = t.parts.filter { it.id != "engine_oil" && it.id != "chain" }.map { ServiceEvent(it.id, 21_000, today - 10) } +
            listOf(ServiceEvent("engine_oil", 20_000, today - 40), ServiceEvent("chain", 20_700, today - 20))
        val docs = listOf(VehicleDocument(DocumentType.TAX_TOKEN, today + 9))
        val c = VehicleDoctor.checkup(t, VehicleRecords(v, 20_000, services, docs), today)
        val oil = c.parts.first { it.part.id == "engine_oil" }
        assertEquals(DueState.OVERDUE, oil.state)
        assertEquals(-400, oil.kmRemaining)
        val kinds = c.health.deductions.map { it.kind }
        assertTrue(DeductionKind.SERVICE_OVERDUE in kinds)
        assertTrue(DeductionKind.DOCUMENT in kinds)
        assertTrue(c.health.score in 60..85, "score ${c.health.score}")
        val s = c.health.summary("Pulsar", Lang.EN)
        assertTrue(s.startsWith("Your Pulsar is at ${c.health.score}/100."), s)
        assertTrue("400 km overdue" in s, s)
        assertTrue(c.health.summary("Pulsar", Lang.BN).contains("১০০"))
    }

    @Test fun freshVehicleScores100() {
        val t = reg.template("car")!!
        val v = Vehicle(1, "car", "toyota_axio", "Axio", null, 50_000, EnergyType.OCTANE, today)
        val c = VehicleDoctor.checkup(t, VehicleRecords(v, 50_000), today)
        assertEquals(100, c.health.score)
    }

    @Test fun neglectedVehicleNeverBelowZero() {
        val t = reg.template("car")!!
        val v = Vehicle(1, "car", null, "Old", null, 300_000, EnergyType.OCTANE, today - 2000)
        val docs = DocumentType.entries.map { VehicleDocument(it, today - 100) }
        val c = VehicleDoctor.checkup(t, VehicleRecords(v, 100_000, documents = docs), today)
        assertEquals(0, c.health.score)
        assertTrue(c.health.deductions.filter { it.kind == DeductionKind.DOCUMENT }.sumOf { it.points } <= HealthScoreEngine.DOC_CAP)
    }

    @Test fun failedCheckAndSymptomCountOncePerPart() {
        val t = reg.template("motorcycle")!!
        val parts = MaintenanceScheduler.statuses(t.parts, t.parts.map { ServiceEvent(it.id, 1000, today) }, 1000, today, 1000, today)
        val r = HealthScoreEngine.compute(HealthInputs(parts,
            checks = listOf(CheckResult("brake_front", false, today + 1)),
            symptoms = listOf(ReportedSymptom("brake_front", "spongy", today + 1)), todayEpochDay = today + 1))
        assertEquals(1, r.deductions.size)
        assertEquals(15, r.deductions.single().points)
        assertEquals(85, r.score)
    }

    @Test fun timeBasedIntervalTriggersFirst() {
        val oil = reg.template("motorcycle")!!.part("engine_oil")!!
        val s = MaintenanceScheduler.status(oil, ServiceEvent("engine_oil", 1000, today - 150), 1100, today, 0, 0)
        assertEquals(DueState.OVERDUE, s.state)
        assertEquals("time", s.drivenBy)
    }

    @Test fun lowVoltageDeducts() {
        val t = reg.template("motorcycle")!!
        val parts = MaintenanceScheduler.statuses(t.parts, emptyList(), 0, today, 0, today)
        val r = HealthScoreEngine.compute(HealthInputs(parts, voltage = VoltageReading(11.8, 12.0, today), todayEpochDay = today))
        assertEquals(DeductionKind.LOW_VOLTAGE, r.deductions.single().kind)
    }

    @Test fun bnDigits() {
        assertEquals("১২৩", Bn.digits(123))
        assertEquals("123", Bn.toAscii("১২৩"))
    }
}

class FuelTests {
    @Test fun efficiencyTankToTankAndAnomaly() {
        val e = mutableListOf<FuelEntry>()
        var km = 10_000; var day = 1000L
        e += FuelEntry(km, 5.0, 650, true, day)
        repeat(6) { km += 200; day += 40; e += FuelEntry(km, 5.0, 650, true, day) } // 40 km/L baseline
        repeat(2) { km += 160; day += 16; e += FuelEntry(km, 5.0, 650, true, day) } // 32 km/L recent
        val stats = FuelAnalyzer.stats(e, EfficiencyUnit.KM_PER_LITRE, day)
        assertEquals(40.0, stats.points.first().efficiency, 0.01)
        val trend = stats.trend!!
        assertEquals(20.0, trend.dropPercent, 0.5)
        val alert = FuelAnalyzer.alert(trend, electric = false)!!
        assertEquals(20, alert.dropPercent)
        assertTrue(alert.causes.any { "tyre" in it.en })
    }

    @Test fun partialFillsAccumulate() {
        val pts = FuelAnalyzer.points(listOf(
            FuelEntry(0, 10.0, null, true, 1), FuelEntry(100, 2.0, null, false, 2), FuelEntry(300, 4.0, null, true, 3)))
        assertEquals(1, pts.size)
        assertEquals(50.0, pts[0].efficiency, 0.01)
    }

    @Test fun noTrendWithoutBaseline() {
        assertNull(FuelAnalyzer.trend(emptyList(), EfficiencyUnit.KM_PER_M3, 100))
    }
}

class DocTests {
    @Test fun expiryStates() {
        assertEquals(ExpiryState.EXPIRED, DocumentExpiry.status(VehicleDocument(DocumentType.FITNESS, 99), 100).state)
        assertEquals(ExpiryState.EXPIRING_15, DocumentExpiry.status(VehicleDocument(DocumentType.FITNESS, 109), 100).state)
        assertTrue(DocumentExpiry.shouldRemind(VehicleDocument(DocumentType.INSURANCE, 130), 100))
        assertFalse(DocumentExpiry.shouldRemind(VehicleDocument(DocumentType.INSURANCE, 131), 100))
    }
}

class SymptomTests {
    private val reg = TemplateRegistry.fromClasspath()

    @Test fun pullsWhenBrakingPath() {
        val s = SymptomSession(reg.tree("motorcycle")!!, "pulls")
        assertEquals("pulls_q1", s.current!!.id)
        s.answer(0)
        val d = s.diagnosis!!
        assertEquals(Urgency.THIS_WEEK, d.urgency)
        assertEquals("brake_front", d.partId)
        assertTrue(s.back()); assertNull(s.diagnosis); assertEquals("pulls_q1", s.current!!.id)
    }

    @Test fun easyBikeRangeHalfPath() {
        val s = SymptomSession(reg.tree("easy_bike")!!, "range")
        s.answer(0); s.answer(1); s.answer(0)
        assertEquals("traction_battery", s.diagnosis!!.partId)
    }

    @Test fun carWhiteSmokeHeadGasketIsStopNow() {
        val s = SymptomSession(reg.tree("car")!!, "smoke_white")
        s.answer(1); s.answer(0)
        assertEquals(Urgency.STOP_NOW, s.diagnosis!!.urgency)
    }
}

class ObdTests {
    private val dict = ObdDecoder.loadDictionary(javaClass.classLoader.getResource("obd/dtc.json")!!.readText())

    @Test fun decodesStoredCodes() {
        val codes = ObdDecoder.decodeDtcs("43 01 33 03 00 00 00\r\n>", dict)
        assertEquals(listOf("P0133", "P0300"), codes.map { it.code })
        assertEquals("অক্সিজেন সেন্সর ধীর (B1S1)", codes[0].describe().bn)
    }

    @Test fun decodesCanWithCountByte() {
        val codes = ObdDecoder.decodeDtcs("43 02 01 71 C0 35", dict)
        assertEquals(listOf("P0171", "U0035"), codes.map { it.code })
    }

    @Test fun noData() = assertTrue(ObdDecoder.decodeDtcs("NO DATA").isEmpty())

    @Test fun live() {
        assertEquals(1726.0, ObdDecoder.decodeLive("41 0C 1A F8")!!.value)
        assertEquals(10.0, ObdDecoder.decodeLive("410532")!!.value)
        assertNull(ObdDecoder.decodeLive("7F 01 12"))
    }
}

class OcrTests {
    @Test fun picksOdometerOverTripAndClock() {
        val text = "TRIP 123.4\n10:45\nODO 024567\n60 80 100"
        assertEquals(24567, OdometerParser.best(text, 24_100)!!.km)
    }

    @Test fun banglaDigitsAndLetterConfusion() {
        assertEquals(15320, OdometerParser.best("১৫৩২০ km", 15_000)!!.km)
        assertEquals(10560, OdometerParser.best("ODO 1O56O", 10_000)!!.km)
    }

    @Test fun rejectsLowerThanPrevious() {
        val best = OdometerParser.best("12000\n45210", 45_000)
        assertEquals(45210, best!!.km)
    }
}

class MiscTests {
    @Test fun batteryHealthFromRange() {
        val r = BatteryHealth.report(EnergyType.ELECTRIC_LEAD_ACID, 100.0, 300, 0.0,
            listOf(RangeSample(1, 62.0), RangeSample(2, 58.0), RangeSample(3, 60.0)), 45)
        assertEquals(60, r.healthPercent)
        assertEquals(100, r.cyclesLeft)
        assertTrue(r.advice.size >= 2)
    }

    @Test fun proCodes() {
        val c = ProCodes.generate(42, "salt")
        assertTrue(ProCodes.isValid(c, "salt"), c)
        assertTrue(ProCodes.isValid(c.lowercase(), "salt"))
        assertFalse(ProCodes.isValid(c, "other"))
        assertFalse(ProCodes.isValid("GD-AAAA-AAAA-AAAA", "salt"))
    }

    @Test fun passportQrRoundTrip() {
        val reg = TemplateRegistry.fromClasspath()
        val t = reg.template("motorcycle")!!
        val v = Vehicle(1, "motorcycle", null, "Bike", "DHA-1", 5000, EnergyType.OCTANE, 100)
        val c = VehicleDoctor.checkup(t, VehicleRecords(v, 5000), 100)
        val p = VehiclePassport.build(v, "Motorcycle", null, c.health, emptyList(), emptyList(), null, 100)
        val q = VehiclePassport.parseQr(p.qrPayload())!!
        assertEquals("DHA-1", q["reg"]); assertEquals("100", q["score"])
        assertEquals(64, p.fingerprint.length)
    }
}
