@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.Garages
import com.garidoctor.app.data.SosContact
import com.garidoctor.app.sos.Sos
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad

@Composable
fun GaragesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val contacts by vm.sos.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf<String?>(null) }
    var hasLoc by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLoc = it }
    val here = remember(hasLoc) { if (hasLoc) Sos.lastLocation(ctx) else null }
    var sosOpen by remember { mutableStateOf(false) }
    val types = listOf("bike_mechanic" to t("Bike mechanic", "বাইক মেকানিক"), "car_workshop" to t("Car workshop", "কার ওয়ার্কশপ"),
        "battery_shop" to t("Easy-bike battery", "ইজি বাইক ব্যাটারি"), "cng_kit" to t("CNG kit", "সিএনজি কিট"))
    val list = vm.garages.filter { type == null || it.type == type }
        .sortedWith(compareByDescending<com.garidoctor.app.data.Garage> { it.featured }.thenBy { g -> here?.let { Garages.distanceKm(it.latitude, it.longitude, g.lat, g.lng) } ?: 0.0 })

    Screen(t("Garages & SOS", "গ্যারেজ ও SOS"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = { if (!hasLoc) perm.launch(Manifest.permission.ACCESS_FINE_LOCATION); sosOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Bad), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("🆘 " + t("SOS — send my location & problem", "SOS — আমার লোকেশন ও সমস্যা পাঠান"), fontWeight = FontWeight.Bold)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == null, { type = null }, { Text(t("All", "সব")) })
                    types.forEach { (k, l) -> FilterChip(type == k, { type = k }, { Text(l) }) }
                }
            }
            if (vm.garages.any { it.sample }) item {
                Text(t("Directory is in pilot: listings below are samples. Verified garages are being added area by area.", "ডিরেক্টরি পাইলট পর্যায়ে: নিচের তালিকা নমুনা। এলাকাভিত্তিক যাচাই করা গ্যারেজ যোগ হচ্ছে।"), style = MaterialTheme.typography.bodySmall)
            }
            items(list, key = { it.id }) { g ->
                ListItem(
                    headlineContent = { Row { Text(g.name); if (g.verified) Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary) } },
                    supportingContent = {
                        val dist = here?.let { " · ${"%.1f".format(Garages.distanceKm(it.latitude, it.longitude, g.lat, g.lng))} km" } ?: ""
                        Text(types.firstOrNull { it.first == g.type }?.second.orEmpty() + " · " + g.area + dist)
                    },
                    leadingContent = { if (g.featured) Icon(Icons.Default.Star, t("Featured", "ফিচার্ড"), tint = MaterialTheme.colorScheme.tertiary) },
                    trailingContent = {
                        Row {
                            if (g.phone.isNotBlank()) TextButton(onClick = { Sos.call(ctx, g.phone) }) { Text(t("Call", "কল")) }
                            TextButton(onClick = { Sos.directions(ctx, g.lat, g.lng, g.name) }) { Text(t("Map", "ম্যাপ")) }
                        }
                    },
                )
            }
            item { Text(t("Own a garage? Get a featured listing — pay monthly via bKash/Nagad.", "আপনার গ্যারেজ আছে? ফিচার্ড লিস্টিং নিন — মাসিক বিকাশ/নগদে পেমেন্ট।"), style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (sosOpen) {
        var vehicleId by remember { mutableStateOf(st.snapshot.vehicles.firstOrNull()?.id) }
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        val v = st.snapshot.vehicles.firstOrNull { it.id == vehicleId }
        val diag = st.snapshot.symptoms.filter { it.vehicleId == vehicleId && !it.resolved }.maxByOrNull { it.epochDay }?.summary
            ?: st.checkups[vehicleId]?.health?.deductions?.firstOrNull()?.reason?.get(lang)
        val text = Sos.message(lang, v?.nickname ?: "-", v?.registrationNo, diag, Sos.lastLocation(ctx))
        AlertDialog(onDismissRequest = { sosOpen = false }, title = { Text("SOS") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    st.snapshot.vehicles.forEach { ve -> FilterChip(vehicleId == ve.id, { vehicleId = ve.id }, { Text(ve.nickname) }) }
                }
                Text(text, style = MaterialTheme.typography.bodySmall)
                contacts.forEach { c ->
                    Row {
                        Text(c.name, Modifier.weight(1f).padding(top = 12.dp))
                        TextButton(onClick = { Sos.sms(ctx, c, text) }) { Text("SMS") }
                        TextButton(onClick = { Sos.whatsapp(ctx, c, text) }) { Text("WhatsApp") }
                    }
                }
                Text(t("Add a contact (mechanic / family)", "কন্টাক্ট যোগ করুন (মেকানিক / পরিবার)"), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it }, label = { Text(t("Name", "নাম")) }, singleLine = true)
                NumberField(phone, { phone = it }, t("Phone (01XXXXXXXXX)", "ফোন (০১XXXXXXXXX)"))
                TextButton(enabled = name.isNotBlank() && phone.length >= 11, onClick = { vm.setSos(contacts + SosContact(name.trim(), phone)); name = ""; phone = "" }) { Text(t("Add", "যোগ")) }
            }
        }, confirmButton = { TextButton(onClick = { sosOpen = false }) { Text(t("Close", "বন্ধ")) } })
    }
}
