@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.GariDoctorApp
import com.garidoctor.app.data.VoltageEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.obd.BleElm
import com.garidoctor.app.obd.CarInfo
import com.garidoctor.app.obd.ElmClient
import com.garidoctor.app.obd.UsbElm
import com.garidoctor.app.obd.WifiElm
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.obd.EngineProfile
import com.garidoctor.core.obd.LiveContext
import com.garidoctor.core.obd.LiveData
import com.garidoctor.core.obd.LiveLevel
import com.garidoctor.core.obd.LiveMonitor
import com.garidoctor.core.obd.LiveRules
import com.garidoctor.core.obd.LiveVerdict
import com.garidoctor.core.model.EnergyType
import com.garidoctor.app.ui.theme.container
import com.garidoctor.app.ui.theme.onContainer
import com.garidoctor.core.pro.ProFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The four ways an ELM327 adapter can talk to the phone. */
private enum class Link(val icon: ImageVector) { BLUETOOTH(Icons.Default.Bluetooth), BLE(Icons.Default.BluetoothSearching), WIFI(Icons.Default.Wifi), USB(Icons.Default.Usb) }

/**
 * OBD scan with any ELM327 adapter: Bluetooth Classic, Bluetooth LE, Wi-Fi or a USB cable (OTG).
 * After connecting we ask the car what it supports once, then read stored and pending fault codes
 * (explained offline) and live data (Pro). The last adapter that worked reconnects in one tap.
 */
@SuppressLint("MissingPermission")
@Composable
fun ObdScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val client = remember { ElmClient(ctx) }
    DisposableEffect(Unit) { onDispose { client.close() } }
    val dict = remember { GariDoctorApp.instance.repository.dtc }
    val usbSupported = remember { UsbElm.isSupported(ctx) }

    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val btPerms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    val blePerms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    var btGranted by remember { mutableStateOf(btPerms.all(::has)) }
    var bleGranted by remember { mutableStateOf(blePerms.all(::has)) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> btGranted = r.values.all { it } }
    val bleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> bleGranted = r.values.all { it } }

    var link by rememberSaveable { mutableStateOf(Link.BLUETOOTH) }
    var bonded by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val bleFound = remember { mutableStateListOf<BleElm.Found>() }
    var bleScanning by remember { mutableStateOf(false) }
    var usbAdapters by remember { mutableStateOf<List<UsbElm.Adapter>>(emptyList()) }
    var host by rememberSaveable { mutableStateOf(WifiElm.DEFAULT_HOST) }
    var port by rememberSaveable { mutableStateOf(WifiElm.DEFAULT_PORT.toString()) }

    var connected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<CarInfo?>(null) }
    var codes by remember { mutableStateOf<List<DiagnosticTroubleCode>?>(null) }
    var pending by remember { mutableStateOf<List<DiagnosticTroubleCode>>(emptyList()) }
    var live by remember { mutableStateOf<List<LiveData>>(emptyList()) }
    var liveOn by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    val vehicle = st.snapshot.vehicles.firstOrNull { it.id == id }
    val profile = remember(vehicle?.energyType) { EngineProfile.of(vehicle?.energyType?.let { runCatching { EnergyType.valueOf(it) }.getOrNull() } ?: EnergyType.OCTANE) }
    var monitor by remember { mutableStateOf(LiveMonitor(profile)) }
    var verdicts by remember { mutableStateOf<Map<String, LiveVerdict>>(emptyMap()) }
    var liveVolts by remember { mutableStateOf<Double?>(null) }
    var saved by remember { mutableStateOf(false) }
    /** Saves the live check to history (once per session; needs at least 20 s of data). */
    fun saveCheck(): Boolean {
        if (saved || monitor.samples < 20) return false
        vm.saveLiveCheck(id, monitor.summary(System.currentTimeMillis())); saved = true
        android.widget.Toast.makeText(ctx, lang.pick("Live check saved to History", "লাইভ চেক ইতিহাসে সেভ হয়েছে"), android.widget.Toast.LENGTH_SHORT).show()
        return true
    }

    /** Connect with [open], remember the adapter, then ask the car what it supports. */
    fun connect(name: String, key: String, open: suspend () -> Unit) {
        busy = lang.pick("Connecting to $name…", "$name এর সাথে যুক্ত হচ্ছে…"); error = null
        scope.launch {
            runCatching {
                open()
                vm.prefs.lastObd = key; vm.prefs.lastObdName = name
                busy = lang.pick("Talking to the car…", "গাড়ির সাথে কথা হচ্ছে…")
                info = client.probe()
            }.onSuccess { connected = name }
                .onFailure { client.close(); error = lang.pick("Could not connect: ${it.message}", "কানেক্ট হয়নি: ${it.message}") }
            busy = null
        }
    }
    fun connectBt(d: BluetoothDevice) = connect(d.name ?: d.address, "bt:${d.address}") { client.connect(d) }
    fun connectBle(d: BluetoothDevice, name: String) = connect(name, "ble:${d.address}") { client.connectBle(d) }
    fun connectWifi(h: String, p: Int) = connect("Wi-Fi $h", "wifi:$h:$p") { client.connectWifi(h, p) }
    fun connectUsb(a: UsbElm.Adapter) = connect(a.title, "usb") {
        if (!UsbElm.requestPermission(ctx, a)) throw IllegalStateException(lang.pick("USB access was not allowed", "ইউএসবি অনুমতি দেওয়া হয়নি"))
        client.connect(a)
    }

    /** One tap back to the adapter that worked last time. */
    val last = vm.prefs.lastObd
    fun reconnect() {
        val key = last ?: return
        when {
            key.startsWith("bt:") -> { if (!btGranted) { btLauncher.launch(btPerms); return }
                client.bondedDevices().firstOrNull { it.address == key.removePrefix("bt:") }?.let(::connectBt)
                    ?: run { error = lang.pick("That adapter is no longer paired.", "ওই অ্যাডাপ্টার আর পেয়ার করা নেই।") } }
            key.startsWith("ble:") -> { if (!bleGranted) { bleLauncher.launch(blePerms); return }
                BleElm.device(ctx, key.removePrefix("ble:"))?.let { connectBle(it, vm.prefs.lastObdName ?: "OBD") } }
            key.startsWith("wifi:") -> key.removePrefix("wifi:").split(':').let { (h, p) -> connectWifi(h, p.toIntOrNull() ?: WifiElm.DEFAULT_PORT) }
            key == "usb" -> UsbElm.adapters(ctx).firstOrNull()?.let(::connectUsb)
                ?: run { error = lang.pick("Plug the USB cable in first.", "আগে ইউএসবি ক্যাবল লাগান।") }
        }
    }

    LaunchedEffect(btGranted) { if (btGranted) bonded = runCatching { client.bondedDevices() }.getOrDefault(emptyList()) }
    // Android only tells activities about a newly plugged cable, so look for one every 2 s while that tab is open.
    LaunchedEffect(link, connected) { while (link == Link.USB && connected == null && isActive) { usbAdapters = UsbElm.adapters(ctx); delay(2000) } }
    // BLE scan runs for 12 s at a time.
    LaunchedEffect(bleScanning) {
        if (!bleScanning) return@LaunchedEffect
        bleFound.clear()
        val handle = runCatching { BleElm.scan(ctx) { f -> scope.launch { if (bleFound.none { it.device.address == f.device.address }) bleFound += f } } }
            .onFailure { error = it.message; bleScanning = false }.getOrNull() ?: return@LaunchedEffect
        try { delay(12_000) } finally { handle.close(); bleScanning = false }
    }
    // Live loop: readings once a second, the adapter's voltage every 5th pass, all judged by the monitor.
    LaunchedEffect(liveOn) {
        if (!liveOn) { if (monitor.samples >= 30) saveCheck(); return@LaunchedEffect }
        monitor = LiveMonitor(profile); saved = false; verdicts = emptyMap()
        var pass = 0
        while (liveOn && isActive) {
            live = runCatching { client.readLive() }.getOrDefault(live)
            if (pass++ % 5 == 0) client.adapterVolts()?.let { liveVolts = it }
            verdicts = monitor.feed(live.associate { it.pid to it.value }, liveVolts, System.currentTimeMillis()).associateBy { it.key }
            delay(1000)
        }
    }
    DisposableEffect(Unit) { onDispose { if (monitor.samples >= 30 && !saved) vm.saveLiveCheck(id, monitor.summary(System.currentTimeMillis())) } }

    Screen(t("OBD scan", "OBD স্ক্যান"), onBack, actions = {
        if (connected != null) TextButton(onClick = { liveOn = false; if (monitor.samples >= 30) saveCheck(); client.close(); connected = null; info = null; codes = null; pending = emptyList(); live = emptyList() }) {
            Text(t("Disconnect", "সংযোগ বিচ্ছিন্ন"))
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            busy?.let { msg -> item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(msg) } } }
            error?.let { item { StatusBanner(Icons.Default.ErrorOutline, Tone.BAD, t("Couldn't connect", "কানেক্ট হয়নি"), it) } }

            if (connected == null) {
                if (last != null && busy == null) item {
                    AppSurface(Modifier.fillMaxWidth(), onClick = ::reconnect) {
                        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.m))
                            Column(Modifier.weight(1f)) {
                                Text(t("Reconnect", "আবার যুক্ত করুন"), style = MaterialTheme.typography.titleMedium)
                                Text(vm.prefs.lastObdName ?: "OBD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Text(t("How does your adapter connect?", "আপনার অ্যাডাপ্টার কীভাবে যুক্ত হয়?"), style = MaterialTheme.typography.titleSmall)
                    Gap(Spacing.s)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Link.entries.filter { it != Link.USB || usbSupported }.forEach { l ->
                            FilterChip(selected = link == l, onClick = { link = l; error = null }, leadingIcon = { Icon(l.icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
                                label = { Text(when (l) { Link.BLUETOOTH -> t("Bluetooth", "ব্লুটুথ"); Link.BLE -> t("Bluetooth LE", "ব্লুটুথ LE"); Link.WIFI -> t("Wi-Fi", "ওয়াই-ফাই"); Link.USB -> t("USB cable", "ইউএসবি ক্যাবল") }) })
                        }
                    }
                }
                item {
                    Text(when (link) {
                        Link.BLUETOOTH -> t("Plug an ELM327 Bluetooth dongle into the OBD-II port (under the steering), turn ignition ON, and pair it in phone Bluetooth settings (PIN usually 1234 or 0000).",
                            "ELM327 ব্লুটুথ ডঙ্গল OBD-II পোর্টে (স্টিয়ারিং এর নিচে) লাগান, চাবি ON করুন এবং ফোনের ব্লুটুথ সেটিংসে পেয়ার করুন (পিন সাধারণত ১২৩৪ বা ০০০০)।")
                        Link.BLE -> t("For \"BLE\" or \"iPhone\" adapters (Vgate iCar Pro BLE, OBDLink CX…). Plug it in, turn the ignition ON and scan. Don't pair it in phone settings.",
                            "\"BLE\" বা \"iPhone\" অ্যাডাপ্টারের জন্য (Vgate iCar Pro BLE, OBDLink CX…)। লাগিয়ে চাবি ON করে স্ক্যান করুন। ফোনের সেটিংসে পেয়ার করবেন না।")
                        Link.WIFI -> t("Plug the Wi-Fi adapter in, turn the ignition ON, then connect the phone's Wi-Fi to its network (often \"WiFi_OBDII\" or \"V-LINK\"). Come back here and tap Connect.",
                            "ওয়াই-ফাই অ্যাডাপ্টার লাগিয়ে চাবি ON করুন, তারপর ফোনের ওয়াই-ফাই এর নেটওয়ার্কে যুক্ত করুন (প্রায়ই \"WiFi_OBDII\" বা \"V-LINK\")। এখানে ফিরে কানেক্ট চাপুন।")
                        Link.USB -> t("Plug the ELM327 USB cable into the OBD-II port (under the steering) and into your phone with an OTG adapter, then turn the ignition ON. Allow access when Android asks.",
                            "ELM327 ইউএসবি ক্যাবল OBD-II পোর্টে (স্টিয়ারিং এর নিচে) লাগান, OTG অ্যাডাপ্টার দিয়ে ফোনে লাগান, তারপর চাবি ON করুন। অ্যান্ড্রয়েড অনুমতি চাইলে দিন।")
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (link) {
                    Link.BLUETOOTH -> {
                        if (!btGranted) item { Button(onClick = { btLauncher.launch(btPerms) }) { Text(t("Allow Bluetooth", "ব্লুটুথ অনুমতি দিন")) } }
                        else if (bonded.isEmpty()) item { Text(t("No paired devices. Pair the dongle first.", "কোনো পেয়ার করা ডিভাইস নেই। আগে ডঙ্গল পেয়ার করুন।")) }
                        items(bonded, key = { "bt" + it.address }) { d ->
                            AdapterRow(Icons.Default.Bluetooth, d.name ?: d.address, d.address, enabled = busy == null) { connectBt(d) }
                        }
                    }
                    Link.BLE -> {
                        item {
                            if (!bleGranted) Button(onClick = { bleLauncher.launch(blePerms) }) { Text(t("Allow Bluetooth", "ব্লুটুথ অনুমতি দিন")) }
                            else Button(enabled = !bleScanning && busy == null, onClick = { bleScanning = true }) {
                                if (bleScanning) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                                Text(if (bleScanning) t("Scanning…", "খোঁজা হচ্ছে…") else t("Scan for adapters", "অ্যাডাপ্টার খুঁজুন"))
                            }
                        }
                        val sorted = bleFound.sortedWith(compareByDescending<BleElm.Found> { it.looksLikeObd }.thenByDescending { it.rssi })
                        items(sorted, key = { "ble" + it.device.address }) { f ->
                            AdapterRow(Icons.Default.BluetoothSearching, f.name, if (f.looksLikeObd) t("Looks like an OBD adapter", "OBD অ্যাডাপ্টার মনে হচ্ছে") else f.device.address, enabled = busy == null) {
                                bleScanning = false; connectBle(f.device, f.name)
                            }
                        }
                    }
                    Link.WIFI -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                OutlinedTextField(host, { host = it.trim() }, label = { Text(t("Address", "ঠিকানা")) }, singleLine = true, modifier = Modifier.weight(2f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text(t("Port", "পোর্ট")) }, singleLine = true, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            Button(enabled = busy == null && host.isNotBlank() && port.toIntOrNull() != null, onClick = { connectWifi(host, port.toInt()) }) {
                                Icon(Icons.Default.Wifi, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Connect", "কানেক্ট"))
                            }
                            Text(t("Most adapters use 192.168.0.10 port 35000. If yours differs, it is printed on the box or in its manual.",
                                "বেশিরভাগ অ্যাডাপ্টার 192.168.0.10, পোর্ট 35000 ব্যবহার করে। আলাদা হলে বাক্সে বা ম্যানুয়ালে লেখা থাকে।"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Link.USB -> {
                        if (usbAdapters.isEmpty()) item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(t("Waiting for a USB OBD cable…", "ইউএসবি OBD ক্যাবলের অপেক্ষায়…"))
                            }
                        }
                        items(usbAdapters, key = { "usb" + it.key }) { a -> AdapterRow(Icons.Default.Usb, a.title, a.chip, enabled = busy == null) { connectUsb(a) } }
                        item { Text(t("Works with FTDI, CH340, CP210x and PL2303 cables. Your phone must support USB OTG.", "FTDI, CH340, CP210x ও PL2303 ক্যাবলে কাজ করে। ফোনে USB OTG সাপোর্ট থাকতে হবে।"), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item { Text(t("Tip: buy a tested dongle. Cheap clones (v2.1) often fail on newer cars.", "টিপ: পরীক্ষিত ডঙ্গল কিনুন। সস্তা ক্লোন (v2.1) নতুন গাড়িতে প্রায়ই কাজ করে না।"), style = MaterialTheme.typography.bodySmall) }
            } else {
                // ── What the car told us ──
                val i = info
                item {
                    AppSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t("Connected to $connected", "$connected এর সাথে যুক্ত"), style = MaterialTheme.typography.titleMedium)
                            i?.status?.let { s ->
                                StatusPill(if (s.milOn) t("Check-engine light ON · ${s.storedCodes} code(s)", "চেক-ইঞ্জিন লাইট জ্বলছে · ${n(s.storedCodes)}টি কোড")
                                    else t("Check-engine light off", "চেক-ইঞ্জিন লাইট বন্ধ"), if (s.milOn) Tone.BAD else Tone.GOOD)
                            }
                            (liveVolts ?: i?.adapterVolts)?.let { v ->
                                // Same rule as live data: 12.9 V is fine with the engine off but means "not charging" when running.
                                val ctxNow = LiveContext.of(live.associate { it.pid to it.value }, v)
                                val verdict = LiveRules.judge("RV", v, ctxNow, profile)
                                val tone = when (verdict.level) { LiveLevel.BAD -> Tone.BAD; LiveLevel.WARN -> Tone.WARN; LiveLevel.OK -> Tone.GOOD }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t("Battery", "ব্যাটারি") + ": " + "%.1f V".format(v) + " · " + (if (ctxNow.running) t("engine running", "ইঞ্জিন চালু") else t("engine off", "ইঞ্জিন বন্ধ")))
                                        Text(t("Normal", "স্বাভাবিক") + " " + (verdict.range ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusPill(when (tone) { Tone.BAD -> t("Low", "কম"); Tone.WARN -> t("Weak", "দুর্বল"); else -> t("Good", "ভালো") }, tone)
                                }
                                verdict.message?.let { Text(it.tr(), style = MaterialTheme.typography.bodySmall, color = tone.onContainer()) }
                            }
                            i?.protocol?.let { Text(t("Protocol", "প্রোটোকল") + ": $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (i != null && i.supportedPids.isNotEmpty()) Text(t("${i.supportedPids.size} readings supported", "${n(i.supportedPids.size)}টি রিডিং সাপোর্টেড"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            i?.vin?.let { vin ->
                                Text("VIN: $vin", style = MaterialTheme.typography.bodyMedium)
                                val clash = vehicle?.let { v -> st.snapshot.vehicles.any { o -> o.id != v.id && VehicleIdentity.normalize(o.chassisNo) == VehicleIdentity.normalize(vin) } } ?: true
                                if (vehicle != null && vehicle.chassisNo.isNullOrBlank() && !clash)
                                    TextButton(onClick = { vm.updateVehicle(vehicle.copy(chassisNo = vin)) }) { Text(t("Save as chassis no.", "চেসিস নম্বর হিসেবে সেভ করুন")) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                                Button(enabled = busy == null, onClick = {
                                    busy = lang.pick("Reading fault codes…", "ফল্ট কোড পড়া হচ্ছে…")
                                    scope.launch {
                                        runCatching { client.readCodes(dict) }.onSuccess { codes = it; vm.replaceFaultCodes(id, it.map { c -> c.code }) }.onFailure { error = it.message }
                                        pending = client.readPending(dict).filter { p -> codes?.none { it.code == p.code } ?: true }
                                        busy = null
                                    }
                                }) { Text(t("Read fault codes", "ফল্ট কোড পড়ুন")) }
                                OutlinedButton(onClick = { if (vm.allowed(ProFeature.LIVE_OBD_GRAPHS)) liveOn = !liveOn else showPro = true }) {
                                    Text(if (liveOn) t("Stop live", "লাইভ বন্ধ") else t("Live data", "লাইভ ডেটা"))
                                }
                            }
                            i?.adapterVolts?.let { v ->
                                TextButton(onClick = { vm.addVoltage(VoltageEntity(vehicleId = id, volts = v, nominalVolts = 12.0, epochDay = today())) }) {
                                    Text(t("Save battery voltage to health record", "ব্যাটারি ভোল্টেজ হেলথ রেকর্ডে সেভ করুন"))
                                }
                            }
                        }
                    }
                }
                codes?.let { list ->
                    item { SectionHeader(t("Fault codes", "ফল্ট কোড")) }
                    if (list.isEmpty() && pending.isEmpty()) item { StatusBanner(Icons.Default.Verified, Tone.GOOD, t("No fault codes stored ✓", "কোনো ফল্ট কোড নেই ✓"), t("The engine computer has nothing to report.", "ইঞ্জিন কম্পিউটারে কোনো সমস্যা জমা নেই।")) }
                    items(list, key = { "s" + it.code }) { c -> CodeCard(c, pendingCode = false) }
                    items(pending, key = { "p" + it.code }) { c -> CodeCard(c, pendingCode = true) }
                    if (list.isNotEmpty()) item {
                        OutlinedButton(onClick = { scope.launch { runCatching { client.clearCodes() }; codes = emptyList(); pending = emptyList(); vm.replaceFaultCodes(id, emptyList()) } }) {
                            Text(t("Clear codes (after repair)", "কোড মুছুন (মেরামতের পর)"))
                        }
                    }
                }
                if (live.isNotEmpty()) {
                    val red = verdicts.values.filter { it.level == LiveLevel.BAD }
                    val amber = verdicts.values.filter { it.level == LiveLevel.WARN }
                    if (red.isNotEmpty()) item {
                        StatusBanner(Icons.Default.Error, Tone.BAD, red.first().message?.tr() ?: "",
                            red.drop(1).joinToString("\n") { it.message?.tr() ?: "" }.ifBlank { t("Check this before driving further.", "আর চালানোর আগে এটি দেখুন।") })
                    } else if (amber.isNotEmpty()) item {
                        StatusBanner(Icons.Default.Warning, Tone.WARN, t("${amber.size} reading(s) need attention", "${n(amber.size)}টি রিডিং দেখা দরকার"),
                            amber.joinToString("\n") { it.message?.tr() ?: "" })
                    } else if (monitor.samples >= 10) item {
                        StatusBanner(Icons.Default.Verified, Tone.GOOD, t("All readings normal", "সব রিডিং স্বাভাবিক"),
                            t("Values must stay out of range for a few seconds before they're flagged.", "কয়েক সেকেন্ড সীমার বাইরে থাকলে তবেই সতর্ক করা হয়।"))
                    }
                    item {
                        SectionHeader(t("Live data", "লাইভ ডেটা")) {
                            TextButton(enabled = !saved && monitor.samples >= 20, onClick = { saveCheck() }) {
                                Text(if (saved) t("Saved ✓", "সেভ হয়েছে ✓") else t("Save check", "চেক সেভ করুন"))
                            }
                        }
                    }
                    item {
                        val tiles = live.map { it.pid to it } + (liveVolts?.let { listOf("RV" to LiveData("RV", com.garidoctor.core.obd.ObdDecoder.label("RV").first, it, "V")) } ?: emptyList())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), maxItemsInEachRow = 2) {
                            tiles.forEach { (key, l) -> LiveTile(l, verdicts[key], Modifier.weight(1f)) }
                        }
                    }
                    item {
                        Text(t("Saved checks appear in History and count in the health score for 30 days. A check is saved automatically when you stop live data after 30 seconds or more.",
                            "সেভ করা চেক ইতিহাসে দেখাবে এবং ৩০ দিন হেলথ স্কোরে গণনা হবে। ৩০ সেকেন্ড বা বেশি চালানোর পর লাইভ বন্ধ করলে চেক নিজেই সেভ হয়।"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showPro) ProDialog(vm, t("Live OBD graphs are a Pro feature.", "লাইভ OBD গ্রাফ প্রো ফিচার।")) { showPro = false }
}

@Composable
private fun AdapterRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onConnect: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, leadingContent = { Icon(icon, null) },
        trailingContent = { TextButton(enabled = enabled, onClick = onConnect) { Text(t("Connect", "কানেক্ট")) } })
}

@Composable
private fun CodeCard(c: DiagnosticTroubleCode, pendingCode: Boolean) {
    SectionCard(c.code) {
        if (pendingCode) StatusPill(t("Pending: seen once, not confirmed yet", "পেন্ডিং: একবার দেখা গেছে, এখনো নিশ্চিত নয়"), Tone.WARN)
        Text(c.describe().tr(), fontWeight = FontWeight.SemiBold,
            color = if ((c.info?.severity ?: 2) >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        c.info?.advice?.let { Text(it.tr(), style = MaterialTheme.typography.bodySmall) }
    }
}

/** One live reading: value, safe range, and colour + advice when it's out of range. */
@Composable
private fun LiveTile(l: LiveData, v: LiveVerdict?, modifier: Modifier) {
    val tone = when (v?.level) { LiveLevel.BAD -> Tone.BAD; LiveLevel.WARN -> Tone.WARN; else -> null }
    AppSurface(modifier, radius = 20.dp, tint = tone?.container()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val fg = tone?.onContainer() ?: MaterialTheme.colorScheme.onSurfaceVariant
            Text(l.name.tr(), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (l.unit == "rpm" || l.unit == "km/h" || l.unit == "s") "%.0f".format(l.value) else "%.1f".format(l.value),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, color = tone?.onContainer() ?: MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Text(l.unit, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.padding(bottom = 4.dp))
            }
            v?.range?.let { Text(t("Normal", "স্বাভাবিক") + " " + it, style = MaterialTheme.typography.labelSmall, color = fg) }
            v?.message?.let { Text(it.tr(), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 3) }
        }
    }
}
