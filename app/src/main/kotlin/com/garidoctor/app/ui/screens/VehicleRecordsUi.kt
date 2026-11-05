package com.garidoctor.app.ui.screens

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garidoctor.app.data.Snapshot
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.today
import com.garidoctor.app.passport.PassportPdf
import com.garidoctor.app.passport.Printing
import com.garidoctor.app.ui.*
import com.garidoctor.core.VehicleDoctor
import com.garidoctor.core.history.ExpenseCategory
import com.garidoctor.core.history.HistoryEntry
import com.garidoctor.core.history.HistoryKind
import com.garidoctor.core.identity.VehicleIdentity
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.Lang
import com.garidoctor.core.passport.VehiclePassport
import com.garidoctor.core.pro.ProFeature
import com.garidoctor.core.symptom.Urgency
import com.garidoctor.core.template.VehicleTemplate

/** The vehicle's references as shown on the ID card, the QR and the passport. */
fun VehicleEntity.identity(tpl: VehicleTemplate?, lang: Lang) = VehicleIdentity(
    nickname = nickname,
    vehicleType = tpl?.name?.get(lang) ?: templateId,
    makeModel = tpl?.model(modelPresetId)?.let { "${it.brand} ${it.model}" },
    registrationNo = registrationNo, engineNo = engineNo, chassisNo = chassisNo,
    driverName = driverName, driverLicenceNo = driverLicenceNo, driverPhone = driverPhone,
)

/** Every bill, fill-up, check and problem for one vehicle, as history lines in [lang]. */
fun buildHistory(s: Snapshot, vehicleId: Long, tpl: VehicleTemplate?, lang: Lang): List<HistoryEntry> = buildList {
    val v = s.vehicles.firstOrNull { it.id == vehicleId }
    val energy = v?.let { runCatching { EnergyType.valueOf(it.energyType) }.getOrNull() }
    val electric = energy != null && VehicleDoctor.isElectric(energy)
    s.services.filter { it.vehicleId == vehicleId }.forEach { e ->
        add(HistoryEntry(e.epochDay, HistoryKind.SERVICE, tpl?.part(e.partId)?.name?.get(lang) ?: e.partId,
            e.note?.takeIf { it != "initial" }, e.costBdt, e.odometerKm, e.id, e.receiptPath))
    }
    s.fuel.filter { it.vehicleId == vehicleId }.forEach { f ->
        val qty = when {
            electric -> "${(f.amount * 100).toInt()}%"
            energy == EnergyType.CNG -> "%.1f m³".format(f.amount)
            else -> "%.1f L".format(f.amount)
        }
        add(HistoryEntry(f.epochDay, HistoryKind.FUEL, if (electric) lang.pick("Charge", "চার্জ") else lang.pick("Fuel", "জ্বালানি"),
            qty + if (f.full) " · " + lang.pick("full", "ফুল") else "", f.priceBdt, f.odometerKm, f.id))
    }
    s.expenses.filter { it.vehicleId == vehicleId }.forEach { e ->
        add(HistoryEntry(e.epochDay, HistoryKind.EXPENSE, ExpenseCategory.of(e.category).label.get(lang), e.note, e.amountBdt, null, e.id, e.receiptPath))
    }
    s.checks.filter { it.vehicleId == vehicleId }.forEach { c ->
        val name = tpl?.check(c.checkId)?.title?.get(lang) ?: tpl?.part(c.partId)?.name?.get(lang) ?: c.checkId
        add(HistoryEntry(c.epochDay, HistoryKind.CHECK, name, if (c.passed) lang.pick("Passed", "ঠিক আছে") else lang.pick("Needs work", "কাজ দরকার"), refId = c.id))
    }
    s.symptoms.filter { it.vehicleId == vehicleId }.forEach { p ->
        val u = runCatching { Urgency.valueOf(p.urgency) }.getOrDefault(Urgency.THIS_WEEK)
        add(HistoryEntry(p.epochDay, HistoryKind.PROBLEM, p.summary, if (p.resolved) lang.pick("Fixed", "ঠিক হয়েছে") else u.label.get(lang), refId = p.id))
    }
}

enum class PassportMode { SHARE, PRINT }

/**
 * Builds the passport PDF (references, health, papers, spending, full history) and shares or
 * prints it. Pro, or one free with a rewarded ad. Shows its own dialogs where it is called.
 */
@Composable
fun rememberPassportAction(vm: MainViewModel, id: Long): (PassportMode) -> Unit {
    val st by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<PassportMode?>(null) }
    var showProFull by remember { mutableStateOf(false) }
    val make: (PassportMode) -> Unit = make@{ mode ->
        val v = st.snapshot.vehicles.firstOrNull { it.id == id } ?: return@make
        val tpl = st.registry.template(v.templateId) ?: return@make
        val c = st.checkups[id] ?: return@make
        val rec = st.snapshot.records[id] ?: return@make
        val p = VehiclePassport.build(rec.vehicle, tpl.name.get(lang), tpl.model(v.modelPresetId)?.let { "${it.brand} ${it.model}" },
            c.health, rec.services, rec.documents, c.fuel, today())
        runCatching {
            PassportPdf.write(ctx, p, { pid -> tpl.part(pid)?.name?.get(lang) ?: pid }, lang,
                v.identity(tpl, lang), vm.prefs.shareDriverDetails, buildHistory(st.snapshot, id, tpl, lang))
        }.onSuccess { f ->
            if (mode == PassportMode.PRINT) Printing.printPdf(ctx, f, "Car Doctor AI · ${v.nickname}") else PassportPdf.share(ctx, f)
        }.onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
    }
    pending?.let { mode ->
        AlertDialog(onDismissRequest = { pending = null },
            icon = { Icon(Icons.Default.PictureAsPdf, null) },
            title = { Text(t("Vehicle Passport", "ভেহিকল পাসপোর্ট")) },
            text = { Text(t("A PDF with the vehicle and driver details, health score, papers, every bill and a QR code. Print it, keep it in the car, or share it when selling.",
                "গাড়ি ও চালকের তথ্য, হেলথ স্কোর, কাগজপত্র, সব বিল ও QR কোডসহ একটি পিডিএফ। প্রিন্ট করে গাড়িতে রাখুন, বা বিক্রির সময় শেয়ার করুন।")) },
            confirmButton = { Button(onClick = { pending = null; showProFull = true }) { Text(t("Get Pro", "প্রো নিন")) } },
            dismissButton = {
                TextButton(onClick = {
                    pending = null
                    val shown = ctx.findActivity()?.let { a -> com.garidoctor.app.ads.Ads.showRewarded(a) { make(mode) } } ?: false
                    if (!shown) Toast.makeText(ctx, lang.pick("Ad not ready yet, try again in a moment", "বিজ্ঞাপন এখনো প্রস্তুত নয়, একটু পরে চেষ্টা করুন"), Toast.LENGTH_SHORT).show()
                }) { Text(t("Watch ad · 1 free", "বিজ্ঞাপন দেখুন · ১টি ফ্রি")) }
            })
    }
    if (showProFull) ProDialog(vm, t("Unlock Pro: multiple vehicles, unlimited passports, live OBD graphs, fleet mode, no ads.", "প্রো আনলক: একাধিক গাড়ি, আনলিমিটেড পাসপোর্ট, লাইভ OBD গ্রাফ, ফ্লিট মোড, বিজ্ঞাপন নেই।")) { showProFull = false }
    return { mode ->
        if (vm.allowed(ProFeature.PASSPORT_PDF)) make(mode) else pending = mode
        Unit
    }
}
