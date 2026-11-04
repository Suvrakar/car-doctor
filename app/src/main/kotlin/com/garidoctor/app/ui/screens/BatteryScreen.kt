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
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.core.battery.BatteryHealth
import com.garidoctor.core.battery.RangeSample
import com.garidoctor.core.model.EnergyType

/** Easy-bike / e-scooter battery set health: cycles, range fade, water top-up. */
@Composable
fun BatteryScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val energy = runCatching { EnergyType.valueOf(v.energyType) }.getOrDefault(EnergyType.ELECTRIC_LEAD_ACID)
    val charges = st.snapshot.fuel.filter { it.vehicleId == id }
    val lastWater = st.snapshot.services.filter { it.vehicleId == id && it.partId == "battery_water" }.maxOfOrNull { it.epochDay }
    var rated by remember { mutableStateOf(v.ratedRangeKm?.toInt()?.toString() ?: "") }
    val ratedKm = rated.toDoubleOrNull()

    Screen(t("Battery health", "ব্যাটারি হেলথ"), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(rated, { rated = it }, t("Range per full charge when new (km)", "নতুন অবস্থায় এক চার্জে রেঞ্জ (কিমি)"))
            if (ratedKm != null && ratedKm != v.ratedRangeKm) TextButton(onClick = { vm.updateVehicle(v.copy(ratedRangeKm = ratedKm)) }) { Text(t("Save", "সেভ")) }
            if (ratedKm == null) { Text(t("Enter the rated range to estimate battery health.", "ব্যাটারি হেলথ জানতে রেঞ্জ লিখুন।")); return@Column }
            val r = BatteryHealth.report(
                energy, ratedKm,
                chargeCount = charges.count { it.full },
                partialChargeFractions = charges.filter { !it.full }.sumOf { it.amount },
                samples = charges.mapNotNull { c -> c.rangeKm?.let { RangeSample(c.epochDay, it) } },
                daysSinceWaterTopUp = lastWater?.let { today() - it },
            )
            SectionCard {
                Row {
                    ScoreRing(r.healthPercent, 96.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(t("State of health", "ব্যাটারির অবস্থা"), style = MaterialTheme.typography.titleMedium)
                        r.currentRangeKm?.let { Text(t("Recent range: ${it.toInt()} of ${ratedKm.toInt()} km", "সাম্প্রতিক রেঞ্জ: ${n(it.toInt())} / ${n(ratedKm.toInt())} কিমি")) }
                        Text(t("Charge cycles: ${r.estimatedCycles} of ~${r.ratedCycles}", "চার্জ সাইকেল: ${n(r.estimatedCycles)} / ~${n(r.ratedCycles)}"))
                        Text(t("≈ ${r.cyclesLeft} cycles left", "≈ ${n(r.cyclesLeft)} সাইকেল বাকি"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (r.advice.isNotEmpty()) SectionCard(t("Advice", "পরামর্শ")) { r.advice.forEach { Text("• ${it.tr()}") } }
            Text(t("Log each charge in the Charge log. Ticking \"Charged to full\" records the km driven on the previous charge as a range sample.",
                "প্রতিটি চার্জ 'চার্জ লগ'-এ লিখুন। 'ফুল চার্জ' টিক দিলে আগের চার্জে কত কিমি চলেছে তা রেঞ্জ হিসেবে রেকর্ড হয়।"), style = MaterialTheme.typography.bodySmall)
        }
    }
}
