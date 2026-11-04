package com.garidoctor.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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
}
