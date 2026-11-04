@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.BuildConfig
import com.garidoctor.app.ui.*
import com.garidoctor.core.model.Lang

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val lang by vm.lang.collectAsStateWithLifecycle()
    val pro by vm.pro.collectAsStateWithLifecycle()
    val contacts by vm.sos.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }
    Screen(t("Settings", "সেটিংস"), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
