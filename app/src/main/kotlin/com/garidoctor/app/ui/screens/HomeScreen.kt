@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.app.ui.theme.tone
import com.garidoctor.core.Checkup
import com.garidoctor.core.VehicleRecords
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.schedule.DueState
import com.garidoctor.core.symptom.Urgency

/** One actionable item on the home "Needs attention" list. Sorted by [severity], highest first. */
private sealed class Task(val vehicle: VehicleEntity, val severity: Int, val route: String) {
    class Service(v: VehicleEntity, val partName: String, val kmOver: Int?, val daysOver: Long?, overdue: Boolean) :
        Task(v, if (overdue) 80 else 40, "service/${v.id}")
    class Paper(v: VehicleEntity, val docName: String, val daysLeft: Long) :
        Task(v, if (daysLeft < 0) 90 else 60 - daysLeft.toInt(), "docs/${v.id}")
    class Problem(v: VehicleEntity, val summary: String, val urgency: Urgency) :
        Task(v, when (urgency) { Urgency.STOP_NOW -> 100; Urgency.THIS_WEEK -> 70; Urgency.SAFE -> 20 }, "vehicle/${v.id}")
}

@Composable
private fun buildTasks(vehicles: List<VehicleEntity>, checkups: Map<Long, Checkup>, records: Map<Long, VehicleRecords>,
                       symptoms: List<com.garidoctor.app.data.SymptomEntity>): List<Task> {
    val out = mutableListOf<Task>()
    val day = today()
    vehicles.forEach { v ->
        checkups[v.id]?.parts?.filter { it.state != DueState.OK }?.forEach { s ->
            out += Task.Service(v, s.part.name.tr(), s.kmRemaining?.let { -it }?.takeIf { s.drivenBy == "km" },
                s.daysRemaining?.let { -it }?.takeIf { s.drivenBy == "time" }, s.state == DueState.OVERDUE)
        }
        records[v.id]?.documents?.forEach { d ->
            val st = DocumentExpiry.status(d, day)
            if (st.state != ExpiryState.VALID) out += Task.Paper(v, d.type.label.tr(), st.daysLeft)
        }
        symptoms.filter { it.vehicleId == v.id && !it.resolved }.forEach { s ->
            out += Task.Problem(v, s.summary, runCatching { Urgency.valueOf(s.urgency) }.getOrDefault(Urgency.THIS_WEEK))
        }
    }
    return out.sortedByDescending { it.severity }
}

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onNav: (String) -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val st by vm.state.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }
    var showAllTasks by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    val tasks = buildTasks(st.snapshot.vehicles, st.checkups, st.snapshot.records, st.snapshot.symptoms)

    Screen(
        title = t("GariDoctor", "গাড়িডাক্তার"), onBack = null, large = true, bottomBar = bottomBar,
        fab = {
            if (st.snapshot.vehicles.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { if (vm.canAddVehicle()) onAdd() else showPro = true }, expanded = expandedFab,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text(t("Add vehicle", "গাড়ি যোগ করুন")) },
            )
        },
    ) { pad ->
        if (st.loaded && st.snapshot.vehicles.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Default.HealthAndSafety,
                    t("A health doctor for every vehicle", "প্রতিটি গাড়ির জন্য একজন হেলথ ডাক্তার"),
                    t("Add your bike, car, CNG, easy-bike, microbus, pickup or truck. Get a 0–100 health score, service reminders and paper-expiry alerts. Everything stays on your phone.",
                        "আপনার বাইক, কার, সিএনজি, ইজি বাইক, মাইক্রোবাস, পিকআপ বা ট্রাক যোগ করুন। পান ০–১০০ হেলথ স্কোর, সার্ভিস রিমাইন্ডার ও কাগজের মেয়াদের সতর্কতা। সব তথ্য আপনার ফোনেই থাকে।"),
                ) {
                    Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Add your first vehicle", "প্রথম গাড়ি যোগ করুন"))
                    }
                }
            }
            return@Screen
        }
        LazyColumn(
            state = list, modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item {
                val open = tasks.size
                Text(
                    if (open == 0) t("All vehicles are in good shape.", "সব গাড়ি ভালো অবস্থায় আছে।")
                    else t("$open thing(s) need your attention", "${n(open)}টি বিষয়ে মনোযোগ দরকার"),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(st.snapshot.vehicles, key = { it.id }) { v ->
                VehicleCard(v, st.checkups[v.id], st.snapshot.records[v.id], st.registry.template(v.templateId)?.let { tpl ->
                    listOfNotNull(tpl.name.tr(), tpl.model(v.modelPresetId)?.let { "${it.brand} ${it.model}" }).joinToString(" · ")
                } ?: "", onOpen = { onOpen(v.id) }, onOdometer = { onNav("odometer/${v.id}") })
            }
            if (tasks.isNotEmpty()) {
                item { SectionHeader(t("Needs attention", "মনোযোগ দরকার")) }
                item {
                    AppSurface(Modifier.fillMaxWidth().animateContentSize()) { Column {
                        val shown = if (showAllTasks) tasks else tasks.take(5)
                        shown.forEachIndexed { i, task ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            TaskRow(task, multi = st.snapshot.vehicles.size > 1) { onNav(task.route) }
                        }
                        if (tasks.size > 5) TextButton(onClick = { showAllTasks = !showAllTasks }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                            Text(if (showAllTasks) t("Show less", "কম দেখান") else t("Show all ${tasks.size}", "সবগুলো দেখুন (${n(tasks.size)})"))
                        }
                    } }
                }
            }
            if (!pro) item { Box(Modifier.padding(top = Spacing.s)) { com.garidoctor.app.ads.BannerAd() } }
        }
    }
    if (showPro) ProDialog(vm, t("The free version supports one vehicle. Unlock Pro for multiple vehicles, PDF passport, live OBD graphs and fleet mode.",
        "ফ্রি ভার্সনে একটি গাড়ি। একাধিক গাড়ি, পিডিএফ পাসপোর্ট, লাইভ OBD গ্রাফ ও ফ্লিট মোডের জন্য প্রো আনলক করুন।")) { showPro = false }
}

@Composable
private fun VehicleCard(v: VehicleEntity, c: Checkup?, rec: VehicleRecords?, subtitle: String, onOpen: () -> Unit, onOdometer: () -> Unit) {
    val score = c?.health?.score ?: 100
    val overdue = c?.parts?.count { it.state == DueState.OVERDUE } ?: 0
    val papers = rec?.documents?.count { DocumentExpiry.status(it, today()).state != ExpiryState.VALID } ?: 0
    AppSurface(Modifier.fillMaxWidth(), radius = 28.dp, elevation = 8.dp, onClick = onOpen, contentDescription = v.nickname) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score, 84.dp, 9.dp)
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(v.nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    c?.health?.band?.let { StatusPill(it.label.tr(), it.tone(), Modifier.padding(top = 6.dp)) }
                }
            }
            c?.health?.deductions?.firstOrNull()?.let {
                Gap(Spacing.m)
                Text(it.reason.tr(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Gap(Spacing.m)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AssistChip(onClick = onOdometer, label = { Text("${n(v.odometerKm)} ${t("km", "কিমি")}") },
                    leadingIcon = { Icon(Icons.Outlined.Speed, null, Modifier.size(AssistChipDefaults.IconSize)) })
                if (overdue > 0) StatusPill(t("$overdue overdue", "${n(overdue)}টি বাকি"), Tone.BAD, Modifier.align(Alignment.CenterVertically))
                if (papers > 0) StatusPill(t("$papers paper(s)", "${n(papers)}টি কাগজ"), Tone.WARN, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, multi: Boolean, onClick: () -> Unit) {
    val prefix = if (multi) "${task.vehicle.nickname} · " else ""
    val (icon: ImageVector, title: String, sub: String, tone: Tone) = when (task) {
        is Task.Service -> Quad(Icons.Default.Build, task.partName, prefix + when {
            task.kmOver != null && task.kmOver > 0 -> t("${task.kmOver} km overdue", "${n(task.kmOver)} কিমি পেরিয়েছে")
            task.daysOver != null && task.daysOver > 0 -> t("${task.daysOver} days overdue", "${n(task.daysOver)} দিন পেরিয়েছে")
            task.kmOver != null -> t("due in ${-task.kmOver} km", "${n(-task.kmOver)} কিমির মধ্যে")
            task.daysOver != null -> t("due in ${-task.daysOver} days", "${n(-task.daysOver)} দিনের মধ্যে")
            else -> t("due soon", "শীঘ্রই")
        }, if (task.severity >= 80) Tone.BAD else Tone.WARN)
        is Task.Paper -> Quad(Icons.Default.Description, task.docName, prefix +
            if (task.daysLeft < 0) t("expired ${-task.daysLeft} days ago", "${n(-task.daysLeft)} দিন আগে মেয়াদ শেষ") else t("expires in ${task.daysLeft} days", "${n(task.daysLeft)} দিনে মেয়াদ শেষ"),
            if (task.daysLeft < 0 || task.daysLeft <= 7) Tone.BAD else Tone.WARN)
        is Task.Problem -> Quad(Icons.Default.HealthAndSafety, task.summary, prefix + task.urgency.label.tr(), task.urgency.tone())
    }
    ActionRow(icon, title, sub, tone, onClick = onClick)
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
/** Pro unlock: promo/bundle code redemption (Play Billing hooks in here for paid unlocks). */
@Composable
fun ProDialog(vm: MainViewModel, reason: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val price by vm.proPrice.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    LaunchedEffect(pro) { if (pro) onDismiss() }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t("GariDoctor Pro", "গাড়িডাক্তার প্রো")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reason)
                price?.let { p ->
                    Button(onClick = { ctx.findActivity()?.let { vm.buyPro(it) } }, modifier = Modifier.fillMaxWidth()) { Text(t("Buy Pro · $p (one-time)", "প্রো কিনুন · $p (এককালীন)")) }
                }
                Text(t("Have a code from a Facebook group, garage or dongle bundle?", "ফেসবুক গ্রুপ, গ্যারেজ বা ডঙ্গল বান্ডেলের কোড আছে?"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(code, { code = it; error = false }, label = { Text("GD-XXXX-XXXX-XXXX") }, isError = error, singleLine = true)
                if (error) Text(t("Invalid code", "কোডটি সঠিক নয়"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { if (vm.redeem(code)) onDismiss() else error = true }) { Text(t("Redeem", "রিডিম")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Later", "পরে")) } })
}
