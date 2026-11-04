@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.ServiceEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.core.schedule.DueState
import java.time.LocalDate

@Composable
fun ServiceScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId) ?: return
    val c = st.checkups[id] ?: return
    var logPart by remember { mutableStateOf<String?>(null) }
    val history = st.snapshot.services.filter { it.vehicleId == id }

    Screen(t("Service log", "সার্ভিস লগ"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(t("Tap a part to mark it serviced", "সার্ভিস করা পার্টসে ট্যাপ করুন"), style = MaterialTheme.typography.titleSmall) }
            items(c.parts, key = { it.part.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.part.name.tr()) },
                    supportingContent = {
                        val every = listOfNotNull(s.part.intervalKm?.let { t("every ${"%,d".format(it)} km", "প্রতি ${n(it)} কিমি") }, s.part.intervalMonths?.let { t("or $it months", "বা ${n(it)} মাস") })
                        Text(every.joinToString(" ") + (s.lastService?.let { " · " + t("last at ${"%,d".format(it.odometerKm)} km", "শেষ ${n(it.odometerKm)} কিমিতে") } ?: ""))
                    },
                    trailingContent = {
                        when (s.state) {
                            DueState.OVERDUE -> Text(t("Overdue", "সময় পেরিয়েছে"), color = Bad)
                            DueState.DUE_SOON -> Text(t("Due soon", "শীঘ্রই"), color = Warn)
                            else -> {}
                        }
                    },
                    modifier = Modifier.clickableItem { logPart = s.part.id },
                )
            }
            item { Gap(); Text(t("History", "ইতিহাস"), style = MaterialTheme.typography.titleSmall) }
            if (history.isEmpty()) item { Text(t("No services logged yet.", "এখনো কোনো সার্ভিস লগ নেই।"), style = MaterialTheme.typography.bodySmall) }
            items(history, key = { "h${it.id}" }) { h ->
                ListItem(
                    headlineContent = { Text(tpl.part(h.partId)?.name?.tr() ?: h.partId) },
                    supportingContent = { Text("${LocalDate.ofEpochDay(h.epochDay)} · ${n(h.odometerKm)} ${t("km", "কিমি")}" + (h.costBdt?.let { " · ৳${n(it)}" } ?: "") + (h.note?.let { " · $it" } ?: "")) },
                    leadingContent = { if (h.receiptPath != null) Icon(Icons.Default.Receipt, t("Receipt", "রসিদ")) },
                    trailingContent = { IconButton(onClick = { vm.deleteService(h.id) }) { Icon(Icons.Default.Delete, t("Delete", "মুছুন")) } },
                )
            }
        }
    }
    logPart?.let { pid ->
        LogServiceDialog(vm, id, pid, tpl.part(pid)?.name?.tr() ?: pid, v.odometerKm) { logPart = null }
    }
}

fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun LogServiceDialog(vm: MainViewModel, vehicleId: Long, partId: String, partName: String, currentKm: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var km by remember { mutableStateOf(currentKm.toString()) }
    var day by remember { mutableLongStateOf(today()) }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) receipt = pendingPath }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(partName) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(km, { km = it }, t("Odometer (km)", "ওডোমিটার (কিমি)"))
            DateButton(day, { day = it }, t("Date", "তারিখ"))
            NumberField(cost, { cost = it }, t("Cost ৳ (optional)", "খরচ ৳ (ঐচ্ছিক)"))
            OutlinedTextField(note, { note = it }, label = { Text(t("Note / garage (optional)", "নোট / গ্যারেজ (ঐচ্ছিক)")) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val (uri, path) = Photos.newPhoto(ctx, "receipt")
                    pendingUri = uri; pendingPath = path
                    camera.launch(uri)
                }) { Text(if (receipt == null) t("Photo receipt", "রসিদের ছবি") else t("Receipt saved ✓", "রসিদ সেভ ✓")) }
            }
        }
    }, confirmButton = {
        TextButton(enabled = km.toIntOrNull() != null, onClick = {
            vm.addService(ServiceEntity(vehicleId = vehicleId, partId = partId, odometerKm = km.toInt(), epochDay = day, costBdt = cost.toIntOrNull(), note = note.ifBlank { null }, receiptPath = receipt))
            onDismiss()
        }) { Text(t("Save", "সেভ")) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "বাতিল")) } })
}
