@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.ServiceEntity
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.template.ModelPreset
import com.garidoctor.core.template.VehicleTemplate

fun vehicleIcon(templateId: String): ImageVector = when (templateId) {
    "motorcycle" -> Icons.Default.TwoWheeler
    "scooter" -> Icons.Default.Moped
    "car" -> Icons.Default.DirectionsCar
    "cng_auto" -> Icons.Default.LocalTaxi
    "easy_bike" -> Icons.Default.BatteryChargingFull
    "e_scooter" -> Icons.Default.EvStation
    "microbus" -> Icons.Default.AirportShuttle
    "pickup", "truck" -> Icons.Default.LocalShipping
    else -> Icons.Default.DirectionsCar
}

/** Three short steps instead of one long form: type → model → details. */
@Composable
fun AddVehicleScreen(vm: MainViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var modelId by rememberSaveable { mutableStateOf<String?>(null) }
    val tpl = templateId?.let { st.registry.template(it) }
    val goBack: () -> Unit = { if (step > 0) step-- else onBack() }
    BackHandler(enabled = step > 0) { step-- }

    val titles = listOf(t("What do you drive?", "কোন গাড়ি চালান?"), t("Which model?", "কোন মডেল?"), t("A few details", "কিছু তথ্য"))
    Screen(t("Add vehicle", "গাড়ি যোগ করুন"), goBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), drawStopIndicator = {})
            Text(t("Step ${step + 1} of 3", "ধাপ ${n(step + 1)} / ${n(3)}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.gutter, top = Spacing.m))
            Text(titles[step], style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
            AnimatedContent(
                targetState = step, label = "step",
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                },
            ) { s ->
                when (s) {
                    0 -> TypeStep(st.registry.all, templateId) { templateId = it; modelId = null; step = 1 }
                    1 -> if (tpl != null) ModelStep(tpl, modelId) { modelId = it; step = 2 }
                    else -> if (tpl != null) DetailsStep(vm, tpl, modelId, onCreated)
                }
            }
        }
    }
}

@Composable
private fun TypeStep(all: List<VehicleTemplate>, selected: String?, onPick: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(Spacing.gutter + 4.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        items(all, key = { it.id }) { tpl ->
            val sel = tpl.id == selected
            AppSurface(
                modifier = Modifier.height(120.dp).then(if (sel) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.large) else Modifier),
                onClick = { onPick(tpl.id) }, contentDescription = tpl.name.tr(), elevation = if (sel) 2.dp else 6.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconBadge(vehicleIcon(tpl.id), size = 48.dp)
                    Gap(Spacing.s)
                    Text(tpl.name.tr(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ModelStep(tpl: VehicleTemplate, selected: String?, onPick: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val models: List<ModelPreset> = tpl.models.filter { query.isBlank() || "${it.brand} ${it.model}".contains(query.trim(), ignoreCase = true) }
    Column {
        OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(t("Search brand or model", "ব্র্যান্ড বা মডেল খুঁজুন")) },
            singleLine = true, shape = CircleShape50, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter))
        LazyColumn(contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, Spacing.xxl)) {
            item {
                GroupCard {
                    models.forEachIndexed { i, m ->
                        if (i > 0) RowDivider()
                        ActionRow(vehicleIcon(tpl.id), "${m.brand} ${m.model}", m.overrides["engine_oil"]?.intervalKm?.let { t("Oil every ${"%,d".format(it)} km", "প্রতি ${n(it)} কিমিতে তেল বদল") },
                            trailing = { if (m.id == selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { onPick(m.id) })
                    }
                    if (models.isNotEmpty()) RowDivider()
                    ActionRow(Icons.Default.MoreHoriz, t("Other / not listed", "অন্যান্য / তালিকায় নেই"), t("Uses typical intervals; you can edit them later", "সাধারণ সময়সূচি; পরে বদলাতে পারবেন"),
                        onClick = { onPick(null) })
                }
            }
        }
    }
}

private val CircleShape50 = androidx.compose.foundation.shape.RoundedCornerShape(50)

@Composable
private fun DetailsStep(vm: MainViewModel, tpl: VehicleTemplate, modelId: String?, onCreated: (Long) -> Unit) {
    val preset = tpl.model(modelId)
    var nickname by rememberSaveable { mutableStateOf(preset?.model ?: "") }
    var reg by rememberSaveable { mutableStateOf("") }
    var odo by rememberSaveable { mutableStateOf("") }
    var energy by rememberSaveable { mutableStateOf(preset?.energyType ?: tpl.defaultEnergy) }
    var range by rememberSaveable { mutableStateOf("") }
    var driver by rememberSaveable { mutableStateOf("") }
    var lastOilKm by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val km = odo.toIntOrNull()
    val oilError = lastOilKm.toIntOrNull()?.let { km != null && it > km } ?: false
    val electric = energy == EnergyType.ELECTRIC_LEAD_ACID || energy == EnergyType.ELECTRIC_LITHIUM

    Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.gutter).imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        OutlinedTextField(nickname, { nickname = it }, label = { Text(t("Nickname", "নাম")) }, placeholder = { Text(t("e.g. My Pulsar", "যেমন: আমার পালসার")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField(odo, { odo = it }, t("Current odometer (km)", "বর্তমান ওডোমিটার (কিমি)"), supporting = t("Read it from the meter", "মিটার দেখে লিখুন"))
        NumberField(lastOilKm, { lastOilKm = it }, t("Last engine-oil change at (km) — optional", "শেষ ইঞ্জিন অয়েল বদল (কিমি) — ঐচ্ছিক"),
            supporting = if (oilError) t("Can't be more than the odometer", "ওডোমিটারের চেয়ে বেশি হতে পারে না") else t("Makes your first score accurate", "প্রথম স্কোর সঠিক করে"))
        Text(t("Fuel / energy", "জ্বালানি / শক্তি"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            EnergyType.entries.forEach { e ->
                FilterChip(selected = energy == e, onClick = { energy = e }, label = { Text(energyLabel(e)) },
                    leadingIcon = if (energy == e) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
            }
        }
        if (electric) NumberField(range, { range = it }, t("Range on a full charge when new (km)", "নতুন অবস্থায় এক চার্জে রেঞ্জ (কিমি)"))
        OutlinedTextField(reg, { reg = it }, label = { Text(t("Registration no. — optional", "রেজিস্ট্রেশন নম্বর — ঐচ্ছিক")) }, placeholder = { Text("DHAKA METRO-LA 12-3456") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (tpl.fleetFriendly) OutlinedTextField(driver, { driver = it }, label = { Text(t("Driver name — optional", "চালকের নাম — ঐচ্ছিক")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Gap(Spacing.s)
        Button(
            enabled = !saving && km != null && nickname.isNotBlank() && !oilError,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = {
                saving = true
                val v = VehicleEntity(templateId = tpl.id, modelPresetId = modelId, nickname = nickname.trim(), registrationNo = reg.trim().ifBlank { null },
                    odometerKm = km!!, baselineKm = km, energyType = energy.name, createdAtEpochDay = today(),
                    driverName = driver.trim().ifBlank { null }, ratedRangeKm = range.toDoubleOrNull())
                vm.addVehicle(v) { id ->
                    lastOilKm.toIntOrNull()?.takeIf { it <= km && tpl.part("engine_oil") != null }?.let {
                        vm.addService(ServiceEntity(vehicleId = id, partId = "engine_oil", odometerKm = it, epochDay = today(), note = "initial"))
                    }
                    onCreated(id)
                }
            },
        ) { Text(t("Run first checkup", "প্রথম চেকআপ করুন")) }
        Text(t("Parts without a logged service are counted from today. You can log past services on the next screen.",
            "যে পার্টসের সার্ভিস লগ নেই সেগুলো আজ থেকে গণনা হবে। আগের সার্ভিসগুলো পরের স্ক্রিনে লগ করতে পারবেন।"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
