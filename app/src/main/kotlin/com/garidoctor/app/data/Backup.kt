package com.garidoctor.app.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Whole-database backup as small JSON text. Receipt photo paths are kept, photos themselves are not. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedEpochDay: Long,
    val vehicles: List<VehicleEntity>,
    val services: List<ServiceEntity>,
    val fuel: List<FuelEntity>,
    val documents: List<DocumentEntity>,
    val checks: List<CheckEntity>,
    val symptoms: List<SymptomEntity>,
    val voltages: List<VoltageEntity>,
    val faultCodes: List<FaultCodeEntity>,
    val odometer: List<OdometerEntity>,
    val overrides: List<IntervalOverrideEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
) {
    companion object { const val FORMAT = "garidoctor-backup" }
}

class BackupManager(private val db: AppDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(): String = json.encodeToString(BackupFile.serializer(), BackupFile(
        exportedEpochDay = today(),
        vehicles = dao.vehiclesOnce(), services = dao.allServices(), fuel = dao.allFuel(), documents = dao.allDocuments(),
        checks = dao.allChecks(), symptoms = dao.allSymptoms(), voltages = dao.allVoltages(), faultCodes = dao.allFaultCodes(),
        odometer = dao.allOdometer(), overrides = dao.allOverrides(), expenses = dao.allExpenses(),
    ))

    /** Replaces everything on this phone with the backup. Throws if the text isn't a GariDoctor backup. */
    suspend fun restore(text: String): Int {
        val b = json.decodeFromString(BackupFile.serializer(), text)
        require(b.format == BackupFile.FORMAT) { "Not a Car Doctor AI backup" }
        db.withTransaction {
            dao.deleteAllVehicles()
            dao.insertVehicles(b.vehicles); dao.insertServices(b.services); dao.insertFuel(b.fuel); dao.insertDocuments(b.documents)
            dao.insertChecks(b.checks); dao.insertSymptoms(b.symptoms); dao.insertVoltages(b.voltages); dao.insertFaultCodes(b.faultCodes)
            dao.insertOdometer(b.odometer); dao.insertOverrides(b.overrides); dao.insertExpenses(b.expenses)
        }
        return b.vehicles.size
    }
}
