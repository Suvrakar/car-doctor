@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.garidoctor.app.ai.AiException
import com.garidoctor.app.ocr.TextReader
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.ml.WarningLightClassifier
import com.garidoctor.app.ui.*
import com.garidoctor.core.symptom.WarningLight
import com.garidoctor.core.symptom.WarningLights

@Composable
fun WarningLightsScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val isBike = v.templateId in setOf("motorcycle", "scooter", "e_scooter")
    val classifier = remember { WarningLightClassifier(ctx) }
    DisposableEffect(Unit) { onDispose { classifier.close() } }
    var selected by remember { mutableStateOf<WarningLight?>(null) }
    var guesses by remember { mutableStateOf<List<Pair<WarningLight, Float>>>(emptyList()) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending
        if (ok && u != null) guesses = classifier.classify(u).mapNotNull { (l, p) -> WarningLights.byId(l)?.let { it to p } }.take(3)
    }
    val list = WarningLights.all.filter { if (isBike) it.forBikes else it.forCars }
    // Cloud identification: the AI button opens the camera, waits for the photo, then asks Gemini,
    // so quota and network errors surface on the button itself.
    var aiPending by remember { mutableStateOf<Uri?>(null) }
    var photoWait by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val aiCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> photoWait?.complete(if (ok) aiPending else null) }

    Screen(t("Warning lights", "ওয়ার্নিং লাইট"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (classifier.available) item {
                Button(onClick = { val (u, _) = Photos.newPhoto(ctx, "light"); pending = u; camera.launch(u) }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Scan with camera (on-device)", "ক্যামেরা দিয়ে স্ক্যান (অফলাইন)"))
                }
            } else {
                if (vm.ai.enabled) item {
                    var aiError by remember { mutableStateOf<String?>(null) }
                    CloudAiButton(vm, t("Photograph the light and identify it with AI", "লাইটের ছবি তুলে AI দিয়ে চিনুন"), onError = { aiError = it }) {
                        val wait = CompletableDeferred<Uri?>(); photoWait = wait
                        val (u, _) = Photos.newPhoto(ctx, "light"); aiPending = u; aiCamera.launch(u)
                        val uri = wait.await() ?: return@CloudAiButton
                        val bmp = TextReader.bitmap(ctx, uri) ?: throw AiException(AiException.Reason.FAILED)
                        val (lid, p) = vm.ai.warningLightCloud(bmp) ?: throw AiException(AiException.Reason.FAILED)
                        WarningLights.byId(lid)?.let { w -> guesses = listOf(w to p.toFloat()); selected = w }
                    }
                    aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                item { Text(t("Or pick the symbol you see on the dashboard:", "অথবা ড্যাশবোর্ডে যে চিহ্ন দেখছেন সেটি বেছে নিন:"), style = MaterialTheme.typography.titleSmall) }
            }
            if (guesses.isNotEmpty()) item {
                SectionCard(t("Camera thinks it is", "ক্যামেরার অনুমান")) {
                    guesses.forEach { (w, p) -> TextButton(onClick = { selected = w }) { Text("${w.name.tr()} · ${(p * 100).toInt()}%") } }
                }
            }
            items(list, key = { it.id }) { w ->
                ListItem(headlineContent = { Text(w.name.tr()) }, supportingContent = { UrgencyChip(w.urgency) }, modifier = Modifier.clickableItem { selected = w })
            }
        }
    }
    selected?.let { w ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(w.name.tr()) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { UrgencyChip(w.urgency); Text(w.meaning.tr()) }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text(t("OK", "ঠিক আছে")) } })
    }
}
