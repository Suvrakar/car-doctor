@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.passport.PassportPdf
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.app.ui.theme.tone
import com.garidoctor.app.data.today
import com.garidoctor.core.VehicleDoctor
import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.health.DeductionKind
import com.garidoctor.core.passport.VehiclePassport
import com.garidoctor.core.pro.ProFeature
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.schedule.PartStatus
import com.garidoctor.core.symptom.Urgency

private data class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun VehicleScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, nav: (String) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: run { if (st.loaded) LaunchedEffect(Unit) { onBack() }; return }
    val tpl = st.registry.template(v.templateId) ?: return
    val c = st.checkups[id] ?: return
    val rec = st.snapshot.records[id] ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    var showProFull by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showHealthy by remember { mutableStateOf(false) }
    val electric = VehicleDoctor.isElectric(rec.vehicle.energyType)
    val makePassport: () -> Unit = {
        val p = VehiclePassport.build(rec.vehicle, tpl.name.get(lang), tpl.model(v.modelPresetId)?.let { "${it.brand} ${it.model}" },
            c.health, rec.services, rec.documents, c.fuel, today())
        runCatching { PassportPdf.write(ctx, p, { pid -> tpl.part(pid)?.name?.get(lang) ?: pid }, lang) }
            .onSuccess { PassportPdf.share(ctx, it) }
            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
    }
    val passport: () -> Unit = { if (vm.allowed(ProFeature.PASSPORT_PDF)) { makePassport() } else { showPro = true } }

    val actions = buildList {
        add(QuickAction(Icons.Default.Build, t("Log service", "সার্ভিস লগ")) { nav("service/$id") })
        add(QuickAction(if (electric) Icons.Default.BatteryChargingFull else Icons.Default.LocalGasStation, if (electric) t("Charge", "চার্জ") else t("Fuel", "জ্বালানি")) { nav("fuel/$id") })
        add(QuickAction(Icons.Default.HealthAndSafety, t("Doctor", "ডাক্তার")) { nav("symptoms/$id") })
        add(QuickAction(Icons.Default.Description, t("Papers", "কাগজ")) { nav("docs/$id") })
        add(QuickAction(Icons.Default.CameraAlt, t("Checks", "চেক")) { nav("checks/$id") })
        add(QuickAction(Icons.Default.Warning, t("Lights", "লাইট")) { nav("lights/$id") })
        if (tpl.supportsObd) add(QuickAction(Icons.Default.Bluetooth, "OBD") { nav("obd/$id") })
        if (electric) add(QuickAction(Icons.Default.BatteryStd, t("Battery", "ব্যাটারি")) { nav("battery/$id") })
        add(QuickAction(Icons.Default.PictureAsPdf, t("Passport", "পাসপোর্ট"), passport))
    }
    val overdue = c.parts.filter { it.state == DueState.OVERDUE }
    val soon = c.parts.filter { it.state == DueState.DUE_SOON }
    val healthy = c.parts.filter { it.state == DueState.OK }
    val activeSymptoms = st.snapshot.symptoms.filter { it.vehicleId == id && !it.resolved }

    Screen(v.nickname, onBack, actions = {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("More", "আরও")) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(t("Edit vehicle", "গাড়ির তথ্য সম্পাদনা")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; editing = true })
            DropdownMenuItem(text = { Text(t("Share Vehicle Passport", "ভেহিকল পাসপোর্ট শেয়ার")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { menu = false; passport() })
            DropdownMenuItem(text = { Text(t("Delete vehicle", "গাড়ি মুছুন")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; confirmDelete = true })
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // ── Hero ──
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScoreRing(c.health.score, 148.dp, 14.dp)
                        Gap(Spacing.m)
                        StatusPill(c.health.band.label.tr(), c.health.band.tone())
                        Gap(Spacing.s)
                        Text("${tpl.name.tr()}${tpl.model(v.modelPresetId)?.let { " · ${it.brand} ${it.model}" } ?: ""}${v.registrationNo?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Gap(Spacing.s)
                        AssistChip(onClick = { nav("odometer/$id") }, label = { Text("${n(v.odometerKm)} ${t("km · update", "কিমি · আপডেট")}") },
                            leadingIcon = { Icon(Icons.Outlined.Speed, null, Modifier.size(AssistChipDefaults.IconSize)) })
                    }
                }
            }
            // ── Quick actions ──
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    items(actions, key = { it.label }) { a -> ActionTile(a) }
                }
            }
            // ── Why this score ──
            if (c.health.deductions.isNotEmpty()) {
                item { SectionHeader(t("What's lowering the score", "স্কোর কেন কমেছে")) }
                item {
                    GroupCard {
                        c.health.deductions.forEachIndexed { i, d ->
                            if (i > 0) RowDivider()
                            val (icon, route) = when (d.kind) {
                                DeductionKind.SERVICE_OVERDUE, DeductionKind.SERVICE_DUE_SOON -> Icons.Default.Build to "service/$id"
                                DeductionKind.FAILED_CHECK -> Icons.Default.CameraAlt to "checks/$id"
                                DeductionKind.SYMPTOM -> Icons.Default.HealthAndSafety to "symptoms/$id"
                                DeductionKind.LOW_VOLTAGE -> Icons.Default.BatteryAlert to "checks/$id"
                                DeductionKind.FAULT_CODE -> Icons.Default.Bluetooth to "obd/$id"
                                DeductionKind.EFFICIENCY_DROP -> Icons.Default.LocalGasStation to "fuel/$id"
                                DeductionKind.DOCUMENT -> Icons.Default.Description to "docs/$id"
                            }
                            ActionRow(icon, d.reason.tr(), tone = if (d.points >= 10) Tone.BAD else Tone.WARN, trailing = { PointsPill(d.points) }, onClick = { nav(route) })
                        }
                    }
                }
            } else item {
                StatusBanner(Icons.Default.Verified, Tone.GOOD, t("Everything looks good", "সবকিছু ঠিক আছে"),
                    t("No overdue service, problems or expiring papers.", "কোনো বাকি সার্ভিস, সমস্যা বা মেয়াদোত্তীর্ণ কাগজ নেই।"))
            }
            // ── Mileage anomaly ──
            FuelAnalyzer.alert(c.fuel.trend, electric)?.let { a ->
                item {
                    StatusBanner(Icons.Default.TrendingDown, Tone.WARN, a.title.tr(),
                        t("Common causes: ", "সাধারণ কারণ: ") + a.causes.take(3).joinToString(" · ") { it.tr() }) { nav("fuel/$id") }
                }
            }
            // ── Open problems ──
            if (activeSymptoms.isNotEmpty()) {
                item { SectionHeader(t("Open problems", "চলমান সমস্যা")) }
                item {
                    GroupCard {
                        activeSymptoms.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            val u = runCatching { Urgency.valueOf(s.urgency) }.getOrDefault(Urgency.THIS_WEEK)
                            ActionRow(Icons.Default.HealthAndSafety, s.summary, u.label.tr(), u.tone(),
                                trailing = { TextButton(onClick = { vm.resolveSymptom(s.id) }) { Text(t("Fixed", "ঠিক হয়েছে")) } })
                        }
                    }
                }
            }
            // ── Parts ──
            if (overdue.isNotEmpty()) { item { SectionHeader(t("Due now", "এখনই করতে হবে")) }; item { PartsGroup(overdue, vm::sponsorTip) { nav("service/$id") } } }
            if (soon.isNotEmpty()) { item { SectionHeader(t("Coming up", "শীঘ্রই আসছে")) }; item { PartsGroup(soon, vm::sponsorTip) { nav("service/$id") } } }
            if (healthy.isNotEmpty()) {
                item {
                    SectionHeader(t("Healthy (${healthy.size})", "ভালো আছে (${n(healthy.size)})")) {
                        TextButton(onClick = { showHealthy = !showHealthy }) { Text(if (showHealthy) t("Hide", "লুকান") else t("Show", "দেখুন")) }
                    }
                }
                item { AnimatedVisibility(showHealthy) { PartsGroup(healthy) { nav("service/$id") } } }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text(t("Delete ${v.nickname}?", "${v.nickname} মুছবেন?")) },
        text = { Text(t("All logs and paper records for this vehicle will be deleted. This can't be undone.", "এই গাড়ির সব লগ ও কাগজের তথ্য মুছে যাবে। এটি ফেরানো যাবে না।")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteVehicle(v); onBack() }) { Text(t("Delete", "মুছুন"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel", "বাতিল")) } })
    if (showPro) AlertDialog(onDismissRequest = { showPro = false },
        icon = { Icon(Icons.Default.PictureAsPdf, null) },
        title = { Text(t("Vehicle Passport", "ভেহিকল পাসপোর্ট")) },
        text = { Text(t("A PDF with the health score, full service history and a QR code. Share it when selling to get a better price.",
            "হেলথ স্কোর, পুরো সার্ভিস ইতিহাস ও QR কোডসহ একটি পিডিএফ। বিক্রির সময় শেয়ার করে ভালো দাম পান।")) },
        confirmButton = { Button(onClick = { showPro = false; showProFull = true }) { Text(t("Get Pro", "প্রো নিন")) } },
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
private fun ActionTile(a: QuickAction) {
    Surface(onClick = a.onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.width(84.dp).height(84.dp)) {
        Column(Modifier.padding(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(a.icon, contentDescription = null)
            Gap(6.dp)
            Text(a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = Spacing.xs), content = content)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** Full-width tinted banner (good news, warnings) with optional tap target. */
@Composable
fun StatusBanner(icon: ImageVector, tone: Tone, title: String, body: String, onClick: (() -> Unit)? = null) {
    val container = when (tone) { Tone.GOOD -> com.garidoctor.app.ui.theme.Status.colors.goodContainer; Tone.WARN -> com.garidoctor.app.ui.theme.Status.colors.warnContainer; Tone.BAD -> com.garidoctor.app.ui.theme.Status.colors.badContainer; Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh }
    val onContainer = when (tone) { Tone.GOOD -> com.garidoctor.app.ui.theme.Status.colors.onGoodContainer; Tone.WARN -> com.garidoctor.app.ui.theme.Status.colors.onWarnContainer; Tone.BAD -> com.garidoctor.app.ui.theme.Status.colors.onBadContainer; Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface }
    val inner: @Composable () -> Unit = {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = onContainer)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = onContainer)
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) { inner() }
    else Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) { inner() }
}

@Composable
private fun PartsGroup(parts: List<PartStatus>, sponsor: (String) -> Pair<String, String>? = { null }, onClick: () -> Unit) {
    GroupCard {
        parts.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = MaterialTheme.colorScheme.outlineVariant)
            val tone = when (s.state) { DueState.OVERDUE -> Tone.BAD; DueState.DUE_SOON -> Tone.WARN; DueState.OK -> Tone.GOOD }
            val remaining = buildList {
                s.kmRemaining?.let { add(if (it >= 0) t("${"%,d".format(it)} km left", "${n(it)} কিমি বাকি") else t("${"%,d".format(-it)} km over", "${n(-it)} কিমি পেরিয়েছে")) }
                s.daysRemaining?.let { add(if (it >= 0) t("$it days left", "${n(it)} দিন বাকি") else t("${-it} days over", "${n(-it)} দিন পেরিয়েছে")) }
            }.joinToString(" · ")
            Column(Modifier.fillMaxWidth().clickableItem(onClick).padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.part.name.tr(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.state != DueState.OK && s.part.costMinBdt != null)
                        Text("৳${n(s.part.costMinBdt!!)}–${n(s.part.costMaxBdt ?: 0)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UsageBar(s.ratio.toFloat(), tone)
                Text(remaining, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.state != DueState.OK) sponsor(s.part.category.name)?.let { (brand, text) ->
                    Text(t("Sponsored", "স্পন্সরড") + " · $brand: $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
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
