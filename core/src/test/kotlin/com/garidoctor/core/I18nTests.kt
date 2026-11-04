package com.garidoctor.core

import com.garidoctor.core.model.L10n
import com.garidoctor.core.model.Lang
import com.garidoctor.core.model.Translations
import kotlin.test.*

class I18nTests {
    private val fake = mapOf(
        "i18n/hi.json" to """{"Engine oil":"इंजन ऑयल","Expires in {0} days":"{0} दिनों में समाप्त","{0} of {1} left":"{1} में से {0} बाकी","Your {0} is at {1}/100.":"आपकी {0} का स्कोर {1}/100 है।"}""",
        "i18n/ar.json" to """{"{0} km over":"تجاوز {0} كم"}""",
    )

    @BeforeTest fun setUp() { Translations.reset(); Translations.loader = { fake[it] } }
    @AfterTest fun tearDown() {
        Translations.reset()
        Translations.loader = { p -> Translations::class.java.classLoader?.getResourceAsStream(p)?.bufferedReader()?.use { it.readText() } }
    }

    @Test fun exactAndFallback() {
        assertEquals("इंजन ऑयल", L10n("Engine oil", "ইঞ্জিন অয়েল").get(Lang.HI))
        assertEquals("ইঞ্জিন অয়েল", L10n("Engine oil", "ইঞ্জিন অয়েল").get(Lang.BN))
        assertEquals("Brakes", Translations.tr(Lang.HI, "Brakes")) // missing → English
        assertEquals("Brakes", Translations.tr(Lang.DE, "Brakes")) // no catalog → English
    }

    @Test fun placeholders() {
        assertEquals("9 दिनों में समाप्त", Translations.tr(Lang.HI, "Expires in 9 days"))
        assertEquals("5 में से 2 बाकी", Translations.tr(Lang.HI, "2 of 5 left"))
        assertEquals("تجاوز 1,250 كم", Translations.tr(Lang.AR, "1,250 km over"))
        assertEquals("आपकी Pulsar का स्कोर 72/100 है।", Translations.format(Lang.HI, "Your {0} is at {1}/100.", "Pulsar", 72))
    }

    @Test fun langCodes() {
        assertEquals(Lang.UR, Lang.of("ur-PK")); assertTrue(Lang.UR.rtl); assertNull(Lang.of("xx"))
        assertEquals(11, Lang.entries.size)
    }
}
