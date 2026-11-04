package com.garidoctor.core

import com.garidoctor.core.health.HealthInputs
import com.garidoctor.core.health.HealthScoreEngine
import com.garidoctor.core.model.Lang
import com.garidoctor.core.model.ServiceEvent
import com.garidoctor.core.model.Translations
import com.garidoctor.core.schedule.MaintenanceScheduler
import com.garidoctor.core.template.TemplateRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

/** Runs against the real bundled catalogs (core/src/main/resources/i18n). */
class CatalogTests {
    private val others = Lang.entries.filter { it != Lang.EN && it != Lang.BN }

    @BeforeTest fun reset() = Translations.reset()

    @Test fun everyLanguageHasACompleteCatalog() {
        val keys = Json.parseToJsonElement(java.io.File("../tools/i18n_keys.json").readText()).let { e -> (e as kotlinx.serialization.json.JsonArray).size }
        others.forEach { l ->
            val text = javaClass.classLoader.getResource("i18n/${l.code}.json")?.readText()
            assertNotNull(text, "missing catalog for ${l.code}")
            assertEquals(keys, Json.parseToJsonElement(text).jsonObject.size, "incomplete catalog ${l.code}")
        }
    }

    @Test fun formattedStringsResolveInEveryLanguage() {
        others.forEach { l ->
            listOf("1,250 km left", "Expires in 9 days.", "Step 2 of 3", "Engine oil is 400 km overdue", "Your Pulsar is at 72/100.").forEach { en ->
                val tr = Translations.tr(l, en)
                assertNotEquals(en, tr, "${l.code} did not translate \"$en\"")
            }
        }
        assertEquals("1,250 किमी बाकी", Translations.tr(Lang.HI, "1,250 km left"))
        assertEquals("ステップ 2 / 3", Translations.tr(Lang.JA, "Step 2 of 3"))
    }

    @Test fun templatesAndScoreTextAreTranslated() {
        val reg = TemplateRegistry.fromClasspath()
        val moto = reg.template("motorcycle")!!
        assertEquals("इंजन ऑयल", moto.part("engine_oil")!!.name.get(Lang.HI))
        assertEquals("机油", moto.part("engine_oil")!!.name.get(Lang.ZH))
        // A real deduction reason built by the engine ("Engine oil is 400 km overdue") comes out in Arabic.
        val oil = moto.part("engine_oil")!!
        val parts = MaintenanceScheduler.statuses(listOf(oil), listOf(ServiceEvent("engine_oil", 0, 100)), 1_900, 110, 0, 100)
        val report = HealthScoreEngine.compute(HealthInputs(parts, todayEpochDay = 110))
        val ar = report.deductions.single().reason.get(Lang.AR)
        assertTrue(ar.contains("400") && !ar.contains("overdue"), ar)
        assertTrue(report.summary("Pulsar", Lang.RU).startsWith("Оценка вашего Pulsar"), report.summary("Pulsar", Lang.RU))
    }
}
