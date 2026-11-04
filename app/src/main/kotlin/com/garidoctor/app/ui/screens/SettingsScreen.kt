@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.BuildConfig
import com.garidoctor.app.ui.*
import com.garidoctor.core.model.Lang

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: (() -> Unit)?, bottomBar: @Composable () -> Unit = {}) {
    val lang by vm.lang.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    val contacts by vm.sos.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }
    Screen(t("Settings", "সেটিংস"), onBack, large = true, bottomBar = bottomBar) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceSection(vm)
            SectionCard(t("Language", "ভাষা")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(lang == Lang.BN, { vm.setLang(Lang.BN); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("bn")) }, { Text("বাংলা") })
                    FilterChip(lang == Lang.EN, { vm.setLang(Lang.EN); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }, { Text("English") })
                }
            }
            SectionCard(t("GariDoctor Pro", "গাড়িডাক্তার প্রো")) {
                Text(if (pro) t("Pro is active ✓", "প্রো চালু আছে ✓") else t("Multiple vehicles, PDF passport, live OBD graphs, fleet mode, no ads.", "একাধিক গাড়ি, পিডিএফ পাসপোর্ট, লাইভ OBD গ্রাফ, ফ্লিট মোড, বিজ্ঞাপন নেই।"))
                if (!pro) Button(onClick = { showPro = true }) { Text(t("Unlock / redeem code", "আনলক / কোড রিডিম")) }
            }
            AiSection(vm)
            BackupSection(vm)
            SectionCard(t("SOS contacts", "SOS কন্টাক্ট")) {
                if (contacts.isEmpty()) Text(t("Add contacts from the SOS screen.", "SOS স্ক্রিন থেকে কন্টাক্ট যোগ করুন।"), style = MaterialTheme.typography.bodySmall)
                contacts.forEach { c ->
                    Row { Text("${c.name} · ${c.phone}", Modifier.weight(1f).padding(top = 12.dp)); TextButton(onClick = { vm.setSos(contacts - c) }) { Text(t("Remove", "সরান")) } }
                }
            }
            SectionCard(t("Privacy", "গোপনীয়তা")) {
                Text(t("Your logs and photos stay on this phone. Nothing is uploaded unless you turn on backup.", "আপনার লগ ও ছবি এই ফোনেই থাকে। ব্যাকআপ চালু না করলে কিছুই আপলোড হয় না।"), style = MaterialTheme.typography.bodySmall)
                Text(t("Diagnoses are possible causes only. Always confirm with a mechanic.", "রোগনির্ণয় শুধু সম্ভাব্য কারণ। সবসময় মেকানিক দিয়ে নিশ্চিত হোন।"), style = MaterialTheme.typography.bodySmall)
            }
            Text("GariDoctor ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (showPro) ProDialog(vm, t("Unlock everything once.", "একবারেই সব আনলক করুন।")) { showPro = false }
}

@Composable
private fun BackupSection(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    val okExport = t("Backup saved ✓", "ব্যাকআপ সেভ হয়েছে ✓")
    val okRestore = t("Restored vehicles: ", "ফেরত আনা গাড়ি: ")
    val failed = t("Failed: ", "ব্যর্থ: ")
    fun launchOp(block: suspend () -> String) {
        busy = true
        scope.launch { msg = runCatching { block() }.getOrElse { failed + (it.message ?: it.javaClass.simpleName) }; busy = false }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) launchOp {
            val text = vm.exportBackup()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            okExport
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = {
            launchOp {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
                okRestore + vm.restoreBackup(text)
            }
        }
    }
    SectionCard(t("Backup", "ব্যাকআপ")) {
        Text(t("Logs, papers and settings as a small text file. Photos stay on this phone.", "লগ, কাগজ ও সেটিংস একটি ছোট টেক্সট ফাইলে। ছবি এই ফোনেই থাকে।"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { exportLauncher.launch("garidoctor-backup-${com.garidoctor.app.data.today()}.json") }) { Text(t("Export file", "ফাইলে সেভ")) }
            OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(t("Restore file", "ফাইল থেকে ফেরত")) }
        }
        if (vm.cloudAvailable) {
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var signedIn by remember { mutableStateOf(vm.cloudEmail()) }
            HorizontalDivider()
            Text(t("Cloud backup (free)", "ক্লাউড ব্যাকআপ (ফ্রি)"), style = MaterialTheme.typography.titleSmall)
            if (signedIn == null) {
                OutlinedTextField(email, { email = it }, label = { Text(t("Email", "ইমেইল")) }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text(t("Password (6+ characters)", "পাসওয়ার্ড (৬+ অক্ষর)")) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && email.contains('@') && password.length >= 6, onClick = { launchOp { vm.cloudSignIn(email, password, false); signedIn = vm.cloudEmail(); "✓" } }) { Text(t("Sign in", "সাইন ইন")) }
                    OutlinedButton(enabled = !busy && email.contains('@') && password.length >= 6, onClick = { launchOp { vm.cloudSignIn(email, password, true); signedIn = vm.cloudEmail(); "✓" } }) { Text(t("Create account", "অ্যাকাউন্ট খুলুন")) }
                }
            } else {
                Text(signedIn!!, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { launchOp { vm.cloudUpload(); okExport } }) { Text(t("Back up now", "এখন ব্যাকআপ")) }
                    OutlinedButton(enabled = !busy, onClick = { confirmRestore = { launchOp { okRestore + vm.cloudRestore() } } }) { Text(t("Restore", "ফেরত আনুন")) }
                }
                TextButton(onClick = { vm.cloudSignOut(); signedIn = null }) { Text(t("Sign out", "সাইন আউট")) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    confirmRestore?.let { action ->
        AlertDialog(onDismissRequest = { confirmRestore = null },
            title = { Text(t("Replace data on this phone?", "এই ফোনের তথ্য বদলে যাবে?")) },
            text = { Text(t("All vehicles and logs on this phone will be replaced by the backup.", "এই ফোনের সব গাড়ি ও লগ ব্যাকআপ দিয়ে বদলে যাবে।")) },
            confirmButton = { TextButton(onClick = { confirmRestore = null; action() }) { Text(t("Restore", "ফেরত আনুন")) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(t("Cancel", "বাতিল")) } })
    }
}

/** Appearance options: visual style, light/dark, accent colour. Changes apply instantly. */
@Composable
private fun AppearanceSection(vm: MainViewModel) {
    val style by vm.prefs.uiStyle.collectAsStateWithLifecycle()
    val mode by vm.prefs.themeMode.collectAsStateWithLifecycle()
    val accent by vm.prefs.accent.collectAsStateWithLifecycle()
    SectionCard(t("Appearance", "চেহারা")) {
        Text(t("Style", "স্টাইল"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val opts = listOf("SOFT" to t("Soft", "সফট"), "MATERIAL" to t("Material", "ম্যাটেরিয়াল"))
            opts.forEachIndexed { i, (key, label) ->
                SegmentedButton(selected = style == key, onClick = { vm.prefs.setUiStyle(key) }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(label) }
            }
        }
        Text(t("Soft gives raised, tactile surfaces. Material uses flat cards.", "সফট দেয় উঁচু, স্পর্শযোগ্য সারফেস। ম্যাটেরিয়াল সমতল কার্ড ব্যবহার করে।"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(4.dp)
        Text(t("Theme", "থিম"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val opts = listOf("SYSTEM" to t("System", "সিস্টেম"), "LIGHT" to t("Light", "লাইট"), "DARK" to t("Dark", "ডার্ক"))
            opts.forEachIndexed { i, (key, label) ->
                SegmentedButton(selected = mode == key, onClick = {
                    if (mode != key) { vm.prefs.setThemeMode(key); com.garidoctor.app.GariDoctorApp.applyThemeMode(key) }
                }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(label, maxLines = 1) }
            }
        }
        Gap(4.dp)
        Text(t("Accent colour", "অ্যাকসেন্ট রং"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            com.garidoctor.app.ui.theme.Accent.entries.forEach { a ->
                val name = when (a) {
                    com.garidoctor.app.ui.theme.Accent.TEAL -> t("Teal", "টিল")
                    com.garidoctor.app.ui.theme.Accent.BLUE -> t("Blue", "নীল")
                    com.garidoctor.app.ui.theme.Accent.ORANGE -> t("Orange", "কমলা")
                    com.garidoctor.app.ui.theme.Accent.PURPLE -> t("Purple", "বেগুনি")
                }
                val selected = accent == a.name
                Box(
                    Modifier.size(48.dp)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                        .padding(5.dp)
                        .background(a.swatch, androidx.compose.foundation.shape.CircleShape)
                        .clickableItem { vm.prefs.setAccent(a.name) }
                        .semantics { contentDescription = name; this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}

/** AI reading: consent switch, today's allowance, and what stays on the phone. */
@Composable
private fun AiSection(vm: MainViewModel) {
    val consent by vm.aiConsent.collectAsStateWithLifecycle()
    val quota by vm.ai.quota.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    LaunchedEffect(consent) { if (consent) vm.ai.refreshQuota() }
    SectionCard(t("AI reading", "AI দিয়ে পড়া")) {
        Text(t("Papers, bills and voice are read on your phone first, for free and offline. Cloud AI (Google Gemini) is used only when you tap “Read with AI”.",
            "কাগজ, বিল ও কথা প্রথমে আপনার ফোনেই পড়া হয়, ফ্রি ও অফলাইনে। শুধু “AI দিয়ে পড়ুন” চাপলে ক্লাউড AI (গুগল Gemini) ব্যবহার হয়।"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Allow cloud AI", "ক্লাউড AI চালু"), style = MaterialTheme.typography.bodyLarge)
                Text(t("Sends only the photo or sentence you choose.", "শুধু আপনার বেছে নেওয়া ছবি বা বাক্য পাঠায়।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = consent, onCheckedChange = { vm.setAiConsent(it) })
        }
        if (!vm.ai.enabled) Text(t("Cloud AI isn't available in this build or is paused.", "এই বিল্ডে ক্লাউড AI নেই বা বন্ধ আছে।"), style = MaterialTheme.typography.bodySmall)
        quota?.let { q ->
            Text(t("Today: ${q.remaining} of ${q.limit + q.bonus} AI reads left", "আজ: ${n(q.limit + q.bonus)}টির মধ্যে ${n(q.remaining)}টি AI পড়া বাকি"), style = MaterialTheme.typography.labelLarge)
            if (!pro) Text(t("Pro gives ${30} reads a day. Watching a short ad adds one more (up to 3 a day).", "প্রো-তে দিনে ${n(30)}টি। ছোট বিজ্ঞাপন দেখলে আরও একটি (দিনে সর্বোচ্চ ৩টি)।"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
