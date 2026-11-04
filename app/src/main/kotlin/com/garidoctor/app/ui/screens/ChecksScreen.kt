@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.CheckEntity
import com.garidoctor.app.data.VoltageEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Good
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.template.GuidedCheck

/** Step-by-step guided visual checks: compare with reference states and tap "looks like this". */
@Composable
fun ChecksScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId) ?: return
    var active by remember { mutableStateOf<GuidedCheck?>(null) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var volts by remember { mutableStateOf("") }

    Screen(active?.title?.tr() ?: t("Visual checks", "চোখে দেখে চেক"), onBack = { if (active != null) { active = null; result = null } else onBack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val chk = active
            if (chk == null) {
                tpl.checks.forEach { c ->
                    val parts = tpl.parts.filter { it.checkId == c.id }
                    val last = st.snapshot.checks.filter { it.vehicleId == id && it.checkId == c.id }.maxByOrNull { it.epochDay }
                    AppSurface(Modifier.fillMaxWidth(), radius = 20.dp, elevation = 5.dp, onClick = { active = c; result = null }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.title.tr(), style = MaterialTheme.typography.titleMedium)
                            Text(parts.joinToString { it.name.tr() }, style = MaterialTheme.typography.bodySmall)
                            last?.let { Text(if (it.passed) t("Last: OK", "শেষ: ঠিক আছে") else t("Last: needs attention", "শেষ: মনোযোগ দরকার"), color = if (it.passed) Good else Bad, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                SectionCard(t("Battery voltage (multimeter)", "ব্যাটারি ভোল্টেজ (মাল্টিমিটার)")) {
                    val ev = v.energyType == EnergyType.ELECTRIC_LEAD_ACID.name || v.energyType == EnergyType.ELECTRIC_LITHIUM.name
                    Text(t("Engine off, resting for 1 hour. 12V battery: 12.6V+ is healthy.", "ইঞ্জিন ১ ঘণ্টা বন্ধ রেখে মাপুন। ১২V ব্যাটারি: ১২.৬V+ ভালো।"), style = MaterialTheme.typography.bodySmall)
                    NumberField(volts, { volts = it }, t("Volts", "ভোল্ট"), decimal = true)
                    Button(enabled = volts.toDoubleOrNull() != null, onClick = {
                        val x = volts.toDouble()
                        // Guess nominal pack voltage for easy-bikes (48V / 60V / 72V) from the reading.
                        val nominal = if (!ev || x < 30) 12.0 else listOf(48.0, 60.0, 72.0).minBy { kotlin.math.abs(it * 1.05 - x) }
                        vm.addVoltage(VoltageEntity(vehicleId = id, volts = x, nominalVolts = nominal, epochDay = today())); volts = ""
                    }) { Text(t("Save reading", "সেভ করুন")) }
                }
            } else {
                chk.steps.forEachIndexed { i, s -> Text("${n(i + 1)}. ${s.tr()}") }
                Gap()
                Text(t("Which one does yours look like?", "আপনারটা কোনটার মতো দেখায়?"), style = MaterialTheme.typography.titleMedium)
                chk.references.forEach { r ->
                    AppSurface(Modifier.fillMaxWidth(), radius = 20.dp, elevation = 5.dp, onClick = {
                        tpl.parts.filter { it.checkId == chk.id }.forEach { p ->
                            vm.addCheck(CheckEntity(vehicleId = id, partId = p.id, checkId = chk.id, passed = r.passes, epochDay = today()))
                        }
                        result = r.passes
                    }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            ReferenceArt(r.image)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.label.tr(), style = MaterialTheme.typography.titleSmall, color = if (r.passes) Good else Bad)
                                Text(r.description.tr(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                result?.let {
                    Text(if (it) t("Saved: looks good ✓", "সেভ হয়েছে: ঠিক আছে ✓") else t("Saved: this lowers your health score until serviced.", "সেভ হয়েছে: সার্ভিস না করা পর্যন্ত হেলথ স্কোর কমবে।"), color = if (it) Good else Bad)
                }
            }
        }
    }
}
