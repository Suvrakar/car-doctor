package com.garidoctor.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garidoctor.app.BuildConfig
import com.garidoctor.app.GariDoctorApp
import com.garidoctor.app.data.CheckEntity
import com.garidoctor.app.data.DocumentEntity
import com.garidoctor.app.data.FuelEntity
import com.garidoctor.app.data.Garage
import com.garidoctor.app.data.Garages
import com.garidoctor.app.data.ServiceEntity
import com.garidoctor.app.data.Snapshot
import com.garidoctor.app.data.SosContact
import com.garidoctor.app.data.SymptomEntity
import com.garidoctor.app.data.VehicleEntity
import com.garidoctor.app.data.VoltageEntity
import com.garidoctor.app.data.today
import com.garidoctor.core.Checkup
import com.garidoctor.core.model.Lang
import com.garidoctor.core.pro.ProCodes
import com.garidoctor.core.pro.ProFeature
import com.garidoctor.core.pro.ProGate
import com.garidoctor.core.template.TemplateRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UiState(
    val snapshot: Snapshot = Snapshot(),
    val checkups: Map<Long, Checkup> = emptyMap(),
    val registry: TemplateRegistry,
    val loaded: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app as GariDoctorApp
    val repo = g.repository
    val prefs = g.prefs
    val lang: StateFlow<Lang> = prefs.lang
    val pro: StateFlow<Boolean> = prefs.pro
    val sos: StateFlow<List<SosContact>> = prefs.sosContacts

    /**
     * Everything the screens show. Database reads, joins and every vehicle's checkup run on a
     * background dispatcher (flowOn), so the main thread only draws.
     */
    val state: StateFlow<UiState> = combine(repo.snapshot, g.registry, g.registryReady) { s, reg, ready ->
        val day = today()
        val ups = s.records.mapNotNull { (id, r) -> reg.template(r.vehicle.templateId)?.let { id to com.garidoctor.core.VehicleDoctor.checkup(it, r, day) } }.toMap()
        UiState(s, ups, reg, ready)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, UiState(registry = g.registry.value))

    val garages: List<Garage> by lazy { Garages.load(app, g.remote.string("garages")) }

    /** Sponsored tip for a part category from Remote Config (`sponsor_<CATEGORY>`), if a brand bought the slot. */
    private val sponsorCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    private val noSponsor = "" to ""
    fun sponsorTip(category: String): Pair<String, String>? = sponsorCache.getOrPut("$category/${lang.value.code}") {
        sponsorTipUncached(category) ?: noSponsor
    }.takeIf { it !== noSponsor }

    private fun sponsorTipUncached(category: String): Pair<String, String>? = g.remote.string("sponsor_$category")?.let {
        runCatching {
            val o = Json.parseToJsonElement(it).jsonObject
            val texts = o["text"]!!.jsonObject
            val text = (texts[lang.value.code] ?: texts[if (lang.value == Lang.BN) "bn" else "en"])!!.jsonPrimitive.content
            o["brand"]!!.jsonPrimitive.content to text
        }.getOrNull()
    }

    fun canAddVehicle() = ProGate.allowed(ProFeature.MULTIPLE_VEHICLES, pro.value, state.value.snapshot.vehicles.size)
    fun allowed(f: ProFeature) = ProGate.allowed(f, pro.value)

    fun setLang(l: Lang) = prefs.setLang(l)
    fun redeem(code: String): Boolean = ProCodes.isValid(code, BuildConfig.PRO_SALT).also { if (it) prefs.setPro(true, code.trim().uppercase()) }
    fun setSos(list: List<SosContact>) = prefs.setSos(list)

    // ---- Play Billing ----
    val proPrice: StateFlow<String?> = g.billing.price
    fun buyPro(activity: android.app.Activity): Boolean = g.billing.buy(activity)

    // ---- backup ----
    val cloudAvailable: Boolean get() = g.cloud.available
    fun cloudEmail(): String? = g.cloud.userEmail
    suspend fun exportBackup(): String = g.backup.export()
    suspend fun restoreBackup(text: String): Int = g.backup.restore(text)
    suspend fun cloudSignIn(email: String, password: String, create: Boolean) = g.cloud.signIn(email, password, create)
    fun cloudSignOut() = g.cloud.signOut()
    suspend fun cloudUpload() = g.cloud.upload(g.backup.export(), today())
    suspend fun cloudRestore(): Int = g.cloud.download()?.let { g.backup.restore(it) } ?: error("No cloud backup yet")

    // ---- AI ----
    val ai = g.ai
    val aiConsent: StateFlow<Boolean> = prefs.aiConsent
    fun setAiConsent(v: Boolean) = prefs.setAiConsent(v)
    fun refreshAiQuota() = io { ai.refreshQuota() }
    /** Saves a scanned paper: updates the existing paper of that type, or adds it. */
    fun savePaper(vehicleId: Long, type: com.garidoctor.core.model.DocumentType, expiryEpochDay: Long, number: String?,
                  engineNo: String? = null, chassisNo: String? = null) {
        val existing = state.value.snapshot.documents.firstOrNull { it.vehicleId == vehicleId && it.type == type.name }
        if (existing != null) updateDocument(existing.copy(expiryEpochDay = expiryEpochDay, number = number ?: existing.number))
        else addDocument(DocumentEntity(vehicleId = vehicleId, type = type.name, expiryEpochDay = expiryEpochDay, number = number))
        fillReferencesFromPaper(vehicleId, type, number, engineNo, chassisNo)
    }

    /**
     * A scanned registration certificate fills a blank registration/engine/chassis number, a scanned
     * licence fills the driver's licence number. Never overwrites, never copies a number that
     * another vehicle in the garage already has.
     */
    private fun fillReferencesFromPaper(vehicleId: Long, type: com.garidoctor.core.model.DocumentType, number: String?, engineNo: String?, chassisNo: String?) {
        val all = state.value.snapshot.vehicles
        val v = all.firstOrNull { it.id == vehicleId } ?: return
        val others = all.filter { it.id != vehicleId }
        fun free(value: String?, of: (VehicleEntity) -> String?): String? {
            val n = com.garidoctor.core.identity.VehicleIdentity.normalize(value) ?: return null
            return value!!.trim().takeIf { others.none { o -> com.garidoctor.core.identity.VehicleIdentity.normalize(of(o)) == n } }
        }
        val u = when (type) {
            com.garidoctor.core.model.DocumentType.REGISTRATION -> v.copy(
                registrationNo = v.registrationNo ?: free(number?.takeIf { "METRO" in it.uppercase() || "মেট্রো" in it }) { it.registrationNo },
                engineNo = v.engineNo ?: free(engineNo) { it.engineNo },
                chassisNo = v.chassisNo ?: free(chassisNo) { it.chassisNo },
            )
            com.garidoctor.core.model.DocumentType.DRIVING_LICENCE -> v.copy(driverLicenceNo = v.driverLicenceNo ?: number?.trim()?.uppercase())
            else -> v
        }
        if (u != v) updateVehicle(u)
    }

    fun setInterval(vehicleId: Long, partId: String, km: Int?, months: Int?) = io { repo.setInterval(vehicleId, partId, km, months) }

    private fun io(block: suspend () -> Unit) { viewModelScope.launch { block() } }
    fun addVehicle(v: VehicleEntity, onDone: (Long) -> Unit) = viewModelScope.launch { onDone(repo.addVehicle(v)) }
    fun updateVehicle(v: VehicleEntity) = io { repo.updateVehicle(v) }
    fun deleteVehicle(v: VehicleEntity) = io { repo.deleteVehicle(v) }
    fun updateOdometer(id: Long, km: Int, source: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch { onResult(repo.updateOdometer(id, km, source)) }
    fun addService(s: ServiceEntity) = io { repo.addService(s) }
    fun deleteService(id: Long) = io { repo.deleteService(id) }
    fun addFuel(f: FuelEntity) = io { repo.addFuel(f) }
    fun deleteFuel(id: Long) = io { repo.deleteFuel(id) }
    fun addExpense(e: com.garidoctor.app.data.ExpenseEntity) = io { repo.addExpense(e) }
    fun deleteExpense(id: Long) = io { repo.deleteExpense(id) }
    fun deleteObdSession(id: Long) = io { repo.deleteObdSession(id) }

    /** Saves a live-data check to history; it then counts in the health score for 30 days. */
    fun saveLiveCheck(vehicleId: Long, summary: com.garidoctor.core.obd.LiveSummary) = io {
        val km = state.value.snapshot.vehicles.firstOrNull { it.id == vehicleId }?.odometerKm
        repo.addObdSession(com.garidoctor.app.data.ObdSessionEntity(vehicleId = vehicleId, epochDay = com.garidoctor.app.data.today(), startedAtMillis = summary.startedAtMillis,
            durationSec = summary.durationSec, worst = summary.worst.name, flagCount = summary.flags.size, summaryJson = summary.toJson(), odometerKm = km))
    }
    fun addDocument(d: DocumentEntity) = io { repo.addDocument(d) }
    fun updateDocument(d: DocumentEntity) = io { repo.updateDocument(d) }
    fun deleteDocument(id: Long) = io { repo.deleteDocument(id) }
    fun addCheck(c: CheckEntity) = io { repo.addCheck(c) }
    fun addSymptom(s: SymptomEntity) = io { repo.addSymptom(s) }
    fun resolveSymptom(id: Long) = io { repo.resolveSymptom(id) }
    fun addVoltage(v: VoltageEntity) = io { repo.addVoltage(v) }
    fun replaceFaultCodes(id: Long, codes: List<String>) = io { repo.replaceFaultCodes(id, codes) }
}
