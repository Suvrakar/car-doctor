@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.garidoctor.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.DocumentEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.core.docs.DocumentExpiry
import com.garidoctor.core.docs.ExpiryState
import com.garidoctor.core.model.DocumentType
import com.garidoctor.core.model.VehicleDocument
import java.time.LocalDate

@Composable
fun DocumentsScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId) ?: return
    val docs = st.snapshot.documents.filter { it.vehicleId == id }
    var editing by remember { mutableStateOf<Pair<DocumentType, DocumentEntity?>?>(null) }

    Screen(t("Papers & expiry", "কাগজপত্র ও মেয়াদ"), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(t("You'll get reminders 30, 15, 7, 3 and 1 day before expiry — avoid police fines.", "মেয়াদ শেষের ৩০, ১৫, ৭, ৩ ও ১ দিন আগে রিমাইন্ডার পাবেন — পুলিশের জরিমানা এড়ান।"), style = MaterialTheme.typography.bodySmall)
            }
            item {
                GroupCard {
                    tpl.documents.forEachIndexed { i, type ->
                        if (i > 0) RowDivider()
                        val d = docs.firstOrNull { it.type == type.name }
                        val status = d?.let { DocumentExpiry.status(VehicleDocument(type, it.expiryEpochDay, it.number), today()) }
                        val tone = when (status?.state) { null -> Tone.NEUTRAL; ExpiryState.VALID -> Tone.GOOD; ExpiryState.EXPIRED, ExpiryState.EXPIRING_7 -> Tone.BAD; else -> Tone.WARN }
                        val sub = when {
                            status == null || d == null -> t("Not added · tap to set expiry", "যোগ করা হয়নি · মেয়াদ দিতে ট্যাপ করুন")
                            status.daysLeft < 0 -> t("Expired ${-status.daysLeft} days ago", "${n(-status.daysLeft)} দিন আগে মেয়াদ শেষ")
                            else -> t("Valid until ${LocalDate.ofEpochDay(d.expiryEpochDay)}", "মেয়াদ ${LocalDate.ofEpochDay(d.expiryEpochDay)} পর্যন্ত")
                        }
                        ActionRow(Icons.Default.Description, type.label.tr(), sub, tone,
                            trailing = {
                                if (status != null) StatusPill(if (status.daysLeft < 0) t("Expired", "মেয়াদোত্তীর্ণ") else t("${status.daysLeft} d", "${n(status.daysLeft)} দিন"), tone)
                            },
                            onClick = { editing = type to d })
                    }
                }
            }
        }
    }
    editing?.let { (type, existing) ->
        var day by remember(type) { mutableLongStateOf(existing?.expiryEpochDay ?: today().plus(365)) }
        var number by remember(type) { mutableStateOf(existing?.number ?: "") }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(type.label.tr()) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(day, { day = it }, t("Expiry date", "মেয়াদ শেষের তারিখ"))
                OutlinedTextField(number, { number = it }, label = { Text(t("Number (optional)", "নম্বর (ঐচ্ছিক)")) }, singleLine = true)
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (existing == null) vm.addDocument(DocumentEntity(vehicleId = id, type = type.name, expiryEpochDay = day, number = number.ifBlank { null }))
                else vm.updateDocument(existing.copy(expiryEpochDay = day, number = number.ifBlank { null }))
                editing = null
            }) { Text(t("Save", "সেভ")) }
        }, dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { vm.deleteDocument(existing.id); editing = null }) { Text(t("Remove", "সরান"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = null }) { Text(t("Cancel", "বাতিল")) }
            }
        })
    }
}
