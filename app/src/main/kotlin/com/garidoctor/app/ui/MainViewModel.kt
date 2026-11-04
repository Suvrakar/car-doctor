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
import kotlinx.coroutines.flow.combine
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

    val state: StateFlow<UiState> = combine(repo.snapshot, g.registry) { s, reg ->
        val ups = s.records.mapNotNull { (id, r) -> reg.template(r.vehicle.templateId)?.let { id to com.garidoctor.core.VehicleDoctor.checkup(it, r, today()) } }.toMap()
        UiState(s, ups, reg, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(registry = g.registry.value))

    val garages: List<Garage> by lazy { Garages.load(app, g.remote.string("garages")) }

    /** Sponsored tip for a part category from Remote Config (`sponsor_<CATEGORY>`), if a brand bought the slot. */
    fun sponsorTip(category: String): Pair<String, String>? = g.remote.string("sponsor_$category")?.let {
        runCatching {
            val o = Json.parseToJsonElement(it).jsonObject
            val text = o["text"]!!.jsonObject[if (lang.value == Lang.BN) "bn" else "en"]!!.jsonPrimitive.content
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
    fun addDocument(d: DocumentEntity) = io { repo.addDocument(d) }
    fun updateDocument(d: DocumentEntity) = io { repo.updateDocument(d) }
    fun deleteDocument(id: Long) = io { repo.deleteDocument(id) }
    fun addCheck(c: CheckEntity) = io { repo.addCheck(c) }
    fun addSymptom(s: SymptomEntity) = io { repo.addSymptom(s) }
    fun resolveSymptom(id: Long) = io { repo.resolveSymptom(id) }
    fun addVoltage(v: VoltageEntity) = io { repo.addVoltage(v) }
    fun replaceFaultCodes(id: Long, codes: List<String>) = io { repo.replaceFaultCodes(id, codes) }
}
