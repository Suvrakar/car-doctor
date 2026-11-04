@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.passport.PassportPdf
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Good
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.app.ui.theme.bandColor
import com.garidoctor.app.data.today
import com.garidoctor.core.VehicleDoctor
import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.passport.VehiclePassport
import com.garidoctor.core.pro.ProFeature
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.symptom.Urgency

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, nav: (String) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: run { if (st.loaded) LaunchedEffect(Unit) { onBack() }; return }
    val tpl = st.registry.template(v.templateId) ?: return
    val c = st.checkups[id] ?: return
    val rec = st.snapshot.records[id]!!
    var confirmDelete by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    var showProFull by remember { mutableStateOf(false) }
    val electric = VehicleDoctor.isElectric(rec.vehicle.energyType)
    var editing by remember { mutableStateOf(false) }
    val makePassport: () -> Unit = {
        val p = VehiclePassport.build(rec.vehicle, tpl.name.get(lang), tpl.model(v.modelPresetId)?.let { "${it.brand} ${it.model}" },
            c.health, rec.services, rec.documents, c.fuel, today())
        runCatching { PassportPdf.write(ctx, p, { pid -> tpl.part(pid)?.name?.get(lang) ?: pid }, lang) }
            .onSuccess { PassportPdf.share(ctx, it) }
            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
    }

    Screen(v.nickname, onBack, actions = {
        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, t("Edit", "সম্পাদনা")) }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, t("Delete", "মুছুন")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(c.health.score, 112.dp, 12.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(c.health.band.label.tr(), color = bandColor(c.health.band), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${tpl.name.tr()}${tpl.model(v.modelPresetId)?.let { " · ${it.brand} ${it.model}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            Text("${n(v.odometerKm)} ${t("km", "কিমি")}", style = MaterialTheme.typography.bodyMedium)
                            v.registrationNo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text(c.health.summary(v.nickname, lang), style = MaterialTheme.typography.bodyMedium)
                    c.health.deductions.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("−${n(d.points)}", color = Bad, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                            Text(d.reason.tr(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action(Icons.Default.Build, t("Log service", "সার্ভিস লগ")) { nav("service/$id") }
                    Action(Icons.Default.Speed, t("Odometer", "ওডোমিটার")) { nav("odometer/$id") }
                    Action(if (electric) Icons.Default.BatteryChargingFull else Icons.Default.LocalGasStation, if (electric) t("Charge log", "চার্জ লগ") else t("Fuel / CNG", "জ্বালানি / সিএনজি")) { nav("fuel/$id") }
                    Action(Icons.Default.Description, t("Papers", "কাগজপত্র")) { nav("docs/$id") }
                    Action(Icons.Default.HealthAndSafety, t("Symptom doctor", "সমস্যা নির্ণয়")) { nav("symptoms/$id") }
                    Action(Icons.Default.CameraAlt, t("Visual checks", "চোখে দেখে চেক")) { nav("checks/$id") }
                    Action(Icons.Default.Warning, t("Warning lights", "ওয়ার্নিং লাইট")) { nav("lights/$id") }
                    if (tpl.supportsObd) Action(Icons.Default.Bluetooth, t("OBD scan", "OBD স্ক্যান")) { nav("obd/$id") }
                    if (electric) Action(Icons.Default.BatteryStd, t("Battery health", "ব্যাটারি হেলথ")) { nav("battery/$id") }
                    Action(Icons.Default.PictureAsPdf, t("Passport PDF", "পাসপোর্ট পিডিএফ")) {
                        if (vm.allowed(ProFeature.PASSPORT_PDF)) { makePassport() } else { showPro = true }
                    }
                }
            }
            FuelAnalyzer.alert(c.fuel.trend, electric)?.let { a ->
                item {
                    SectionCard(a.title.tr()) {
                        Text(t("Common causes:", "সাধারণ কারণ:"), style = MaterialTheme.typography.labelLarge)
                        a.causes.forEach { Text("• ${it.tr()}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            val activeSymptoms = st.snapshot.symptoms.filter { it.vehicleId == id && !it.resolved }
            if (activeSymptoms.isNotEmpty()) item {
                SectionCard(t("Open problems", "চলমান সমস্যা")) {
                    activeSymptoms.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                runCatching { Urgency.valueOf(s.urgency) }.getOrNull()?.let { UrgencyChip(it) }
                                Text(s.summary, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.resolveSymptom(s.id) }) { Text(t("Fixed", "ঠিক হয়েছে")) }
                        }
                    }
                }
            }
            item {
                SectionCard(t("Parts", "পার্টস")) {
                    c.parts.forEach { s ->
                        val color = when (s.state) { DueState.OVERDUE -> Bad; DueState.DUE_SOON -> Warn; DueState.OK -> Good }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Dot(color); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.part.name.tr(), style = MaterialTheme.typography.bodyMedium)
                                val parts = buildList {
                                    s.kmRemaining?.let { add(if (it >= 0) t("${"%,d".format(it)} km left", "${n(it)} কিমি বাকি") else t("${"%,d".format(-it)} km over", "${n(-it)} কিমি পেরিয়েছে")) }
                                    s.daysRemaining?.let { add(if (it >= 0) t("$it days left", "${n(it)} দিন বাকি") else t("${-it} days over", "${n(-it)} দিন পেরিয়েছে")) }
                                }
                                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = color)
                                if (s.state != DueState.OK && s.part.costMinBdt != null)
                                    Text(t("Approx. ৳${s.part.costMinBdt}–${s.part.costMaxBdt}", "আনুমানিক ৳${n(s.part.costMinBdt!!)}–${n(s.part.costMaxBdt ?: 0)}"), style = MaterialTheme.typography.labelSmall)
                                if (s.state != DueState.OK) vm.sponsorTip(s.part.category.name)?.let { (brand, text) ->
                                    Text(t("Sponsored", "স্পন্সরড") + " · $brand: $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(t("Delete ${v.nickname}?", "${v.nickname} মুছবেন?")) },
        text = { Text(t("All logs, papers and photos records for this vehicle will be deleted.", "এই গাড়ির সব লগ ও কাগজের তথ্য মুছে যাবে।")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteVehicle(v); onBack() }) { Text(t("Delete", "মুছুন"), color = Bad) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel", "বাতিল")) } })
    if (showPro) AlertDialog(onDismissRequest = { showPro = false },
        title = { Text(t("Vehicle Passport", "ভেহিকল পাসপোর্ট")) },
        text = { Text(t("The Passport PDF is a Pro feature. Share it when selling to get a better price. Or watch a short ad for one free passport.",
            "পাসপোর্ট পিডিএফ প্রো ফিচার। বিক্রির সময় শেয়ার করে ভালো দাম পান। অথবা একটি ছোট বিজ্ঞাপন দেখে একবার ফ্রি পাসপোর্ট নিন।")) },
        confirmButton = { TextButton(onClick = { showPro = false; showProFull = true }) { Text(t("Get Pro", "প্রো নিন")) } },
        dismissButton = {
            TextButton(onClick = {
                showPro = false
                val shown = ctx.findActivity()?.let { a -> com.garidoctor.app.ads.Ads.showRewarded(a) { makePassport() } } ?: false
                if (!shown) Toast.makeText(ctx, t2(lang, "Ad not ready yet, try again in a moment", "বিজ্ঞাপন এখনো প্রস্তুত নয়, একটু পরে চেষ্টা করুন"), Toast.LENGTH_SHORT).show()
            }) { Text(t("Watch ad · 1 free", "বিজ্ঞাপন দেখুন · ১টি ফ্রি")) }
        })
    if (showProFull) ProDialog(vm, t("Unlock Pro: multiple vehicles, unlimited passports, live OBD graphs, fleet mode, no ads.", "প্রো আনলক: একাধিক গাড়ি, আনলিমিটেড পাসপোর্ট, লাইভ OBD গ্রাফ, ফ্লিট মোড, বিজ্ঞাপন নেই।")) { showProFull = false }
    if (editing) EditVehicleDialog(vm, v) { editing = false }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.width(104.dp).height(84.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Gap(4.dp)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

private fun t2(lang: com.garidoctor.core.model.Lang, en: String, bn: String) = if (lang == com.garidoctor.core.model.Lang.BN) bn else en

@Composable
private fun EditVehicleDialog(vm: MainViewModel, v: com.garidoctor.app.data.VehicleEntity, onDismiss: () -> Unit) {
    var nickname by remember { mutableStateOf(v.nickname) }
    var reg by remember { mutableStateOf(v.registrationNo ?: "") }
    var driver by remember { mutableStateOf(v.driverName ?: "") }
    var driverPhone by remember { mutableStateOf(v.driverPhone ?: "") }
    var range by remember { mutableStateOf(v.ratedRangeKm?.toInt()?.toString() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Edit vehicle", "গাড়ির তথ্য সম্পাদনা")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nickname, { nickname = it }, label = { Text(t("Nickname", "নাম")) }, singleLine = true)
            OutlinedTextField(reg, { reg = it }, label = { Text(t("Registration no.", "রেজিস্ট্রেশন নম্বর")) }, singleLine = true)
            OutlinedTextField(driver, { driver = it }, label = { Text(t("Driver name", "চালকের নাম")) }, singleLine = true)
            NumberField(driverPhone, { driverPhone = it }, t("Driver phone", "চালকের ফোন"))
            if (VehicleDoctor.isElectric(v.toModelEnergy())) NumberField(range, { range = it }, t("Rated range per charge (km)", "এক চার্জে রেঞ্জ (কিমি)"))
        }
    }, confirmButton = {
        TextButton(enabled = nickname.isNotBlank(), onClick = {
            vm.updateVehicle(v.copy(nickname = nickname.trim(), registrationNo = reg.trim().ifBlank { null }, driverName = driver.trim().ifBlank { null },
                driverPhone = driverPhone.ifBlank { null }, ratedRangeKm = range.toDoubleOrNull() ?: v.ratedRangeKm))
            onDismiss()
        }) { Text(t("Save", "সেভ")) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "বাতিল")) } })
}

private fun com.garidoctor.app.data.VehicleEntity.toModelEnergy() =
    runCatching { com.garidoctor.core.model.EnergyType.valueOf(energyType) }.getOrDefault(com.garidoctor.core.model.EnergyType.OCTANE)
