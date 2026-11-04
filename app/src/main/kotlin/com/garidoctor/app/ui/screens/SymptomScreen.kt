@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.SymptomEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.core.symptom.SymptomSession

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
    var tick by remember { mutableIntStateOf(0) } // SymptomSession is mutable; bump to recompose
    var saved by remember { mutableStateOf(false) }

    Screen(t("Symptom doctor", "সমস্যা নির্ণয়"), onBack = {
        val s = session
        if (s != null && s.back()) { tick++; saved = false } else if (s != null) { session = null; entryId = null } else onBack()
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            check(tick >= 0) // read tick so the mutable session re-renders
            val s = session
            if (s == null) {
                Text(t("What's the problem?", "সমস্যা কী?"), style = MaterialTheme.typography.titleLarge)
                tree.entries.forEach { e ->
                    OutlinedButton(onClick = { entryId = e.id; session = SymptomSession(tree, e.id); saved = false; tick++ }, modifier = Modifier.fillMaxWidth()) { Text(e.label.tr()) }
                }
            } else {
                val node = s.current
                val d = s.diagnosis
                if (node != null) {
                    Text(node.question.tr(), style = MaterialTheme.typography.titleLarge)
                    node.options.forEachIndexed { i, o ->
                        Button(onClick = { s.answer(i); tick++ }, modifier = Modifier.fillMaxWidth()) { Text(o.label.tr()) }
                    }
                } else if (d != null) {
                    UrgencyChip(d.urgency)
                    SectionCard(t("Possible causes", "সম্ভাব্য কারণ")) {
                        d.causes.sortedByDescending { it.likelihood }.forEach { c ->
                            Row { Text("${c.likelihood}%", fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp)); Text(c.text.tr()) }
                        }
                    }
                    SectionCard(t("What to do", "কী করবেন")) {
                        Text(d.advice.tr())
                        if (d.costMaxBdt > 0) Text(t("Approx. repair cost: ৳${d.costMinBdt}–${d.costMaxBdt}", "আনুমানিক খরচ: ৳${n(d.costMinBdt)}–${n(d.costMaxBdt)}"), fontWeight = FontWeight.SemiBold)
                    }
                    Text(SymptomSession.DISCLAIMER.tr(), style = MaterialTheme.typography.bodySmall)
                    if (d.partId != null && tpl.part(d.partId!!) != null) {
                        Button(enabled = !saved, onClick = {
                            val entry = tree.entries.first { it.id == entryId }
                            vm.addSymptom(SymptomEntity(vehicleId = id, partId = d.partId!!, symptomId = entry.id, urgency = d.urgency.name,
                                summary = "${entry.label.get(lang)}: ${d.causes.maxBy { it.likelihood }.text.get(lang)}", epochDay = today()))
                            saved = true
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) t("Added to health record ✓", "হেলথ রেকর্ডে যোগ হয়েছে ✓") else t("Add to health record", "হেলথ রেকর্ডে যোগ করুন")) }
                    }
                    OutlinedButton(onClick = onGarages, modifier = Modifier.fillMaxWidth()) { Text(t("Find a mechanic / SOS", "মেকানিক খুঁজুন / SOS")) }
                    TextButton(onClick = { session = null; entryId = null }) { Text(t("Check another problem", "অন্য সমস্যা দেখুন")) }
                }
            }
        }
    }
}
