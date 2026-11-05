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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.garidoctor.app.GariDoctorApp
import com.garidoctor.app.data.VoltageEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.obd.ElmClient
import com.garidoctor.app.obd.UsbElm
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.app.ui.theme.Warn
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.obd.LiveData
import com.garidoctor.core.pro.ProFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** ELM327 over Bluetooth or a USB cable (OTG): fault codes decoded offline + live data (Pro). */
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

    val usbSupported = remember { UsbElm.isSupported(ctx) }
    var useUsb by rememberSaveable { mutableStateOf(false) }
    var usbAdapters by remember { mutableStateOf<List<UsbElm.Adapter>>(emptyList()) }

    LaunchedEffect(granted) { if (granted) devices = runCatching { client.bondedDevices() }.getOrDefault(emptyList()) }
    // Android only tells activities about a newly plugged cable, so look for one every 2 s while this tab is open.
    LaunchedEffect(useUsb, connected) {
        while (useUsb && connected == null && isActive) { usbAdapters = UsbElm.adapters(ctx); delay(2000) }
    }
    LaunchedEffect(liveOn) {
        while (liveOn && isActive) { live = runCatching { client.readLive() }.getOrDefault(live); delay(1000) }
    }

    Screen(t("OBD scan", "OBD স্ক্যান"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (connected == null && usbSupported) item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !useUsb, onClick = { useUsb = false; error = null }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { SegmentedButtonDefaults.Icon(!useUsb) { Icon(Icons.Default.Bluetooth, null, Modifier.size(SegmentedButtonDefaults.IconSize)) } }) { Text(t("Bluetooth", "ব্লুটুথ")) }
                    SegmentedButton(selected = useUsb, onClick = { useUsb = true; error = null }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { SegmentedButtonDefaults.Icon(useUsb) { Icon(Icons.Default.Usb, null, Modifier.size(SegmentedButtonDefaults.IconSize)) } }) { Text(t("USB cable", "ইউএসবি ক্যাবল")) }
                }
            }
            if (connected == null) item {
                Text(
                    if (useUsb) t("Plug the ELM327 USB cable into the OBD-II port (under the steering) and into your phone with an OTG adapter, then turn the ignition ON. Allow access when Android asks.",
                        "ELM327 ইউএসবি ক্যাবল OBD-II পোর্টে (স্টিয়ারিং এর নিচে) লাগান, OTG অ্যাডাপ্টার দিয়ে ফোনে লাগান, তারপর চাবি ON করুন। অ্যান্ড্রয়েড অনুমতি চাইলে দিন।")
                    else t("Plug an ELM327 Bluetooth dongle into the OBD-II port (under the steering), turn ignition ON, and pair it in phone Bluetooth settings (PIN usually 1234 or 0000).",
                        "ELM327 ব্লুটুথ ডঙ্গল OBD-II পোর্টে (স্টিয়ারিং এর নিচে) লাগান, চাবি ON করুন এবং ফোনের ব্লুটুথ সেটিংসে পেয়ার করুন (পিন সাধারণত ১২৩৪ বা ০০০০)।"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { item { Text(it, color = Bad) } }
            if (connected == null && useUsb) {
                if (usbAdapters.isEmpty()) item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(t("Waiting for a USB OBD cable…", "ইউএসবি OBD ক্যাবলের অপেক্ষায়…"))
                    }
                }
                items(usbAdapters, key = { it.key }) { a ->
                    ListItem(headlineContent = { Text(a.title) }, supportingContent = { Text(a.chip) },
                        leadingContent = { Icon(Icons.Default.Usb, null) }, trailingContent = {
                        TextButton(enabled = !busy, onClick = {
                            busy = true; error = null
                            scope.launch {
                                runCatching {
                                    if (!UsbElm.requestPermission(ctx, a)) throw IllegalStateException(t2(vm, "USB access was not allowed", "ইউএসবি অনুমতি দেওয়া হয়নি"))
                                    client.connect(a)
                                }
                                    .onSuccess { connected = a.title }
                                    .onFailure { error = t2(vm, "Could not connect: ${it.message}", "কানেক্ট হয়নি: ${it.message}") }
                                busy = false
                            }
                        }) { Text(t("Connect", "কানেক্ট")) }
                    })
                }
            } else if (connected == null) {
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
            if (useUsb && connected == null) item { Text(t("Works with FTDI, CH340, CP210x and PL2303 cables. Your phone must support USB OTG.", "FTDI, CH340, CP210x ও PL2303 ক্যাবলে কাজ করে। ফোনে USB OTG সাপোর্ট থাকতে হবে।"), style = MaterialTheme.typography.bodySmall) }
            item { Text(t("Tip: buy a tested dongle. Cheap clones (v2.1) often fail on newer cars.", "টিপ: পরীক্ষিত ডঙ্গল কিনুন। সস্তা ক্লোন (v2.1) নতুন গাড়িতে প্রায়ই কাজ করে না।"), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showPro) ProDialog(vm, t("Live OBD graphs are a Pro feature.", "লাইভ OBD গ্রাফ প্রো ফিচার।")) { showPro = false }
}

private fun t2(vm: MainViewModel, en: String, bn: String) = vm.lang.value.pick(en, bn)
