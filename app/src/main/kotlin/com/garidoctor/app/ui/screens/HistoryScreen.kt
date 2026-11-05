@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.ExpenseEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.core.history.ExpenseCategory
import com.garidoctor.core.history.History
import com.garidoctor.core.history.HistoryEntry
import com.garidoctor.core.history.HistoryKind
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class HistoryFilter(val kinds: Set<HistoryKind>?) {
    ALL(null), SERVICE(setOf(HistoryKind.SERVICE)), FUEL(setOf(HistoryKind.FUEL)), BILLS(setOf(HistoryKind.EXPENSE)),
    CHECKS(setOf(HistoryKind.CHECK, HistoryKind.PROBLEM)),
}

/** Everything that happened to one vehicle, newest first, with what it cost. Printable as the passport. */
@Composable
fun HistoryScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, nav: (String) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId)
    val all = remember(st.snapshot, lang) { buildHistory(st.snapshot, id, tpl, lang) }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val months = remember(all, filter) { History.byMonth(History.filter(all, filter.kinds)) }
    val spend = remember(all) { History.spend(all, today()) }
    val passport = rememberPassportAction(vm, id)
    var addMenu by remember { mutableStateOf(false) }
    var addExpense by remember { mutableStateOf(false) }
    var scanBill by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<HistoryEntry?>(null) }
    val locale = remember(lang) { Locale.forLanguageTag(lang.code) }

    Screen(t("History", "ইতিহাস"), onBack, actions = {
        IconButton(onClick = { passport(PassportMode.PRINT) }) { Icon(Icons.Default.Print, t("Print", "প্রিন্ট")) }
        IconButton(onClick = { passport(PassportMode.SHARE) }) { Icon(Icons.Default.PictureAsPdf, t("Share PDF", "পিডিএফ শেয়ার")) }
    }, fab = {
        Box {
            ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(t("Add bill", "বিল যোগ")) })
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text(t("Scan a bill", "বিল স্ক্যান")) }, leadingIcon = { Icon(Icons.Default.Receipt, null) }, onClick = { addMenu = false; scanBill = true })
                DropdownMenuItem(text = { Text(t("Service / parts", "সার্ভিস / পার্টস")) }, leadingIcon = { Icon(Icons.Default.Build, null) }, onClick = { addMenu = false; nav("service/$id") })
                DropdownMenuItem(text = { Text(t("Fuel", "জ্বালানি")) }, leadingIcon = { Icon(Icons.Default.LocalGasStation, null) }, onClick = { addMenu = false; nav("fuel/$id") })
                DropdownMenuItem(text = { Text(t("Other bill (papers, toll, fine…)", "অন্য বিল (কাগজ, টোল, জরিমানা…)")) }, leadingIcon = { Icon(Icons.Default.Payments, null) },
                    onClick = { addMenu = false; addExpense = true })
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, 104.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item {
                AppSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(t("Total spent", "মোট খরচ"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("৳" + n(spend.totalBdt), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                        Text(t("Last 12 months", "গত ১২ মাস") + ": ৳" + n(spend.last12MonthsBdt) + " · " + t("${spend.bills} bills", "${n(spend.bills)}টি বিল"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.SpaceBetween) {
                            SpendCell(t("Service", "সার্ভিস"), spend.serviceBdt)
                            SpendCell(t("Fuel", "জ্বালানি"), spend.fuelBdt)
                            SpendCell(t("Other bills", "অন্যান্য বিল"), spend.otherBdt)
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    HistoryFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(when (f) {
                            HistoryFilter.ALL -> t("All", "সব"); HistoryFilter.SERVICE -> t("Service", "সার্ভিস"); HistoryFilter.FUEL -> t("Fuel", "জ্বালানি")
                            HistoryFilter.BILLS -> t("Other bills", "অন্যান্য বিল"); HistoryFilter.CHECKS -> t("Checks & problems", "চেক ও সমস্যা")
                        }) })
                    }
                }
            }
            if (months.isEmpty()) item {
                EmptyState(Icons.Default.History, t("Nothing here yet", "এখনো কিছু নেই"),
                    t("Bills, fuel, services and checks you add show up here, month by month.", "আপনার যোগ করা বিল, জ্বালানি, সার্ভিস ও চেক এখানে মাস অনুযায়ী দেখাবে।"))
            }
            months.forEach { m ->
                item(key = "h${m.year}-${m.month}") {
                    val name = LocalDate.of(m.year, m.month, 1).month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
                    SectionHeader("$name ${m.year}") { if (m.spentBdt > 0) Text("৳" + n(m.spentBdt), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Spacing.s)) }
                }
                item(key = "m${m.year}-${m.month}") {
                    GroupCard {
                        m.entries.forEachIndexed { i, e ->
                            if (i > 0) RowDivider()
                            val day = LocalDate.ofEpochDay(e.epochDay).format(DateTimeFormatter.ofPattern("d MMM", locale))
                            val sub = listOfNotNull(day, e.odometerKm?.let { n(it) + " " + t("km", "কিমি") }, e.detail).joinToString(" · ")
                            ActionRow(kindIcon(e.kind), e.title, sub, kindTone(e),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (e.receiptPath != null) Icon(Icons.Default.Receipt, t("Receipt", "রসিদ"), Modifier.size(18.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        e.amountBdt?.let { Text("৳" + n(it), style = MaterialTheme.typography.labelLarge) }
                                    }
                                }, onClick = { open = e })
                        }
                    }
                }
            }
        }
    }
    open?.let { e ->
        AlertDialog(onDismissRequest = { open = null }, icon = { Icon(kindIcon(e.kind), null) }, title = { Text(e.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(LocalDate.ofEpochDay(e.epochDay).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
                e.odometerKm?.let { Text(t("Odometer", "ওডোমিটার") + ": " + n(it) + " " + t("km", "কিমি")) }
                e.amountBdt?.let { Text(t("Cost", "খরচ") + ": ৳" + n(it), fontWeight = FontWeight.Medium) }
                e.detail?.let { Text(it) }
                e.receiptPath?.let { path ->
                    TextButton(onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    }) { Icon(Icons.Default.Receipt, null); Spacer(Modifier.width(8.dp)); Text(t("View bill photo", "বিলের ছবি দেখুন")) }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = null }) { Text(t("Close", "বন্ধ")) } },
            dismissButton = {
                val del: (() -> Unit)? = when (e.kind) {
                    HistoryKind.SERVICE -> { { vm.deleteService(e.refId!!) } }
                    HistoryKind.FUEL -> { { vm.deleteFuel(e.refId!!) } }
                    HistoryKind.EXPENSE -> { { vm.deleteExpense(e.refId!!) } }
                    else -> null
                }
                if (del != null && e.refId != null) TextButton(onClick = { del(); open = null }) { Text(t("Delete", "মুছুন"), color = MaterialTheme.colorScheme.error) }
            })
    }
    if (addExpense) AddExpenseDialog(vm, id) { addExpense = false }
    if (scanBill && tpl != null) ReceiptScanFlow(vm, v, tpl) { scanBill = false }
}

@Composable
private fun RowScope.SpendCell(label: String, bdt: Int) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("৳" + n(bdt), style = MaterialTheme.typography.titleMedium)
    }
}

private fun kindIcon(k: HistoryKind): ImageVector = when (k) {
    HistoryKind.SERVICE -> Icons.Default.Build
    HistoryKind.EXPENSE -> Icons.Default.Payments
    HistoryKind.FUEL -> Icons.Default.LocalGasStation
    HistoryKind.PAPER -> Icons.Default.Description
    HistoryKind.ODOMETER -> Icons.Default.Speed
    HistoryKind.CHECK -> Icons.Default.CameraAlt
    HistoryKind.PROBLEM -> Icons.Default.HealthAndSafety
}

private fun kindTone(e: HistoryEntry): Tone = when (e.kind) {
    HistoryKind.PROBLEM -> Tone.WARN
    else -> Tone.NEUTRAL
}

/** A bill that isn't a part service or fuel: paper fees, insurance, tolls, fines, washes… */
@Composable
fun AddExpenseDialog(vm: MainViewModel, vehicleId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.PAPER_FEE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(today()) }
    var note by rememberSaveable { mutableStateOf("") }
    var receipt by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) receipt = pendingPath }
    val bdt = amount.toIntOrNull()
    AlertDialog(onDismissRequest = onClose, title = { Text(t("Add a bill", "বিল যোগ করুন")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ExpenseCategory.entries.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label.tr()) }) }
            }
            NumberField(amount, { amount = it.filter(Char::isDigit) }, t("Amount (৳)", "টাকার পরিমাণ (৳)"))
            DateButton(day, { day = it }, t("Date", "তারিখ"))
            OutlinedTextField(note, { note = it }, label = { Text(t("Note (optional)", "নোট (ঐচ্ছিক)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                val (uri, path) = Photos.newPhoto(ctx, "bill"); pendingPath = path; camera.launch(uri)
            }) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (receipt == null) t("Photo of the bill", "বিলের ছবি") else t("Photo saved ✓", "ছবি সেভ ✓")) }
        }
    }, confirmButton = {
        Button(enabled = bdt != null && bdt > 0, onClick = {
            vm.addExpense(ExpenseEntity(vehicleId = vehicleId, category = category.name, amountBdt = bdt!!, epochDay = day, note = note.trim().ifBlank { null }, receiptPath = receipt))
            onClose()
        }) { Text(t("Save", "সেভ")) }
    }, dismissButton = { TextButton(onClick = onClose) { Text(t("Cancel", "বাতিল")) } })
}
