package com.garidoctor.core

import com.garidoctor.core.ai.*
import com.garidoctor.core.model.DocumentType
import java.time.LocalDate
import kotlin.test.*

class DateTests {
    @Test fun formats() {
        val d = Dates.find("Issue: 03/02/2025  Valid till 02-02-2026 also 2027-01-15 and 5 Mar 2028, January 9, 2029, ১২ জানুয়ারি ২০৩০")
        assertEquals(listOf(LocalDate.of(2025, 2, 3), LocalDate.of(2026, 2, 2), LocalDate.of(2027, 1, 15), LocalDate.of(2028, 3, 5), LocalDate.of(2029, 1, 9), LocalDate.of(2030, 1, 12)),
            d.map { it.date })
    }
    @Test fun rejectsNonsense() = assertTrue(Dates.find("Phone 01712-345678 total 32/13/2025").isEmpty())
}

class DocumentScanTests {
    private val today = LocalDate.of(2026, 11, 4).toEpochDay()

    @Test fun taxTokenEnglish() {
        val text = """
            BANGLADESH ROAD TRANSPORT AUTHORITY
            TAX TOKEN
            Registration No: DHAKA METRO-LA-12-3456
            Token No: TT2026/558812
            Valid From 15/01/2026 To 14/01/2027
        """.trimIndent()
        val d = DocumentTextParser.parse(text, today)
        assertEquals(DocumentType.TAX_TOKEN, d.type)
        assertEquals(LocalDate.of(2027, 1, 14).toEpochDay(), d.expiryEpochDay)
        assertEquals("DHAKA METRO-LA-12-3456", d.number)
        assertTrue(d.complete && d.confidence >= 0.85)
    }

    @Test fun fitnessBangla() {
        val text = "ফিটনেস সার্টিফিকেট\nঢাকা মেট্রো-গ ১১-২২৩৩\nইস্যুর তারিখ: ১০/০৩/২০২৬\nমেয়াদ: ০৯/০৩/২০২৭ পর্যন্ত"
        val d = DocumentTextParser.parse(text, today)
        assertEquals(DocumentType.FITNESS, d.type)
        assertEquals(LocalDate.of(2027, 3, 9).toEpochDay(), d.expiryEpochDay)
    }

    @Test fun insuranceWithPolicyNumber() {
        val d = DocumentTextParser.parse("Motor Insurance Cover Note\nPolicy No: MC-DHK-0099812\nPeriod of insurance: 01 Dec 2025 to 30 Nov 2026", today)
        assertEquals(DocumentType.INSURANCE, d.type)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), d.expiryEpochDay)
        assertEquals("MC-DHK-0099812", d.number)
    }

    @Test fun loneIssueDateIsNotExpiry() {
        assertNull(DocumentTextParser.parse("Driving Licence\nDate of issue: 12/05/2024", today).expiryEpochDay)
    }
}

class ReceiptScanTests {
    private val parts = setOf("engine_oil", "air_filter", "spark_plug", "brake_front", "chain", "tyre_rear")

    @Test fun mixedBill() {
        val text = """
            রহমান মোটরস
            Date: 02/11/2026
            ODO 24,560 km
            Engine oil 1L  950
            Air filter      350 tk
            চেইন টাইট ও ক্লিন  100
            Labour  200
            মোট ১,৬০০ টাকা
            Mobile 01711-000000
        """.trimIndent()
        val r = ReceiptTextParser.parse(text, parts)
        assertEquals(listOf("engine_oil", "air_filter", "chain"), r.items.map { it.partId })
        assertEquals(listOf(950, 350, 100), r.items.map { it.amountBdt })
        assertEquals(1600, r.totalBdt)
        assertEquals(24560, r.odometerKm)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), r.epochDay)
        assertTrue(r.confidence >= 0.9)
    }

    @Test fun nothingFound() = assertTrue(ReceiptTextParser.parse("Thank you, come again", parts).items.isEmpty())
}

class VoiceTests {
    private val parts = setOf("engine_oil", "chain", "brake_front", "air_filter")

    @Test fun banglaOilChange() {
        val a = VoiceCommandParser.parse("আজ ৪০০ টাকায় তেল বদলালাম মিটার ২৪৫০০", parts)
        assertEquals(VoiceAction.Service(listOf("engine_oil"), 400, 24500), a)
    }
    @Test fun englishFuel() {
        val a = VoiceCommandParser.parse("filled 5 litres octane 650 taka at 24600 km", parts) as VoiceAction.Fuel
        assertEquals(5.0, a.amount); assertEquals(650, a.costBdt); assertEquals(24600, a.odometerKm); assertFalse(a.cng)
    }
    @Test fun cng() {
        val a = VoiceCommandParser.parse("সিএনজি নিলাম ৮ ঘনমিটার ৩৫০ টাকা", parts) as VoiceAction.Fuel
        assertTrue(a.cng); assertEquals(8.0, a.amount); assertEquals(350, a.costBdt)
    }
    @Test fun odometerOnly() = assertEquals(VoiceAction.Odometer(25100), VoiceCommandParser.parse("meter 25100", parts))
    @Test fun unknown() = assertEquals(VoiceAction.Unknown, VoiceCommandParser.parse("hello there", parts))
}

class AiJsonTests {
    @Test fun paperWithFence() {
        val d = AiJson.paper("```json\n{\"type\":\"FITNESS\",\"number\":\"F-77\",\"expiry\":\"2027-03-09\"}\n```")!!
        assertEquals(DocumentType.FITNESS, d.type); assertEquals(LocalDate.of(2027, 3, 9).toEpochDay(), d.expiryEpochDay); assertEquals("ai", d.source)
    }
    @Test fun receiptFiltersUnknownParts() {
        val r = AiJson.receipt("""{"items":[{"label":"Mobil","partId":"engine_oil","amount":950},{"label":"Magic","partId":"warp_drive","amount":5}],"total":955,"date":"2026-11-02","odometerKm":24560}""", setOf("engine_oil"))!!
        assertEquals(listOf("engine_oil", null), r.items.map { it.partId }); assertEquals(955, r.totalBdt)
    }
    @Test fun voiceAndLight() {
        assertEquals(VoiceAction.Odometer(100), AiJson.voice("""{"kind":"odometer","km":100}""", emptySet()))
        assertEquals("oil_pressure" to 0.8, AiJson.warningLight("""{"id":"oil_pressure","confidence":0.8}"""))
        assertNull(AiJson.warningLight("""{"id":"made_up"}"""))
        assertNull(AiJson.paper("sorry, I can't"))
    }
    @Test fun quota() {
        val q = AiQuota(used = 3, bonus = 1, limit = AiQuota.limitFor(false, 3, 30))
        assertEquals(1, q.remaining)
        assertEquals(AiQuota.HARD_CAP, AiQuota.limitFor(true, 3, 999))
        assertEquals(20_396L, AiQuota.utcDay(1_762_214_400_000L))
    }
}
