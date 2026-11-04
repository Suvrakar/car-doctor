@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.ads.Ads
import com.garidoctor.app.ai.AiException
import com.garidoctor.app.data.FuelEntity
import com.garidoctor.app.data.ServiceEntity
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ocr.TextReader
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.core.ai.*
import com.garidoctor.core.model.DocumentType
import com.garidoctor.core.model.Lang
import com.garidoctor.core.template.VehicleTemplate
import kotlinx.coroutines.launch

// ───────────────────────────── Shared pieces ─────────────────────────────

/** Bilingual messages for cloud-AI failures, resolved while composing (coroutines can't call t()). */
@Composable
private fun aiMessages(): Map<AiException.Reason, String> = mapOf(
    AiException.Reason.NOT_CONFIGURED to t("AI reading needs Firebase set up in this build.", "এই বিল্ডে AI পড়ার জন্য Firebase সেটআপ দরকার।"),
    AiException.Reason.DISABLED to t("AI reading is paused right now. Please fill it in by hand.", "AI পড়া এখন বন্ধ আছে। হাতে লিখে দিন।"),
    AiException.Reason.NO_CONSENT to t("Allow AI reading first.", "আগে AI পড়ার অনুমতি দিন।"),
    AiException.Reason.QUOTA to t("You've used today's AI reads. Watch a short ad for one more, or get Pro.", "আজকের AI পড়া শেষ। আরেকটির জন্য ছোট বিজ্ঞাপন দেখুন, বা প্রো নিন।"),
    AiException.Reason.FAILED to t("AI couldn't read it. Check your internet and try again, or fill it in by hand.", "AI পড়তে পারেনি। ইন্টারনেট দেখে আবার চেষ্টা করুন, বা হাতে লিখে দিন।"),
)

/** Asked once, before the first photo or voice text is sent to Google's AI. */
@Composable
fun AiConsentDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(t("Use AI to read this?", "AI দিয়ে পড়াবেন?")) },
        text = {
            Text(t("The photo (or what you said) is sent to Google's Gemini AI to read it. Your logs and other photos stay on this phone. You can turn this off in Settings.",
                "ছবি (বা আপনার বলা কথা) পড়ার জন্য গুগলের Gemini AI-তে পাঠানো হবে। আপনার লগ ও অন্য ছবি এই ফোনেই থাকবে। সেটিংসে এটি বন্ধ করতে পারবেন।"))
        },
        confirmButton = { Button(onClick = onAllow) { Text(t("Allow", "অনুমতি দিন")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Not now", "এখন না")) } })
}

/**
 * "Read with AI" button: shows today's remaining reads, asks consent the first time, runs
 * [onRun], and on an exhausted quota offers a rewarded ad for one more read.
 */
@Composable
fun CloudAiButton(vm: MainViewModel, label: String, onError: (String) -> Unit, onRun: suspend () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent by vm.aiConsent.collectAsStateWithLifecycle()
    val quota by vm.ai.quota.collectAsStateWithLifecycle()
    var askConsent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outOfReads by remember { mutableStateOf(false) }
    val msgs = aiMessages()
    val adNotReady = t("The ad isn't ready yet. Try again in a moment.", "বিজ্ঞাপন এখনো প্রস্তুত নয়। একটু পরে চেষ্টা করুন।")
    LaunchedEffect(Unit) { vm.ai.refreshQuota() }
    if (!vm.ai.enabled) return
    val run: () -> Unit = {
        busy = true
        scope.launch {
            try { onRun() } catch (e: AiException) {
                if (e.reason == AiException.Reason.QUOTA) outOfReads = true
                onError(msgs[e.reason] ?: e.message.orEmpty())
            } catch (e: Exception) {
                onError(msgs[AiException.Reason.FAILED].orEmpty())
            } finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(enabled = !busy, onClick = { if (consent) run() else askConsent = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(Spacing.s))
            Text(label)
        }
        quota?.let { q ->
            Text(t("${q.remaining} AI reads left today", "আজ ${n(q.remaining)}টি AI পড়া বাকি"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (outOfReads) TextButton(onClick = {
            val act = ctx.findActivity()
            val shown = act != null && Ads.showRewarded(act) { scope.launch { if (vm.ai.grantAdBonus()) outOfReads = false } }
            if (!shown) onError(adNotReady)
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Watch an ad for 1 more read", "বিজ্ঞাপন দেখে আরেকটি পড়ুন")) }
    }
    if (askConsent) AiConsentDialog(onAllow = { askConsent = false; vm.setAiConsent(true); run() }, onDismiss = { askConsent = false })
}

@Composable
private fun BusyDialog(text: String) {
    Dialog(onDismissRequest = {}) {
        AppSurface(radius = 28.dp) {
            Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(Spacing.l)); Text(text)
            }
        }
    }
}

@Composable
private fun SourceNote(source: String, complete: Boolean) {
    val (txt, tone) = when (source) {
        "ai" -> t("Read by AI. Please check before saving.", "AI পড়েছে। সেভের আগে দেখে নিন।") to MaterialTheme.colorScheme.primary
        "nano" -> t("Read on this phone with on-device AI.", "ফোনের নিজস্ব AI দিয়ে পড়া হয়েছে।") to MaterialTheme.colorScheme.primary
        else -> (if (complete) t("Read on this phone. Please check before saving.", "ফোনেই পড়া হয়েছে। সেভের আগে দেখে নিন।")
                 else t("Couldn't read everything. Fill in the rest, or try AI.", "সব পড়া যায়নি। বাকিটা লিখুন, বা AI দিয়ে চেষ্টা করুন।")) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(txt, style = MaterialTheme.typography.bodySmall, color = tone)
}

/** Launches the camera once and hands back the photo; null if the user backs out. */
@Composable
private fun rememberOneShotCamera(prefix: String, onResult: (Pair<Uri, String>?) -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> onResult(if (ok) pending else null) }
    LaunchedEffect(Unit) { val p = Photos.newPhoto(ctx, prefix); pending = p; camera.launch(p.first) }
}

private fun merge(a: ScannedDocument, b: ScannedDocument?) = if (b == null) a else
    ScannedDocument(b.type ?: a.type, b.number ?: a.number, b.expiryEpochDay ?: a.expiryEpochDay, maxOf(a.confidence, b.confidence), b.source)

// ───────────────────────────── Paper scanner ─────────────────────────────

/**
 * Photo of a paper → on-device OCR + rules → (Gemini Nano on the text, if the phone has it) →
 * review → saved with reminders. Cloud Gemini on the photo only if the user asks.
 */
@Composable
fun PaperScanFlow(vm: MainViewModel, vehicleId: Long, allowed: List<DocumentType>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var reading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScannedDocument?>(null) }
    rememberOneShotCamera("paper") { p ->
        if (p == null) { onClose(); return@rememberOneShotCamera }
        photo = p; reading = true
        scope.launch {
            val ocr = runCatching { TextReader.read(ctx, p.first) }.getOrDefault("")
            var doc = DocumentTextParser.parse(ocr, today())
            if (!doc.complete) doc = merge(doc, runCatching { vm.ai.paperOnDevice(ocr) }.getOrNull())
            result = doc; reading = false
        }
    }
    if (reading) BusyDialog(t("Reading the paper…", "কাগজ পড়া হচ্ছে…"))
    val r = result ?: return
    var type by remember(r) { mutableStateOf(r.type?.takeIf { it in allowed }) }
    var number by remember(r) { mutableStateOf(r.number ?: "") }
    var expiry by remember(r) { mutableStateOf(r.expiryEpochDay) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(t("Check the paper details", "কাগজের তথ্য দেখে নিন")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SourceNote(r.source, r.complete)
            Text(t("Paper", "কাগজ"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                allowed.forEach { d -> FilterChip(selected = type == d, onClick = { type = d }, label = { Text(d.label.tr()) }) }
            }
            DateButton(expiry ?: (today() + 365), { expiry = it }, if (expiry == null) t("Set expiry date", "মেয়াদের তারিখ দিন") else t("Expires", "মেয়াদ শেষ"))
            OutlinedTextField(number, { number = it }, label = { Text(t("Number (optional)", "নম্বর (ঐচ্ছিক)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (r.source == "device" && !(r.complete && r.confidence >= 0.85)) {
                val p = photo
                if (p != null) CloudAiButton(vm, t("Read with AI", "AI দিয়ে পড়ুন"), onError = { error = it }) {
                    val bmp = TextReader.bitmap(ctx, p.first) ?: throw AiException(AiException.Reason.FAILED)
                    result = merge(r, vm.ai.paperCloud(bmp) ?: throw AiException(AiException.Reason.FAILED))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        Button(enabled = type != null && expiry != null, onClick = {
            vm.savePaper(vehicleId, type!!, expiry!!, number.trim().ifBlank { null }); onClose()
        }) { Text(t("Save & remind me", "সেভ করে রিমাইন্ডার দিন")) }
    }, dismissButton = { TextButton(onClick = onClose) { Text(t("Cancel", "বাতিল")) } })
}

// ───────────────────────────── Receipt scanner ─────────────────────────────

@Composable
fun ReceiptScanFlow(vm: MainViewModel, vehicle: VehicleEntity, tpl: VehicleTemplate, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = remember(tpl) { tpl.parts.map { it.id }.toSet() }
    var photo by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var reading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScannedReceipt?>(null) }
    rememberOneShotCamera("receipt") { p ->
        if (p == null) { onClose(); return@rememberOneShotCamera }
        photo = p; reading = true
        scope.launch {
            val ocr = runCatching { TextReader.read(ctx, p.first) }.getOrDefault("")
            var rec = ReceiptTextParser.parse(ocr, parts)
            if (rec.serviceItems.isEmpty()) runCatching { vm.ai.receiptOnDevice(ocr, parts) }.getOrNull()?.let { rec = it }
            result = rec; reading = false
        }
    }
    if (reading) BusyDialog(t("Reading the bill…", "বিল পড়া হচ্ছে…"))
    val r = result ?: return
    val items = r.serviceItems
    val checked = remember(r) { mutableStateListOf<Boolean>().apply { repeat(items.size) { add(true) } } }
    var day by remember(r) { mutableLongStateOf(r.epochDay ?: today()) }
    var km by remember(r) { mutableStateOf((r.odometerKm ?: vehicle.odometerKm).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(t("Check the bill", "বিল দেখে নিন")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SourceNote(r.source, items.isNotEmpty())
            if (items.isEmpty()) Text(t("No parts found on this bill.", "এই বিলে কোনো পার্টস পাওয়া যায়নি।"))
            items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked[i], { v -> checked[i] = v })
                    Column(Modifier.weight(1f)) {
                        Text(tpl.part(item.partId!!)?.name?.tr() ?: item.label, style = MaterialTheme.typography.bodyLarge)
                        Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    item.amountBdt?.let { a -> Text("৳${n(a)}", style = MaterialTheme.typography.labelLarge) }
                }
            }
            r.totalBdt?.let { Text(t("Total: ৳$it", "মোট: ৳${n(it)}"), style = MaterialTheme.typography.titleSmall) }
            DateButton(day, { day = it }, t("Date", "তারিখ"))
            NumberField(km, { km = it }, t("Odometer (km)", "ওডোমিটার (কিমি)"))
            if (r.source == "device") {
                val p = photo
                if (p != null) CloudAiButton(vm, t("Read with AI", "AI দিয়ে পড়ুন"), onError = { error = it }) {
                    val bmp = TextReader.bitmap(ctx, p.first) ?: throw AiException(AiException.Reason.FAILED)
                    result = vm.ai.receiptCloud(bmp, parts) ?: throw AiException(AiException.Reason.FAILED)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        Button(enabled = items.indices.any { checked.getOrElse(it) { false } } && km.toIntOrNull() != null, onClick = {
            val chosen = items.filterIndexed { i, _ -> checked[i] }
            // If the bill has only a total, put it on the single logged item.
            val soleTotal = if (chosen.size == 1 && chosen[0].amountBdt == null) r.totalBdt else null
            chosen.forEach { item ->
                vm.addService(ServiceEntity(vehicleId = vehicle.id, partId = item.partId!!, odometerKm = km.toInt(), epochDay = day,
                    costBdt = item.amountBdt ?: soleTotal, note = r.garage ?: item.label, receiptPath = photo?.second))
            }
            onClose()
        }) { Text(t("Log services", "সার্ভিস লগ করুন")) }
    }, dismissButton = { TextButton(onClick = onClose) { Text(t("Cancel", "বাতিল")) } })
}

// ───────────────────────────── Voice logging ─────────────────────────────

@Composable
fun VoiceLogFlow(vm: MainViewModel, vehicle: VehicleEntity, tpl: VehicleTemplate, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val parts = remember(tpl) { tpl.parts.map { it.id }.toSet() }
    var transcript by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<VoiceAction>(VoiceAction.Unknown) }
    var noSpeech by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fuelAmount by remember { mutableStateOf("") }

    fun interpret(text: String) {
        transcript = text
        action = VoiceCommandParser.parse(text, parts)
        if (action == VoiceAction.Unknown) scope.launch { action = runCatching { vm.ai.voiceOnDevice(text, parts) }.getOrDefault(VoiceAction.Unknown) }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = if (res.resultCode == Activity.RESULT_OK) res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        if (text == null) { if (transcript == null) onClose() } else interpret(text)
    }
    val prompt = t("Say what you did, e.g. “changed oil for 400 taka at 24500 km”", "যা করেছেন বলুন, যেমন “২৪৫০০ কিমিতে ৪০০ টাকায় তেল বদলালাম”")
    val listen: () -> Unit = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == Lang.BN) "bn-BD" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try { speech.launch(i) } catch (_: ActivityNotFoundException) { noSpeech = true; transcript = "" }
    }
    LaunchedEffect(Unit) { listen() }
    val text = transcript ?: return

    var edit by remember(text) { mutableStateOf(text) }
    val a = action
    AlertDialog(onDismissRequest = onClose, title = { Text(t("Log by voice", "কথা বলে লগ")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (noSpeech) Text(t("Speech input isn't available on this phone. Type it instead.", "এই ফোনে কথা বলে লেখা নেই। লিখে দিন।"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(edit, { edit = it }, label = { Text(t("What you said", "আপনি যা বলেছেন")) }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { interpret(edit) }) { Text(t("Read", "পড়ুন")) } })
            Text(when (a) {
                is VoiceAction.Service -> t("Service: ", "সার্ভিস: ") + a.partIds.joinToString { tpl.part(it)?.name?.tr() ?: it } +
                    (a.costBdt?.let { " · ৳${n(it)}" } ?: "") + (a.odometerKm?.let { " · ${n(it)} ${t("km", "কিমি")}" } ?: "")
                is VoiceAction.Fuel -> (if (a.cng) t("CNG fill", "সিএনজি") else t("Fuel / charge", "জ্বালানি / চার্জ")) +
                    (a.amount?.let { " · $it" } ?: "") + (a.costBdt?.let { " · ৳${n(it)}" } ?: "") + (a.odometerKm?.let { " · ${n(it)} ${t("km", "কিমি")}" } ?: "")
                is VoiceAction.Odometer -> t("Odometer: ", "ওডোমিটার: ") + "${n(a.km)} ${t("km", "কিমি")}"
                VoiceAction.Unknown -> t("I couldn't tell what to log. Edit the text and tap Read, or try AI.", "কী লগ করবো বুঝিনি। লেখাটি ঠিক করে 'পড়ুন' চাপুন, বা AI চেষ্টা করুন।")
            }, style = MaterialTheme.typography.titleSmall)
            if (a is VoiceAction.Fuel && a.amount == null) NumberField(fuelAmount, { fuelAmount = it }, if (a.cng) t("CNG (m³)", "সিএনজি (ঘনমিটার)") else t("Litres (or 1 = full charge)", "লিটার (বা ১ = ফুল চার্জ)"), decimal = true)
            if (a == VoiceAction.Unknown && edit.isNotBlank()) CloudAiButton(vm, t("Understand with AI", "AI দিয়ে বুঝুন"), onError = { error = it }) {
                action = vm.ai.voiceCloud(edit, parts)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = listen) { Text(t("Speak again", "আবার বলুন")) }
        }
    }, confirmButton = {
        val canSave = when (a) { is VoiceAction.Fuel -> (a.amount ?: fuelAmount.toDoubleOrNull()) != null; VoiceAction.Unknown -> false; else -> true }
        Button(enabled = canSave, onClick = {
            when (a) {
                is VoiceAction.Service -> a.partIds.forEachIndexed { i, pid ->
                    vm.addService(ServiceEntity(vehicleId = vehicle.id, partId = pid, odometerKm = a.odometerKm ?: vehicle.odometerKm, epochDay = today(),
                        costBdt = if (i == 0) a.costBdt else null, note = edit.take(80)))
                }
                is VoiceAction.Fuel -> vm.addFuel(FuelEntity(vehicleId = vehicle.id, odometerKm = a.odometerKm ?: vehicle.odometerKm,
                    amount = a.amount ?: fuelAmount.toDouble(), priceBdt = a.costBdt, full = a.full, epochDay = today()))
                is VoiceAction.Odometer -> vm.updateOdometer(vehicle.id, a.km, "voice")
                VoiceAction.Unknown -> {}
            }
            onClose()
        }) { Text(t("Save", "সেভ")) }
    }, dismissButton = { TextButton(onClick = onClose) { Text(t("Cancel", "বাতিল")) } })
}

