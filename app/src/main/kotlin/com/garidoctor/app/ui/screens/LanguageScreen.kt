@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.garidoctor.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.core.model.Lang

/** Applies a language app-wide: saves it and switches the Android per-app locale (RTL follows). */
fun applyLanguage(vm: MainViewModel, l: Lang) {
    vm.setLang(l)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(l.code))
}

/** Languages shown in their own script first (so people find theirs at a glance), English name below. */
@Composable
fun LanguageList(selected: Lang, onPick: (Lang) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s)) {
        item {
            GroupCard {
                Lang.entries.forEachIndexed { i, l ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = l == selected, role = Role.RadioButton, onClick = { onPick(l) })
                            .padding(horizontal = Spacing.l, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.nativeName, style = MaterialTheme.typography.titleMedium)
                            if (l.nativeName != l.englishName) Text(l.englishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = l == selected, onClick = null)
                    }
                }
            }
        }
    }
}

/** First launch: pick a language before anything else (like Google apps in multilingual markets). */
@Composable
fun LanguageOnboarding(vm: MainViewModel) {
    var choice by remember { mutableStateOf(vm.lang.value) }
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 0.dp) {
                Button(onClick = { applyLanguage(vm, choice) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter).heightIn(min = 56.dp)) {
                    Text(choice.pick("Continue", "চালিয়ে যান"))
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Default.Translate, size = 64.dp)
                Gap(Spacing.l)
                Text(choice.pick("Choose your language", "আপনার ভাষা বেছে নিন"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(choice.pick("You can change it later in Settings.", "পরে সেটিংসে বদলাতে পারবেন।"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            LanguageList(choice, { choice = it }, Modifier.weight(1f))
        }
    }
}

/** Settings → Language: full list in a bottom sheet. */
@Composable
fun LanguageSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(t("Language", "ভাষা"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.gutter))
        LanguageList(vm.lang.value, { applyLanguage(vm, it); onDismiss() }, Modifier.fillMaxWidth().heightIn(max = 560.dp))
        Gap(Spacing.l)
    }
}
