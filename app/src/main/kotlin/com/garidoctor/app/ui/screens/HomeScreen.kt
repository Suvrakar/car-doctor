@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.schedule.DueState
import com.garidoctor.app.data.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onNav: (String) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(t("GariDoctor", "গাড়িডাক্তার"), fontWeight = FontWeight.Bold) }, actions = {
                IconButton(onClick = { onNav("garages") }) { Icon(Icons.Default.Build, t("Garages & SOS", "গ্যারেজ ও SOS")) }
                IconButton(onClick = { onNav("fleet") }) { Icon(Icons.Default.Dashboard, t("Fleet", "ফ্লিট")) }
                IconButton(onClick = { onNav("settings") }) { Icon(Icons.Default.Settings, t("Settings", "সেটিংস")) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { if (vm.canAddVehicle()) onAdd() else showPro = true },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text(t("Add vehicle", "গাড়ি যোগ করুন")) })
        },
    ) { pad ->
        if (st.loaded && st.snapshot.vehicles.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(100, 120.dp)
                Gap(16.dp)
                Text(t("A health doctor for every vehicle", "প্রতিটি গাড়ির জন্য একজন হেলথ ডাক্তার"), style = MaterialTheme.typography.titleLarge)
                Gap()
                Text(t("Bike, car, CNG, easy-bike, microbus, pickup or truck — add it and get a 0–100 health score, reminders and paper alerts. Everything stays on your phone.",
                    "বাইক, কার, সিএনজি, ইজি বাইক, মাইক্রোবাস, পিকআপ বা ট্রাক — যোগ করুন আর পান ০–১০০ হেলথ স্কোর, রিমাইন্ডার ও কাগজের মেয়াদের সতর্কতা। সব তথ্য আপনার ফোনেই থাকে।"))
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(st.snapshot.vehicles, key = { it.id }) { v ->
                val c = st.checkups[v.id]
                val tpl = st.registry.template(v.templateId)
                val rec = st.snapshot.records[v.id]
                Card(Modifier.fillMaxWidth().clickable { onOpen(v.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(c?.health?.score ?: 100, 72.dp, 8.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(tpl?.name?.tr(), tpl?.model(v.modelPresetId)?.let { "${it.brand} ${it.model}" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            Text("${n(v.odometerKm)} ${t("km", "কিমি")}", style = MaterialTheme.typography.bodySmall)
                            val overdue = c?.parts?.count { it.state == DueState.OVERDUE } ?: 0
                            val papers = rec?.documents?.count { DocumentExpiry.status(it, today()).state != ExpiryState.VALID } ?: 0
                            if (overdue > 0) Text(t("$overdue service(s) overdue", "${n(overdue)}টি সার্ভিস বাকি"), color = Bad, style = MaterialTheme.typography.labelMedium)
                            if (papers > 0) Text(t("$papers paper(s) need attention", "${n(papers)}টি কাগজের মেয়াদ শেষের পথে"), color = Warn, style = MaterialTheme.typography.labelMedium)
                            c?.health?.deductions?.firstOrNull()?.let { Text(it.reason.tr(), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
    if (showPro) ProDialog(vm, t("Free version supports one vehicle. Unlock Pro for multiple vehicles, PDF passport, live OBD graphs and fleet mode.",
        "ফ্রি ভার্সনে একটি গাড়ি। একাধিক গাড়ি, পিডিএফ পাসপোর্ট, লাইভ OBD গ্রাফ ও ফ্লিট মোডের জন্য প্রো আনলক করুন।")) { showPro = false }
}

/** Pro unlock: promo/bundle code redemption (Play Billing hooks in here for paid unlocks). */
@Composable
fun ProDialog(vm: MainViewModel, reason: String, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t("GariDoctor Pro", "গাড়িডাক্তার প্রো")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reason)
                Text(t("One-time ৳299–499. Have a code from a Facebook group, garage or dongle bundle?", "এককালীন ৳২৯৯–৪৯৯। ফেসবুক গ্রুপ, গ্যারেজ বা ডঙ্গল বান্ডেলের কোড আছে?"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(code, { code = it; error = false }, label = { Text("GD-XXXX-XXXX-XXXX") }, isError = error, singleLine = true)
                if (error) Text(t("Invalid code", "কোডটি সঠিক নয়"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { if (vm.redeem(code)) onDismiss() else error = true }) { Text(t("Redeem", "রিডিম")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Later", "পরে")) } })
}
