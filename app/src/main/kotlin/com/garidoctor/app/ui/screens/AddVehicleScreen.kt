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
import com.garidoctor.app.data.ServiceEntity
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.core.model.EnergyType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddVehicleScreen(vm: MainViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var templateId by remember { mutableStateOf<String?>(null) }
    var modelId by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf<EnergyType?>(null) }
    var range by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    // Optional: "last oil change at km" so a used vehicle doesn't start at 100 falsely or 0 unfairly.
    var lastOilKm by remember { mutableStateOf("") }

    Screen(t("Add vehicle", "গাড়ি যোগ করুন"), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Vehicle type", "গাড়ির ধরন"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.registry.all.forEach { tpl ->
                    FilterChip(selected = templateId == tpl.id, onClick = { templateId = tpl.id; modelId = null; energy = tpl.defaultEnergy }, label = { Text(tpl.name.tr()) })
                }
            }
            val tpl = templateId?.let { st.registry.template(it) }
            if (tpl != null) {
                Text(t("Model (sets service intervals)", "মডেল (সার্ভিস সময়সূচি ঠিক করে)"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = modelId == null, onClick = { modelId = null; energy = tpl.defaultEnergy }, label = { Text(t("Other / generic", "অন্যান্য")) })
                    tpl.models.forEach { m ->
                        FilterChip(selected = modelId == m.id, onClick = {
                            modelId = m.id; energy = m.energyType ?: tpl.defaultEnergy
                            if (nickname.isBlank()) nickname = m.model
                        }, label = { Text("${m.brand} ${m.model}") })
                    }
                }
                Text(t("Fuel / energy", "জ্বালানি / শক্তি"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnergyType.entries.forEach { e -> FilterChip(selected = energy == e, onClick = { energy = e }, label = { Text(energyLabel(e)) }) }
                }
                OutlinedTextField(nickname, { nickname = it }, label = { Text(t("Nickname (e.g. My Pulsar)", "নাম (যেমন: আমার পালসার)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reg, { reg = it }, label = { Text(t("Registration no. (optional)", "রেজিস্ট্রেশন নম্বর (ঐচ্ছিক)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(odo, { odo = it }, t("Current odometer (km)", "বর্তমান ওডোমিটার (কিমি)"))
                NumberField(lastOilKm, { lastOilKm = it }, t("Last engine-oil change at km (optional)", "শেষ ইঞ্জিন অয়েল বদল কত কিমিতে (ঐচ্ছিক)"))
                if (energy == EnergyType.ELECTRIC_LEAD_ACID || energy == EnergyType.ELECTRIC_LITHIUM)
                    NumberField(range, { range = it }, t("Range on a full charge when new (km)", "নতুন অবস্থায় এক চার্জে রেঞ্জ (কিমি)"))
                if (tpl.fleetFriendly) OutlinedTextField(driver, { driver = it }, label = { Text(t("Driver name (fleet, optional)", "চালকের নাম (ফ্লিট, ঐচ্ছিক)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                val km = odo.toIntOrNull()
                Button(enabled = km != null && nickname.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                    val v = VehicleEntity(templateId = tpl.id, modelPresetId = modelId, nickname = nickname.trim(), registrationNo = reg.trim().ifBlank { null },
                        odometerKm = km!!, baselineKm = km, energyType = (energy ?: tpl.defaultEnergy).name, createdAtEpochDay = today(),
                        driverName = driver.trim().ifBlank { null }, ratedRangeKm = range.toDoubleOrNull())
                    vm.addVehicle(v) { id ->
                        lastOilKm.toIntOrNull()?.takeIf { it <= km && tpl.part("engine_oil") != null }?.let {
                            vm.addService(ServiceEntity(vehicleId = id, partId = "engine_oil", odometerKm = it, epochDay = today(), note = "initial"))
                        }
                        onCreated(id)
                    }
                }) { Text(t("Save & run checkup", "সেভ করে চেকআপ করুন")) }
                Text(t("Tip: parts you haven't logged are counted from today's odometer. Log past services on the next screen for an accurate score.",
                    "টিপ: যে পার্টসের সার্ভিস লগ নেই সেগুলো আজকের ওডোমিটার থেকে গণনা হবে। সঠিক স্কোরের জন্য পরের স্ক্রিনে আগের সার্ভিসগুলো লগ করুন।"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun energyLabel(e: EnergyType) = when (e) {
    EnergyType.PETROL -> t("Petrol", "পেট্রোল")
    EnergyType.OCTANE -> t("Octane", "অকটেন")
    EnergyType.DIESEL -> t("Diesel", "ডিজেল")
    EnergyType.CNG -> t("CNG", "সিএনজি")
    EnergyType.PETROL_CNG -> t("Petrol + CNG", "পেট্রোল + সিএনজি")
    EnergyType.ELECTRIC_LEAD_ACID -> t("Electric (lead-acid)", "ইলেকট্রিক (লেড-অ্যাসিড)")
    EnergyType.ELECTRIC_LITHIUM -> t("Electric (lithium)", "ইলেকট্রিক (লিথিয়াম)")
}
