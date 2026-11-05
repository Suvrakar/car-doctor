package com.garidoctor.core

import com.garidoctor.core.obd.ObdDecoder
import kotlin.test.*

class ObdMoreTests {
    @Test fun supportedPidsFromBitmask() {
        // BE = 1011 1110 → PIDs 01,03,04,05,06,07 ; 1F = 0001 1111 → 0C..10 ; A8 13 ...
        val s = ObdDecoder.decodeSupported("41 00 BE 1F A8 13\r\n41 20 80 01 80 01")
        assertTrue(listOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11).all { it in s }, s.toString())
        assertFalse(0x02 in s); assertFalse(0x0B in s)
        assertTrue(0x21 in s && 0x40 in s)
        val pids = ObdDecoder.livePidsFor(s)
        assertTrue("010C" in pids && "0105" in pids && "0111" in pids)
        assertFalse("010B" in pids || "0142" in pids || "015C" in pids)
        assertEquals(ObdDecoder.LIVE_PIDS, ObdDecoder.livePidsFor(emptySet()))
    }

    @Test fun twoEcusMasksMerge() {
        val s = ObdDecoder.decodeSupported("7E8 06 41 00 80 00 00 01\r\n7E9 06 41 00 00 08 00 00".replace("7E8 06 ", "").replace("7E9 06 ", ""))
        assertTrue(0x01 in s && 0x0D in s && 0x20 in s)
    }

    @Test fun checkEngineLightAndCount() {
        val st = ObdDecoder.decodeStatus("41 01 83 07 65 04")!!
        assertTrue(st.milOn); assertEquals(3, st.storedCodes)
        val off = ObdDecoder.decodeStatus("4101000765 04")!!
        assertFalse(off.milOn); assertEquals(0, off.storedCodes)
        assertNull(ObdDecoder.decodeStatus("NO DATA"))
    }

    @Test fun pendingCodes() {
        val c = ObdDecoder.decodeDtcs("47 01 71 00 00 00 00", responseByte = 0x47)
        assertEquals(listOf("P0171"), c.map { it.code })
        assertTrue(ObdDecoder.decodeDtcs("47 01 71 00 00 00 00").isEmpty()) // not a stored-code reply
    }

    @Test fun vinCanAndLegacy() {
        val can = "014\r\n0: 49 02 01 4D 48 46\r\n1: 41 41 41 31 32 33 34\r\n2: 35 36 37 38 39 30 31\r\n>"
        assertEquals("MHFAAA12345678901", ObdDecoder.decodeVin(can))
        val legacy = listOf("49 02 01 00 00 00 4D", "49 02 02 48 46 41 41", "49 02 03 41 31 32 33", "49 02 04 34 35 36 37", "49 02 05 38 39 30 31").joinToString("\r\n")
        assertEquals("MHFAAA12345678901", ObdDecoder.decodeVin(legacy))
        assertNull(ObdDecoder.decodeVin("NO DATA"))
        assertNull(ObdDecoder.decodeVin("49 02 01 4D 48"))
    }

    @Test fun adapterVoltageAndProtocol() {
        assertEquals(12.6, ObdDecoder.decodeAdapterVoltage("12.6V\r\n>"))
        assertEquals(14.1, ObdDecoder.decodeAdapterVoltage("14,1V"))
        assertNull(ObdDecoder.decodeAdapterVoltage("?"))
        assertEquals("ISO 15765-4 (CAN 11/500)", ObdDecoder.protocolName("AUTO, ISO 15765-4 (CAN 11/500)\r\n>"))
        assertEquals("ISO 9141-2", ObdDecoder.protocolName("ISO 9141-2"))
    }

    @Test fun moreLivePids() {
        assertEquals(-25.0, ObdDecoder.decodeLive("41 06 60")!!.value, 0.01)
        assertEquals(90.0, ObdDecoder.decodeLive("41 5C 82")!!.value)
        assertEquals(5.0, ObdDecoder.decodeLive("41 0E 8A")!!.value)
        assertEquals(12.5, ObdDecoder.decodeLive("41 10 04 E2")!!.value)
    }
}
