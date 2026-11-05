@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.garidoctor.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.passport.IdCard
import com.garidoctor.app.passport.Printing
import com.garidoctor.app.ui.*
import com.garidoctor.app.ui.theme.AppSurface
import com.garidoctor.app.ui.theme.Spacing
import com.garidoctor.app.ui.theme.Tone
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.identity.VehicleIdentity.Field
import com.garidoctor.core.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vehicle ID: registration, engine and chassis numbers (unique across the garage), the driver's
 * name, licence and phone, and a QR holding all of it that can be shared to any app or printed.
 */
@Composable
fun VehicleIdScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return
    val tpl = st.registry.template(v.templateId)
    var reg by rememberSaveable(v.id) { mutableStateOf(v.registrationNo ?: "") }
    var engine by rememberSaveable(v.id) { mutableStateOf(v.engineNo ?: "") }
    var chassis by rememberSaveable(v.id) { mutableStateOf(v.chassisNo ?: "") }
    var driver by rememberSaveable(v.id) { mutableStateOf(v.driverName ?: "") }
    var licence by rememberSaveable(v.id) { mutableStateOf(v.driverLicenceNo ?: "") }
    var phone by rememberSaveable(v.id) { mutableStateOf(v.driverPhone ?: "") }
    var includeDriver by remember { mutableStateOf(vm.prefs.shareDriverDetails) }
    var scanLicence by remember { mutableStateOf(false) }
    var scanReg by remember { mutableStateOf(false) }

    val draft = v.copy(
        registrationNo = reg.trim().ifBlank { null }, engineNo = engine.trim().uppercase().ifBlank { null }, chassisNo = chassis.trim().uppercase().ifBlank { null },
        driverName = driver.trim().ifBlank { null }, driverLicenceNo = licence.trim().uppercase().ifBlank { null }, driverPhone = phone.trim().ifBlank { null },
    )
    val identity = draft.identity(tpl, lang)
    val clashes = remember(identity, st.snapshot.vehicles) {
        VehicleIdentity.duplicates(identity, st.snapshot.vehicles.filter { it.id != id }.map { it.identity(st.registry.template(it.templateId), lang) })
    }
    val changed = draft != v
    val qr = remember(identity, includeDriver, lang) { Printing.qrBitmap(identity.qrText(lang, includeDriver), 512).asImageBitmap() }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save() { if (clashes.isEmpty() && changed) vm.updateVehicle(draft) }
    fun export(print: Boolean) {
        save(); busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { if (print) IdCard.pdf(ctx, identity, lang, includeDriver) else IdCard.png(ctx, identity, lang, includeDriver) } }
                .onSuccess { f -> if (print) Printing.printPdf(ctx, f, "Car Doctor AI ID · ${v.nickname}") else Printing.share(ctx, f, "image/png", v.nickname) }
                .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    Screen(t("Vehicle ID & QR", "গাড়ির পরিচয় ও QR"), onBack, actions = {
        TextButton(enabled = changed && clashes.isEmpty(), onClick = { save(); Toast.makeText(ctx, lang.pick("Saved", "সেভ হয়েছে"), Toast.LENGTH_SHORT).show() }) { Text(t("Save", "সেভ")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(Spacing.gutter, Spacing.s, Spacing.gutter, Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // ── QR preview ──
            item {
                AppSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Image(qr, contentDescription = null, filterQuality = FilterQuality.None,
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)
                                .semantics { contentDescription = lang.pick("QR code with the vehicle's references", "গাড়ির তথ্যসহ QR কোড") })
                        Text(t("Any phone camera can read this QR. No app needed.", "যেকোনো ফোনের ক্যামেরা এই QR পড়তে পারে। অ্যাপ লাগবে না।"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Include driver details", "চালকের তথ্য রাখুন"), Modifier.weight(1f))
                            Switch(includeDriver, { includeDriver = it; vm.prefs.shareDriverDetails = it })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Button(enabled = !busy && clashes.isEmpty(), onClick = { export(print = false) }) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Share QR", "QR শেয়ার"))
                            }
                            OutlinedButton(enabled = !busy && clashes.isEmpty(), onClick = { export(print = true) }) {
                                Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Print", "প্রিন্ট"))
                            }
                        }
                    }
                }
            }
            if (clashes.isNotEmpty()) item {
                StatusBanner(Icons.Default.ErrorOutline, Tone.BAD, t("Already used by another vehicle", "অন্য গাড়িতে আগেই আছে"),
                    clashes.entries.joinToString("\n") { (f, other) -> f.label.get(lang) + " → " + other })
            }
            // ── Vehicle references ──
            item {
                SectionHeader(t("Vehicle", "গাড়ি")) {
                    TextButton(onClick = { scanReg = true }) { Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scan registration", "রেজিস্ট্রেশন স্ক্যান")) }
                }
            }
            item {
                SectionCard {
                    RefField(reg, { reg = it }, Field.REGISTRATION, clashes, placeholder = "DHAKA METRO-GA 12-3456")
                    RefField(engine, { engine = it }, Field.ENGINE, clashes)
                    RefField(chassis, { chassis = it }, Field.CHASSIS, clashes)
                    Text(t("Each number can belong to only one vehicle in your garage.", "প্রতিটি নম্বর আপনার গ্যারেজের শুধু একটি গাড়ির হতে পারে।"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // ── Driver ──
            item {
                SectionHeader(t("Driver", "চালক")) {
                    TextButton(onClick = { scanLicence = true }) { Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Scan licence", "লাইসেন্স স্ক্যান")) }
                }
            }
            item {
                SectionCard {
                    OutlinedTextField(driver, { driver = it }, label = { Text(Field.DRIVER.label.tr()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    RefField(licence, { licence = it }, Field.LICENCE, clashes)
                    OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } }, label = { Text(Field.PHONE.label.tr()) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Text(t("The licence's expiry is tracked under Papers, with reminders before it runs out.", "লাইসেন্সের মেয়াদ 'কাগজ' অংশে রাখা হয়, মেয়াদ শেষের আগে রিমাইন্ডার পাবেন।"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    // Scans save the paper with its expiry reminder and fill blank references on the vehicle.
    if (scanReg) PaperScanFlow(vm, id, listOf(DocumentType.REGISTRATION)) { scanReg = false }
    if (scanLicence) PaperScanFlow(vm, id, listOf(DocumentType.DRIVING_LICENCE)) { scanLicence = false }
    // Pick up references filled in by a scan.
    LaunchedEffect(v.registrationNo, v.engineNo, v.chassisNo, v.driverLicenceNo) {
        if (reg.isBlank()) reg = v.registrationNo ?: ""
        if (engine.isBlank()) engine = v.engineNo ?: ""
        if (chassis.isBlank()) chassis = v.chassisNo ?: ""
        if (licence.isBlank()) licence = v.driverLicenceNo ?: ""
    }
}

@Composable
private fun RefField(value: String, onChange: (String) -> Unit, field: Field, clashes: Map<Field, String>, placeholder: String? = null) {
    val clash = clashes[field]
    OutlinedTextField(value, onChange, label = { Text(field.label.tr()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = placeholder?.let { { Text(it) } }, isError = clash != null,
        supportingText = clash?.let { { Text(t("Already used by $it", "$it গাড়িতে আগেই আছে")) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
}
