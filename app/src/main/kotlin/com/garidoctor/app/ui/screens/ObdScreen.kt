@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.garidoctor.app.GariDoctorApp
import com.garidoctor.app.data.VoltageEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.obd.ElmClient
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.obd.LiveData
import com.garidoctor.core.pro.ProFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** ELM327 Bluetooth dongle: fault codes decoded offline in Bangla + live data (Pro). */
@SuppressLint("MissingPermission")
@Composable
fun ObdScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { ElmClient(ctx) }
    DisposableEffect(Unit) { onDispose { client.close() } }
    val needsPerm = Build.VERSION.SDK_INT >= 31
    var granted by remember { mutableStateOf(!needsPerm || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var codes by remember { mutableStateOf<List<DiagnosticTroubleCode>?>(null) }
    var live by remember { mutableStateOf<List<LiveData>>(emptyList()) }
    var liveOn by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    val dict = remember { GariDoctorApp.instance.repository.dtc }

    LaunchedEffect(granted) { if (granted) devices = runCatching { client.bondedDevices() }.getOrDefault(emptyList()) }
    LaunchedEffect(liveOn) {
        while (liveOn && isActive) { live = runCatching { client.readLive() }.getOrDefault(live); delay(1000) }
    }

    Screen(t("OBD scan", "OBD স্ক্যান"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(t("Plug an ELM327 Bluetooth dongle into the OBD-II port (under the steering), turn ignition ON, and pair it in phone Bluetooth settings (PIN usually 1234 or 0000).",
                    "ELM327 ব্লুটুথ ডঙ্গল OBD-II পোর্টে (স্টিয়ারিং এর নিচে) লাগান, চাবি ON করুন এবং ফোনের ব্লুটুথ সেটিংসে পেয়ার করুন (পিন সাধারণত ১২৩৪ বা ০০০০)।"), style = MaterialTheme.typography.bodySmall)
            }
            if (!granted) item { Button(onClick = { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(t("Allow Bluetooth", "ব্লুটুথ অনুমতি দিন")) } }
            error?.let { item { Text(it, color = Bad) } }
            if (connected == null) {
                if (granted && devices.isEmpty()) item { Text(t("No paired devices. Pair the dongle first.", "কোনো পেয়ার করা ডিভাইস নেই। আগে ডঙ্গল পেয়ার করুন।")) }
                items(devices, key = { it.address }) { d ->
                    ListItem(headlineContent = { Text(d.name ?: d.address) }, supportingContent = { Text(d.address) }, trailingContent = {
                        TextButton(enabled = !busy, onClick = {
                            busy = true; error = null
                            scope.launch {
                                runCatching { client.connect(d) }
                                    .onSuccess { connected = d.name ?: d.address }
                                    .onFailure { error = t2(vm, "Could not connect: ${it.message}", "কানেক্ট হয়নি: ${it.message}") }
                                busy = false
                            }
                        }) { Text(t("Connect", "কানেক্ট")) }
                    })
                }
            } else {
                item { Text(t("Connected to $connected", "$connected এর সাথে যুক্ত"), fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { client.readCodes(dict) }.onSuccess { codes = it; vm.replaceFaultCodes(id, it.map { c -> c.code }) }.onFailure { error = it.message }
                                busy = false
                            }
                        }) { Text(t("Read fault codes", "ফল্ট কোড পড়ুন")) }
                        OutlinedButton(onClick = { if (vm.allowed(ProFeature.LIVE_OBD_GRAPHS)) liveOn = !liveOn else showPro = true }) {
                            Text(if (liveOn) t("Stop live", "লাইভ বন্ধ") else t("Live data", "লাইভ ডেটা"))
                        }
                    }
                }
                codes?.let { list ->
                    if (list.isEmpty()) item { Text(t("No fault codes stored ✓", "কোনো ফল্ট কোড নেই ✓")) }
                    items(list, key = { it.code }) { c ->
                        SectionCard(c.code) {
                            Text(c.describe().tr(), fontWeight = FontWeight.SemiBold, color = if ((c.info?.severity ?: 2) >= 3) Bad else Warn)
                            c.info?.advice?.let { Text(it.tr(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (list.isNotEmpty()) item {
                        OutlinedButton(onClick = { scope.launch { runCatching { client.clearCodes() }; codes = emptyList(); vm.replaceFaultCodes(id, emptyList()) } }) {
                            Text(t("Clear codes (after repair)", "কোড মুছুন (মেরামতের পর)"))
                        }
                    }
                }
                if (live.isNotEmpty()) item {
                    SectionCard(t("Live data", "লাইভ ডেটা")) {
                        live.forEach { l -> Row { Text(l.name.tr(), Modifier.weight(1f)); Text("${"%.1f".format(l.value)} ${l.unit}", fontWeight = FontWeight.Bold) } }
                        live.firstOrNull { it.pid == "42" }?.let { volt ->
                            TextButton(onClick = { vm.addVoltage(VoltageEntity(vehicleId = id, volts = volt.value, nominalVolts = 12.0, epochDay = today())) }) {
                                Text(t("Save battery voltage to health record", "ব্যাটারি ভোল্টেজ হেলথ রেকর্ডে সেভ করুন"))
                            }
                        }
                    }
                }
            }
            item { Text(t("Tip: buy a tested dongle. Cheap clones (v2.1) often fail on newer cars.", "টিপ: পরীক্ষিত ডঙ্গল কিনুন। সস্তা ক্লোন (v2.1) নতুন গাড়িতে প্রায়ই কাজ করে না।"), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showPro) ProDialog(vm, t("Live OBD graphs are a Pro feature.", "লাইভ OBD গ্রাফ প্রো ফিচার।")) { showPro = false }
}

private fun t2(vm: MainViewModel, en: String, bn: String) = if (vm.lang.value == com.garidoctor.core.model.Lang.BN) bn else en
