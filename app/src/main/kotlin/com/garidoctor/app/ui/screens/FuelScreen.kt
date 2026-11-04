@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.FuelEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.core.VehicleDoctor
import com.garidoctor.core.fuel.FuelAnalyzer
import com.garidoctor.core.model.EfficiencyUnit
import com.garidoctor.core.model.EnergyType
import java.time.LocalDate

@Composable
fun FuelScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val c = st.checkups[id] ?: return
    val energy = runCatching { EnergyType.valueOf(v.energyType) }.getOrDefault(EnergyType.OCTANE)
    val electric = VehicleDoctor.isElectric(energy)
    var unitOverride by remember { mutableStateOf<EfficiencyUnit?>(null) }
    // Dual-fuel cars: let the user log CNG or petrol separately.
    val unit = unitOverride ?: EfficiencyUnit.of(energy)
    val entries = st.snapshot.fuel.filter { it.vehicleId == id }
    var odo by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(if (electric) "1" else "") }
    var price by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }

    Screen(if (electric) t("Charge log", "চার্জ লগ") else t("Fuel & CNG log", "জ্বালানি ও সিএনজি লগ"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(t("Efficiency", "মাইলেজ")) {
                    val avg = c.fuel.average
                    Text(if (avg != null) "${"%.1f".format(avg)} ${unit.label.tr()}" else t("Log two full fills to see mileage", "মাইলেজ দেখতে দুইবার ফুল ট্যাংক লগ করুন"), style = MaterialTheme.typography.headlineSmall)
                    c.fuel.trend?.let { tr -> Text(t("Last 30 days: ${"%.1f".format(tr.recent)} vs usual ${"%.1f".format(tr.baseline)}", "গত ৩০ দিন: ${"%.1f".format(tr.recent)}, সাধারণত ${"%.1f".format(tr.baseline)}"), style = MaterialTheme.typography.bodySmall) }
                    c.fuel.costPerKmBdt?.let { Text(t("Cost per km: ৳${"%.2f".format(it)}", "প্রতি কিমি খরচ: ৳${"%.2f".format(it)}"), style = MaterialTheme.typography.bodySmall) }
                    if (c.fuel.points.size >= 2) Sparkline(c.fuel.points.map { it.efficiency })
                    FuelAnalyzer.alert(c.fuel.trend, electric)?.let { a ->
                        Text(a.title.tr(), color = MaterialTheme.colorScheme.error)
                        a.causes.forEach { Text("• ${it.tr()}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                SectionCard(if (electric) t("Add charge", "চার্জ যোগ করুন") else t("Add fill-up", "তেল/গ্যাস নেওয়া যোগ করুন")) {
                    if (energy == EnergyType.PETROL_CNG) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(unit == EfficiencyUnit.KM_PER_M3, { unitOverride = EfficiencyUnit.KM_PER_M3 }, { Text(t("CNG", "সিএনজি")) })
                        FilterChip(unit == EfficiencyUnit.KM_PER_LITRE, { unitOverride = EfficiencyUnit.KM_PER_LITRE }, { Text(t("Petrol/Octane", "পেট্রোল/অকটেন")) })
                    }
                    NumberField(odo, { odo = it }, t("Odometer (km) — current ${v.odometerKm}", "ওডোমিটার (কিমি) — বর্তমান ${n(v.odometerKm)}"))
                    NumberField(amount, { amount = it }, when (unit) {
                        EfficiencyUnit.KM_PER_LITRE -> t("Litres", "লিটার"); EfficiencyUnit.KM_PER_M3 -> t("CNG m³", "সিএনজি ঘনমিটার")
                        EfficiencyUnit.KM_PER_CHARGE -> t("Charge (1 = full, 0.5 = half)", "চার্জ (১ = ফুল, ০.৫ = অর্ধেক)")
                    }, decimal = true)
                    NumberField(price, { price = it }, t("Paid ৳ (optional)", "খরচ ৳ (ঐচ্ছিক)"))
                    Row { Checkbox(full, { full = it }); Text(if (electric) t("Charged to full", "ফুল চার্জ") else t("Filled to full", "ফুল ট্যাংক"), Modifier.padding(top = 12.dp)) }
                    Button(enabled = odo.toIntOrNull() != null && amount.toDoubleOrNull() != null, onClick = {
                        val km = odo.toInt()
                        // EV: km driven since the last full charge is the observed range for battery health.
                        val prevFull = entries.filter { it.full }.maxByOrNull { it.odometerKm }
                        val range = if (electric && full && prevFull != null && km > prevFull.odometerKm) (km - prevFull.odometerKm).toDouble() else null
                        vm.addFuel(FuelEntity(vehicleId = id, odometerKm = km, amount = amount.toDouble(), priceBdt = price.toIntOrNull(), full = full, epochDay = today(), rangeKm = range))
                        odo = ""; price = ""; if (!electric) amount = ""
                    }) { Text(t("Save", "সেভ")) }
                }
            }
            items(entries, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text("${n(e.odometerKm)} ${t("km", "কিমি")} · ${"%.2f".format(e.amount)}" + (e.priceBdt?.let { " · ৳${n(it)}" } ?: "")) },
                    supportingContent = { Text("${LocalDate.ofEpochDay(e.epochDay)}" + if (!e.full) " · " + t("partial", "আংশিক") else "") },
                    trailingContent = { IconButton(onClick = { vm.deleteFuel(e.id) }) { Icon(Icons.Default.Delete, null) } },
                )
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val min = values.min(); val max = values.max(); val span = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, (size.height - ((v - min) / span * size.height)).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
    }
}
