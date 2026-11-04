package com.garidoctor.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GariDao {
    @Query("SELECT * FROM vehicles ORDER BY id") fun vehicles(): Flow<List<VehicleEntity>>
    @Query("SELECT * FROM vehicles ORDER BY id") suspend fun vehiclesOnce(): List<VehicleEntity>
    @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun vehicle(id: Long): VehicleEntity?
    @Insert suspend fun insert(v: VehicleEntity): Long
    @Update suspend fun update(v: VehicleEntity)
    @Delete suspend fun delete(v: VehicleEntity)

    @Query("SELECT * FROM services ORDER BY epochDay DESC, odometerKm DESC") fun services(): Flow<List<ServiceEntity>>
    @Query("SELECT * FROM services WHERE vehicleId = :v ORDER BY epochDay DESC") suspend fun servicesOnce(v: Long): List<ServiceEntity>
    @Insert suspend fun insert(s: ServiceEntity): Long
    @Query("DELETE FROM services WHERE id = :id") suspend fun deleteService(id: Long)

    @Query("SELECT * FROM fuel ORDER BY epochDay DESC, odometerKm DESC") fun fuel(): Flow<List<FuelEntity>>
    @Query("SELECT * FROM fuel WHERE vehicleId = :v") suspend fun fuelOnce(v: Long): List<FuelEntity>
    @Insert suspend fun insert(f: FuelEntity): Long
    @Query("DELETE FROM fuel WHERE id = :id") suspend fun deleteFuel(id: Long)

    @Query("SELECT * FROM documents ORDER BY expiryEpochDay") fun documents(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE vehicleId = :v") suspend fun documentsOnce(v: Long): List<DocumentEntity>
    @Insert suspend fun insert(d: DocumentEntity): Long
    @Update suspend fun update(d: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: Long)

    @Query("SELECT * FROM checks") fun checks(): Flow<List<CheckEntity>>
    @Query("SELECT * FROM checks WHERE vehicleId = :v") suspend fun checksOnce(v: Long): List<CheckEntity>
    @Insert suspend fun insert(c: CheckEntity): Long

    @Query("SELECT * FROM symptoms ORDER BY epochDay DESC") fun symptoms(): Flow<List<SymptomEntity>>
    @Query("SELECT * FROM symptoms WHERE vehicleId = :v") suspend fun symptomsOnce(v: Long): List<SymptomEntity>
    @Insert suspend fun insert(s: SymptomEntity): Long
    @Query("UPDATE symptoms SET resolved = 1 WHERE id = :id") suspend fun resolveSymptom(id: Long)

    @Query("SELECT * FROM voltages ORDER BY epochDay DESC") fun voltages(): Flow<List<VoltageEntity>>
    @Query("SELECT * FROM voltages WHERE vehicleId = :v ORDER BY epochDay DESC LIMIT 1") suspend fun latestVoltage(v: Long): VoltageEntity?
    @Insert suspend fun insert(v: VoltageEntity): Long

    @Query("SELECT * FROM fault_codes WHERE cleared = 0") fun faultCodes(): Flow<List<FaultCodeEntity>>
    @Query("SELECT * FROM fault_codes WHERE vehicleId = :v AND cleared = 0") suspend fun faultCodesOnce(v: Long): List<FaultCodeEntity>
    @Insert suspend fun insert(f: FaultCodeEntity): Long
    @Query("UPDATE fault_codes SET cleared = 1 WHERE vehicleId = :v") suspend fun clearFaultCodes(v: Long)

    @Query("SELECT * FROM odometer_log ORDER BY epochDay") fun odometerLog(): Flow<List<OdometerEntity>>
    @Insert suspend fun insert(o: OdometerEntity): Long

    @Query("SELECT * FROM interval_overrides") fun intervalOverrides(): Flow<List<IntervalOverrideEntity>>
    @Query("SELECT * FROM interval_overrides WHERE vehicleId = :v") suspend fun intervalOverridesOnce(v: Long): List<IntervalOverrideEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(o: IntervalOverrideEntity)
    @Query("DELETE FROM interval_overrides WHERE vehicleId = :v AND partId = :p") suspend fun deleteOverride(v: Long, p: String)

    // ---- backup / restore (everything is small text; photos stay on the phone) ----
    @Query("SELECT * FROM services") suspend fun allServices(): List<ServiceEntity>
    @Query("SELECT * FROM fuel") suspend fun allFuel(): List<FuelEntity>
    @Query("SELECT * FROM documents") suspend fun allDocuments(): List<DocumentEntity>
    @Query("SELECT * FROM checks") suspend fun allChecks(): List<CheckEntity>
    @Query("SELECT * FROM symptoms") suspend fun allSymptoms(): List<SymptomEntity>
    @Query("SELECT * FROM voltages") suspend fun allVoltages(): List<VoltageEntity>
    @Query("SELECT * FROM fault_codes") suspend fun allFaultCodes(): List<FaultCodeEntity>
    @Query("SELECT * FROM odometer_log") suspend fun allOdometer(): List<OdometerEntity>
    @Query("SELECT * FROM interval_overrides") suspend fun allOverrides(): List<IntervalOverrideEntity>
    @Query("DELETE FROM vehicles") suspend fun deleteAllVehicles() // children cascade
    @Insert suspend fun insertVehicles(v: List<VehicleEntity>)
    @Insert suspend fun insertServices(v: List<ServiceEntity>)
    @Insert suspend fun insertFuel(v: List<FuelEntity>)
    @Insert suspend fun insertDocuments(v: List<DocumentEntity>)
    @Insert suspend fun insertChecks(v: List<CheckEntity>)
    @Insert suspend fun insertSymptoms(v: List<SymptomEntity>)
    @Insert suspend fun insertVoltages(v: List<VoltageEntity>)
    @Insert suspend fun insertFaultCodes(v: List<FaultCodeEntity>)
    @Insert suspend fun insertOdometer(v: List<OdometerEntity>)
    @Insert suspend fun insertOverrides(v: List<IntervalOverrideEntity>)
}
