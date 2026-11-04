@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.fleet.Fleet
import com.garidoctor.core.fleet.FleetVehicleRow
import com.garidoctor.core.pro.ProFeature
import com.garidoctor.core.schedule.DueState

/** One screen for CNG owners, easy-bike garages and delivery fleets. */
@Composable
fun FleetScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(!vm.allowed(ProFeature.FLEET_MODE)) }
    val rows = st.snapshot.vehicles.mapNotNull { v ->
        val c = st.checkups[v.id] ?: return@mapNotNull null
        val rec = st.snapshot.records[v.id] ?: return@mapNotNull null
        FleetVehicleRow(rec.vehicle, v.driverName, c.health, c.parts.filter { it.state != DueState.OK },
            rec.documents.map { DocumentExpiry.status(it, today()) }.filter { it.state != ExpiryState.VALID })
    }
    val sum = Fleet.summarize(rows)

    Screen(t("Fleet", "ফ্লিট"), onBack) { pad ->
        if (!pro) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                Text(t("Fleet mode is for owners with many vehicles (CNG, easy-bike, delivery, transport). It's part of Pro / Fleet subscription.",
                    "ফ্লিট মোড অনেক গাড়ির মালিকদের জন্য (সিএনজি, ইজি বাইক, ডেলিভারি, পরিবহন)। এটি প্রো / ফ্লিট সাবস্ক্রিপশনের অংশ।"))
                Gap(); Button(onClick = { showPro = true }) { Text(t("Unlock", "আনলক")) }
            }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stat(n(sum.averageScore), t("avg score", "গড় স্কোর"))
                        Stat(n(sum.needAttention), t("need attention", "মনোযোগ দরকার"))
                        Stat(n(sum.overdueServices), t("overdue", "সার্ভিস বাকি"))
                        Stat(n(sum.expiredPapers + sum.papersExpiringIn30), t("papers", "কাগজ"))
                    }
                }
            }
            items(sum.rows, key = { it.vehicle.id }) { r ->
                Card(onClick = { onOpen(r.vehicle.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(r.health.score, 56.dp, 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.vehicle.nickname + (r.vehicle.registrationNo?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                            r.driverName?.let { Text(t("Driver: $it", "চালক: $it"), style = MaterialTheme.typography.bodySmall) }
                            val od = r.overdue.count { it.state == DueState.OVERDUE }
                            if (od > 0) Text(r.overdue.filter { it.state == DueState.OVERDUE }.take(2).joinToString { it.part.name.tr() }, color = Bad, style = MaterialTheme.typography.labelMedium)
                            r.expiringDocs.firstOrNull()?.let { d ->
                                Text("${d.doc.type.label.tr()}: " + if (d.daysLeft < 0) t("expired", "মেয়াদোত্তীর্ণ") else t("${d.daysLeft} days", "${n(d.daysLeft)} দিন"), color = if (d.daysLeft < 0) Bad else Warn, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPro && !pro) ProDialog(vm, t("Fleet mode: see every vehicle's health, overdue services and expiring papers on one screen.", "ফ্লিট মোড: সব গাড়ির হেলথ, বাকি সার্ভিস ও কাগজের মেয়াদ এক স্ক্রিনে।")) { showPro = false }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
