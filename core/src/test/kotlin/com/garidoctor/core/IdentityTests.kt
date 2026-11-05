package com.garidoctor.core

import com.garidoctor.core.ai.DocumentTextParser
import com.garidoctor.core.history.History
import com.garidoctor.core.history.HistoryEntry
import com.garidoctor.core.history.HistoryKind
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.identity.VehicleIdentity.Field
import com.garidoctor.core.model.Lang
import com.garidoctor.core.model.Translations
import java.time.LocalDate
import kotlin.test.*

class IdentityTests {
    @BeforeTest fun reset() = Translations.reset()

    private val axio = VehicleIdentity("Family Axio", "Car", "Toyota Corolla Axio", "DHAKA METRO-GA 12-3456", "1NZ-A123456", "NZE141-6012345",
        "Rahim Uddin", "DK0123456789", "01711000000")

    @Test fun qrTextHasEveryReferenceAndSkipsBlanks() {
        val q = axio.qrText(Lang.EN)
        assertTrue(q.startsWith("GariDoctor · Vehicle ID\nFamily Axio — Car · Toyota Corolla Axio"), q)
        listOf("Registration no.: DHAKA METRO-GA 12-3456", "Engine no.: 1NZ-A123456", "Chassis no.: NZE141-6012345",
            "Driver: Rahim Uddin", "Driving licence no.: DK0123456789", "Phone: 01711000000").forEach { assertTrue(it in q, "missing $it in\n$q") }
        val noDriver = axio.qrText(Lang.EN, includeDriver = false)
        assertFalse("Rahim" in noDriver || "01711000000" in noDriver)
        val bare = VehicleIdentity("Bike", "Motorcycle", registrationNo = "  ").qrText(Lang.EN)
        assertEquals("GariDoctor · Vehicle ID\nBike — Motorcycle", bare)
    }

    @Test fun qrTextIsLocalised() {
        val bn = axio.qrText(Lang.BN)
        assertTrue("ইঞ্জিন নম্বর: 1NZ-A123456" in bn, bn)
        val hi = axio.qrText(Lang.HI)
        assertFalse("Engine no." in hi, hi)
    }

    @Test fun duplicatesIgnoreSpacingCaseAndBanglaDigits() {
        val other = VehicleIdentity("Old Axio", "Car", registrationNo = "Dhaka Metro Ga ১২-৩৪৫৬", chassisNo = "nze141 6012345")
        val d = VehicleIdentity.duplicates(axio, listOf(other))
        assertEquals(setOf(Field.REGISTRATION, Field.CHASSIS), d.keys)
        assertEquals("Old Axio", d[Field.REGISTRATION])
        assertTrue(VehicleIdentity.duplicates(axio, listOf(VehicleIdentity("X", "Car", registrationNo = "DHAKA METRO-GA 99-0000"))).isEmpty())
        // Blank references never clash.
        assertTrue(VehicleIdentity.duplicates(VehicleIdentity("A", "Car"), listOf(VehicleIdentity("B", "Car"))).isEmpty())
    }

    @Test fun registrationScanFindsEngineAndChassis() {
        val text = """
            GOVERNMENT OF BANGLADESH · BRTA
            Certificate of Registration
            Registration No: DHAKA METRO-GA 12-3456
            Engine No: 1NZ-A123456
            Chassis No.: NZE141-6012345
            Valid till 14/03/2027
        """.trimIndent()
        val d = DocumentTextParser.parse(text, LocalDate.of(2026, 1, 1).toEpochDay())
        assertEquals("1NZ-A123456", d.engineNo)
        assertEquals("NZE141-6012345", d.chassisNo)
        val bn = DocumentTextParser.parse("ইঞ্জিন নং: JF50E১২৩৪৫৬৭\nচেসিস নম্বর: MD2A11CZ5KCA12345", 0)
        assertEquals("JF50E1234567", bn.engineNo)
        assertEquals("MD2A11CZ5KCA12345", bn.chassisNo)
        // "English" is not an engine number, "province" not a VIN.
        val none = DocumentTextParser.parse("Written in English 2024. Province 7 office", 0)
        assertNull(none.engineNo); assertNull(none.chassisNo)
    }

    @Test fun historyGroupsByMonthAndSumsSpend() {
        val day = { y: Int, m: Int, d: Int -> LocalDate.of(y, m, d).toEpochDay() }
        val e = listOf(
            HistoryEntry(day(2026, 3, 2), HistoryKind.SERVICE, "Engine oil", amountBdt = 900, odometerKm = 24000),
            HistoryEntry(day(2026, 3, 20), HistoryKind.FUEL, "Fuel", amountBdt = 1200, odometerKm = 24300),
            HistoryEntry(day(2026, 1, 5), HistoryKind.PAPER, "Tax token renewed", amountBdt = 3000),
            HistoryEntry(day(2024, 12, 1), HistoryKind.SERVICE, "Brake pads", amountBdt = 1500),
            HistoryEntry(day(2026, 3, 20), HistoryKind.ODOMETER, "Odometer", odometerKm = 24300),
        )
        val months = History.byMonth(e)
        assertEquals(listOf(2026 to 3, 2026 to 1, 2024 to 12), months.map { it.year to it.month })
        assertEquals(2100, months.first().spentBdt)
        assertEquals(HistoryKind.FUEL, months.first().entries.first().kind) // newest day first
        val s = History.spend(e, day(2026, 4, 1))
        assertEquals(6600, s.totalBdt); assertEquals(5100, s.last12MonthsBdt)
        assertEquals(2400, s.serviceBdt); assertEquals(1200, s.fuelBdt); assertEquals(3000, s.otherBdt); assertEquals(4, s.bills)
        assertEquals(2, History.filter(e, setOf(HistoryKind.SERVICE)).size)
    }
}
