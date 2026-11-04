@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.SymptomEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.app.ui.theme.tone
import com.garidoctor.core.symptom.Diagnosis
import com.garidoctor.core.symptom.Node
import com.garidoctor.core.symptom.SymptomSession
import com.garidoctor.core.symptom.Urgency

/** Immutable view of where the user is, so the outgoing page animates with its own content. */
private sealed interface DoctorPage {
    data object Entries : DoctorPage
    data class Question(val node: Node, val depth: Int) : DoctorPage
    data class Result(val diagnosis: Diagnosis, val depth: Int) : DoctorPage
}

/** Tap-through "doctor chat": rule-based decision tree, Bangla first. */
@Composable
fun SymptomScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, onGarages: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId) ?: return
    val tree = st.registry.tree(tpl.symptomTreeId) ?: return
    var entryId by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<SymptomSession?>(null) }
    var page by remember { mutableStateOf<DoctorPage>(DoctorPage.Entries) }
    var saved by remember { mutableStateOf(false) }

    fun sync() {
        val s = session
        page = when {
            s == null -> DoctorPage.Entries
            s.diagnosis != null -> DoctorPage.Result(s.diagnosis!!, s.depth)
            else -> DoctorPage.Question(s.current!!, s.depth)
        }
    }
    val back: () -> Unit = {
        val s = session
        when {
            s == null -> onBack()
            s.back() -> { saved = false; sync() }
            else -> { session = null; entryId = null; sync() }
        }
    }
    BackHandler(enabled = session != null) { back() }

    Screen(t("Symptom doctor", "সমস্যা নির্ণয়"), onBack = back) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val p = page
            if (p !is DoctorPage.Entries) {
                // Trees are 1–3 questions deep; show progress toward an answer.
                val progress = when (p) { is DoctorPage.Question -> p.depth / (p.depth + 1.5f); else -> 1f }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), drawStopIndicator = {})
            }
            AnimatedContent(targetState = page, label = "doctor", transitionSpec = {
                val forward = when {
                    initialState is DoctorPage.Entries -> true
                    targetState is DoctorPage.Entries -> false
                    else -> depthOf(targetState) >= depthOf(initialState)
                }
                val d = if (forward) 1 else -1
                (slideInHorizontally { it / 5 * d } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 * d } + fadeOut())
            }) { target ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    when (target) {
                        DoctorPage.Entries -> {
                            Text(t("What's wrong with ${v.nickname}?", "${v.nickname} এর কী সমস্যা?"), style = MaterialTheme.typography.headlineSmall)
                            Text(t("Answer a few quick questions. Works offline.", "কয়েকটি ছোট প্রশ্নের উত্তর দিন। ইন্টারনেট লাগে না।"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GroupCard {
                                tree.entries.forEachIndexed { i, e ->
                                    if (i > 0) RowDivider()
                                    ActionRow(Icons.Default.HealthAndSafety, e.label.tr(), onClick = {
                                        entryId = e.id; session = SymptomSession(tree, e.id); saved = false; sync()
                                    })
                                }
                            }
                        }
                        is DoctorPage.Question -> {
                            Text(target.node.question.tr(), style = MaterialTheme.typography.headlineSmall)
                            target.node.options.forEachIndexed { i, o ->
                                AppSurface(Modifier.fillMaxWidth().heightIn(min = 64.dp), radius = 20.dp, elevation = 5.dp, onClick = { session?.answer(i); sync() }, contentDescription = o.label.tr()) {
                                    Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                                        Text(o.label.tr(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        is DoctorPage.Result -> ResultPage(target.diagnosis, saved,
                            canSave = target.diagnosis.partId?.let { tpl.part(it) != null } ?: false,
                            onSave = {
                                val d = target.diagnosis
                                val entry = tree.entries.first { it.id == entryId }
                                vm.addSymptom(SymptomEntity(vehicleId = id, partId = d.partId!!, symptomId = entry.id, urgency = d.urgency.name,
                                    summary = "${entry.label.get(lang)}: ${d.causes.maxBy { it.likelihood }.text.get(lang)}", epochDay = today()))
                                saved = true
                            },
                            onGarages = onGarages,
                            onRestart = { session = null; entryId = null; sync() })
                    }
                }
            }
        }
    }
}

private fun depthOf(p: DoctorPage) = when (p) { DoctorPage.Entries -> 0; is DoctorPage.Question -> p.depth; is DoctorPage.Result -> p.depth + 1 }

@Composable
private fun ColumnScope.ResultPage(d: Diagnosis, saved: Boolean, canSave: Boolean, onSave: () -> Unit, onGarages: () -> Unit, onRestart: () -> Unit) {
    val icon = when (d.urgency) { Urgency.SAFE -> Icons.Default.CheckCircle; Urgency.THIS_WEEK -> Icons.Default.Schedule; Urgency.STOP_NOW -> Icons.Default.Dangerous }
    StatusBanner(icon, d.urgency.tone(), d.urgency.label.tr(), d.advice.tr())
    SectionHeader(t("Possible causes", "সম্ভাব্য কারণ"))
    GroupCard {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            d.causes.sortedByDescending { it.likelihood }.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(c.text.tr(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${n(c.likelihood)}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    UsageBar(c.likelihood / 100f, Tone.NEUTRAL)
                }
            }
        }
    }
    if (d.costMaxBdt > 0) {
        SectionHeader(t("Approximate repair cost", "আনুমানিক মেরামত খরচ"))
        Text("৳${n(d.costMinBdt)} – ৳${n(d.costMaxBdt)}", style = MaterialTheme.typography.headlineSmall)
    }
    Text(SymptomSession.DISCLAIMER.tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (canSave) Button(enabled = !saved, onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(if (saved) Icons.Default.Check else Icons.Default.Add, null); Spacer(Modifier.width(Spacing.s))
        Text(if (saved) t("Added to health record", "হেলথ রেকর্ডে যোগ হয়েছে") else t("Add to health record", "হেলথ রেকর্ডে যোগ করুন"))
    }
    OutlinedButton(onClick = onGarages, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Default.Build, null); Spacer(Modifier.width(Spacing.s)); Text(t("Find a mechanic / SOS", "মেকানিক খুঁজুন / SOS"))
    }
    TextButton(onClick = onRestart, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Check another problem", "অন্য সমস্যা দেখুন")) }
}
