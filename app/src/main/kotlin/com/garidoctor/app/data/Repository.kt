package com.garidoctor.app.data

import com.garidoctor.core.VehicleDoctor
import com.garidoctor.core.VehicleRecords
import com.garidoctor.core.fuel.FuelEntry
import com.garidoctor.core.model.CheckResult
import com.garidoctor.core.model.DocumentType
import com.garidoctor.core.model.EnergyType
import com.garidoctor.core.model.ReportedSymptom
import com.garidoctor.core.model.ServiceEvent
import com.garidoctor.core.model.Vehicle
import com.garidoctor.core.model.VehicleDocument
import com.garidoctor.core.model.VoltageReading
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.obd.DtcInfo
import com.garidoctor.core.template.IntervalOverride
import com.garidoctor.core.template.TemplateRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

fun today(): Long = LocalDate.now().toEpochDay()

fun VehicleEntity.toModel() = Vehicle(id, templateId, modelPresetId, nickname, registrationNo, odometerKm,
    runCatching { EnergyType.valueOf(energyType) }.getOrDefault(EnergyType.OCTANE), createdAtEpochDay)
fun ServiceEntity.toModel() = ServiceEvent(partId, odometerKm, epochDay, costBdt, note)
fun FuelEntity.toModel() = FuelEntry(odometerKm, amount, priceBdt, full, epochDay)
fun DocumentEntity.toModel() = runCatching { VehicleDocument(DocumentType.valueOf(type), expiryEpochDay, number) }.getOrNull()

/** Everything the UI shows, recomputed whenever any table changes. */
data class Snapshot(
    val vehicles: List<VehicleEntity> = emptyList(),
    val records: Map<Long, VehicleRecords> = emptyMap(),
    val services: List<ServiceEntity> = emptyList(),
    val fuel: List<FuelEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val symptoms: List<SymptomEntity> = emptyList(),
    val faultCodes: List<FaultCodeEntity> = emptyList(),
    val voltages: List<VoltageEntity> = emptyList(),
    val checks: List<CheckEntity> = emptyList(),
    val overrides: List<IntervalOverrideEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
)

class Repository(private val dao: GariDao, val registry: () -> TemplateRegistry, private val dtcLoader: () -> Map<String, DtcInfo>) {
    /** Fault-code dictionary, loaded the first time it's needed (rarely: only cars with OBD codes). */
    val dtc: Map<String, DtcInfo> get() = dtcLoader()

    val snapshot: Flow<Snapshot> = combine(
        combine(dao.vehicles(), dao.services(), dao.fuel(), dao.documents()) { v, s, f, d -> Quad(v, s, f, d) },
        combine(dao.symptoms(), dao.faultCodes(), dao.voltages(), dao.checks()) { sy, fc, vo, ch -> Quad(sy, fc, vo, ch) },
        dao.intervalOverrides(),
        dao.expenses(),
    ) { a, b, o, e -> build(a.a, a.b, a.c, a.d, b.a, b.b, b.c, b.d, o).copy(expenses = e) }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun build(
        v: List<VehicleEntity>, s: List<ServiceEntity>, f: List<FuelEntity>, d: List<DocumentEntity>,
        sy: List<SymptomEntity>, fc: List<FaultCodeEntity>, vo: List<VoltageEntity>, ch: List<CheckEntity>,
        ov: List<IntervalOverrideEntity>,
    ): Snapshot {
        val records = v.associate { ve ->
            ve.id to records(ve, s.filter { it.vehicleId == ve.id }, f.filter { it.vehicleId == ve.id }, d.filter { it.vehicleId == ve.id },
                sy.filter { it.vehicleId == ve.id }, fc.filter { it.vehicleId == ve.id }, vo.filter { it.vehicleId == ve.id }.maxByOrNull { it.epochDay },
                ch.filter { it.vehicleId == ve.id }, ov.filter { it.vehicleId == ve.id })
        }
        return Snapshot(v, records, s, f, d, sy, fc, vo, ch, ov)
    }

    private fun records(
        ve: VehicleEntity, s: List<ServiceEntity>, f: List<FuelEntity>, d: List<DocumentEntity>, sy: List<SymptomEntity>,
        fc: List<FaultCodeEntity>, vo: VoltageEntity?, ch: List<CheckEntity>, ov: List<IntervalOverrideEntity>,
    ) = VehicleRecords(
        vehicle = ve.toModel(),
        baselineKm = ve.baselineKm,
        services = s.map { it.toModel() },
        documents = d.mapNotNull { it.toModel() },
        fuel = f.map { it.toModel() },
        checks = ch.map { CheckResult(it.partId, it.passed, it.epochDay) },
        symptoms = sy.map { ReportedSymptom(it.partId, it.symptomId, it.epochDay, it.resolved) },
        voltage = vo?.let { VoltageReading(it.volts, it.nominalVolts, it.epochDay) },
        faultCodes = if (fc.isEmpty()) emptyList() else fc.map { DiagnosticTroubleCode(it.code, dtc[it.code]) },
        intervalOverrides = ov.associate { it.partId to IntervalOverride(it.intervalKm, it.intervalMonths) },
    )

    /** Records loaded directly (for workers that don't observe flows). */
    suspend fun recordsOnce(ve: VehicleEntity) = records(
        ve, dao.servicesOnce(ve.id), dao.fuelOnce(ve.id), dao.documentsOnce(ve.id), dao.symptomsOnce(ve.id),
        dao.faultCodesOnce(ve.id), dao.latestVoltage(ve.id), dao.checksOnce(ve.id), dao.intervalOverridesOnce(ve.id),
    )

    fun checkup(r: VehicleRecords) = registry().template(r.vehicle.templateId)?.let { VehicleDoctor.checkup(it, r, today()) }

    // ---- writes ----
    suspend fun addVehicle(v: VehicleEntity): Long {
        val id = dao.insert(v)
        dao.insert(OdometerEntity(vehicleId = id, km = v.odometerKm, epochDay = v.createdAtEpochDay, source = "manual"))
        return id
    }
    suspend fun updateVehicle(v: VehicleEntity) = dao.update(v)
    suspend fun deleteVehicle(v: VehicleEntity) = dao.delete(v)

    /** Odometer only moves forward; returns false if the reading was lower than the current one. */
    suspend fun updateOdometer(vehicleId: Long, km: Int, source: String): Boolean {
        val v = dao.vehicle(vehicleId) ?: return false
        if (km < v.odometerKm) return false
        dao.update(v.copy(odometerKm = km))
        dao.insert(OdometerEntity(vehicleId = vehicleId, km = km, epochDay = today(), source = source))
        return true
    }

    suspend fun addService(s: ServiceEntity) { dao.insert(s); updateOdometer(s.vehicleId, s.odometerKm, "manual") }
    suspend fun deleteService(id: Long) = dao.deleteService(id)
    suspend fun addFuel(f: FuelEntity) { dao.insert(f); updateOdometer(f.vehicleId, f.odometerKm, "fuel") }
    suspend fun deleteFuel(id: Long) = dao.deleteFuel(id)
    suspend fun addExpense(e: ExpenseEntity) = dao.insert(e)
    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)
    suspend fun addDocument(d: DocumentEntity) = dao.insert(d)
    suspend fun updateDocument(d: DocumentEntity) = dao.update(d)
    suspend fun deleteDocument(id: Long) = dao.deleteDocument(id)
    suspend fun addCheck(c: CheckEntity) = dao.insert(c)
    suspend fun addSymptom(s: SymptomEntity) = dao.insert(s)
    suspend fun resolveSymptom(id: Long) = dao.resolveSymptom(id)
    suspend fun addVoltage(v: VoltageEntity) = dao.insert(v)
    suspend fun replaceFaultCodes(vehicleId: Long, codes: List<String>) {
        dao.clearFaultCodes(vehicleId)
        codes.forEach { dao.insert(FaultCodeEntity(vehicleId = vehicleId, code = it, epochDay = today())) }
    }
    suspend fun vehiclesOnce() = dao.vehiclesOnce()
    suspend fun setInterval(vehicleId: Long, partId: String, km: Int?, months: Int?) =
        if (km == null && months == null) dao.deleteOverride(vehicleId, partId)
        else dao.upsert(IntervalOverrideEntity(vehicleId, partId, km, months))
}
