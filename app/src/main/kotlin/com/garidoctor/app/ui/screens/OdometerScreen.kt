@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.ocr.OdometerReader
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Bad
import com.garidoctor.core.ocr.OdometerCandidate
import kotlinx.coroutines.launch

/** Update the odometer without typing: photo (ML Kit OCR, offline) or GPS trip estimate. */
@SuppressLint("MissingPermission")
@Composable
fun OdometerScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    var manual by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<OdometerCandidate>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    val lowerMsg = t("Reading is lower than the current odometer.", "রিডিং বর্তমান ওডোমিটারের চেয়ে কম।")
    val savedMsg = t("Saved ✓", "সেভ হয়েছে ✓")
    val save: (Int, String) -> Unit = { km, src -> vm.updateOdometer(id, km, src) { ok -> msg = if (ok) savedMsg else lowerMsg } }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending ?: return@rememberLauncherForActivityResult
        if (!ok) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            candidates = runCatching { OdometerReader.read(ctx, u, v.odometerKm) }.getOrDefault(emptyList())
            reading = false
            if (candidates.isEmpty()) msg = null
        }
    }

    // ---- GPS trip ----
    var tripOn by remember { mutableStateOf(false) }
    var tripMeters by remember { mutableDoubleStateOf(0.0) }
    var hasLoc by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLoc = it; if (it) tripOn = true }
    DisposableEffect(tripOn) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        var last: Location? = null
        val listener = LocationListener { loc ->
            if (loc.accuracy > 30f) return@LocationListener
            last?.let { prev ->
                val d = prev.distanceTo(loc)
                val dt = (loc.time - prev.time) / 1000.0
                // Ignore GPS jumps (> 50 m/s ≈ 180 km/h) and jitter (< 5 m).
                if (d >= 5 && dt > 0 && d / dt < 50) tripMeters += d
            }
            last = loc
        }
        if (tripOn && hasLoc) runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener, Looper.getMainLooper()) }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }

    Screen(t("Odometer", "ওডোমিটার"), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Current: ${"%,d".format(v.odometerKm)} km", "বর্তমান: ${n(v.odometerKm)} কিমি"), style = MaterialTheme.typography.headlineSmall)
            msg?.let { Text(it, color = if (it == lowerMsg) Bad else MaterialTheme.colorScheme.primary) }
            SectionCard(t("Photo the meter", "মিটারের ছবি তুলুন")) {
                Text(t("Hold the phone close so the odometer digits fill the frame. Reading happens on your phone, offline.", "ওডোমিটারের সংখ্যাগুলো ফ্রেমে বড় করে তুলুন। ফোনেই অফলাইনে পড়া হয়।"), style = MaterialTheme.typography.bodySmall)
                Button(enabled = !reading, onClick = { val (u, _) = Photos.newPhoto(ctx, "odo"); pending = u; candidates = emptyList(); camera.launch(u) }) {
                    Text(if (reading) t("Reading…", "পড়া হচ্ছে…") else t("Take photo", "ছবি তুলুন"))
                }
                if (candidates.isNotEmpty()) {
                    Text(t("Tap the correct reading:", "সঠিক রিডিংয়ে ট্যাপ করুন:"))
                    candidates.forEach { c -> OutlinedButton(onClick = { save(c.km, "photo"); candidates = emptyList() }) { Text("${n(c.km)} ${t("km", "কিমি")}", fontWeight = FontWeight.Bold) } }
                }
            }
            SectionCard(t("Type it", "লিখে দিন")) {
                NumberField(manual, { manual = it }, t("Odometer (km)", "ওডোমিটার (কিমি)"))
                Button(enabled = manual.toIntOrNull() != null, onClick = { save(manual.toInt(), "manual"); manual = "" }) { Text(t("Save", "সেভ")) }
            }
            SectionCard(t("GPS trip (estimate)", "জিপিএস ট্রিপ (আনুমানিক)")) {
                Text(t("Keep this screen open while riding; km are added to the odometer when you stop.", "চালানোর সময় এই স্ক্রিন খোলা রাখুন; থামলে কিমি ওডোমিটারে যোগ হবে।"), style = MaterialTheme.typography.bodySmall)
                Text("${"%.1f".format(tripMeters / 1000)} ${t("km", "কিমি")}", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (tripOn) {
                            tripOn = false
                            val add = (tripMeters / 1000).toInt()
                            if (add > 0) save(v.odometerKm + add, "gps")
                            tripMeters = 0.0
                        } else if (hasLoc) tripOn = true else locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Text(if (tripOn) t("Stop & add", "থামুন ও যোগ করুন") else t("Start trip", "ট্রিপ শুরু")) }
                    if (tripOn) OutlinedButton(onClick = { tripOn = false; tripMeters = 0.0 }) { Text(t("Discard", "বাতিল")) }
                }
            }
        }
    }
}
