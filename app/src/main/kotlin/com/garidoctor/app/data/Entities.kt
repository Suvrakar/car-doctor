package com.garidoctor.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val modelPresetId: String?,
    val nickname: String,
    val registrationNo: String?,
    val odometerKm: Int,
    /** Odometer when added; parts never serviced count from here. */
    val baselineKm: Int,
    val energyType: String,
    val createdAtEpochDay: Long,
    val driverName: String? = null,
    val driverPhone: String? = null,
    /** Rated range per full charge for EVs (km). */
    val ratedRangeKm: Double? = null,
)


@Entity(tableName = "services", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val partId: String,
    val odometerKm: Int,
    val epochDay: Long,
    val costBdt: Int? = null,
    val note: String? = null,
    /** Receipt photo kept on the phone only (never uploaded). */
    val receiptPath: String? = null,
)

@Entity(tableName = "fuel", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class FuelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val odometerKm: Int,
    /** Litres, m³ CNG, or charge fraction (1.0 = full) for EVs. */
    val amount: Double,
    val priceBdt: Int?,
    val full: Boolean,
    val epochDay: Long,
    /** EVs: km actually driven on the previous full charge. */
    val rangeKm: Double? = null,
)

@Entity(tableName = "documents", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val type: String,
    val expiryEpochDay: Long,
    val number: String? = null,
)

@Entity(tableName = "checks", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val partId: String,
    val checkId: String,
    val passed: Boolean,
    val epochDay: Long,
)

@Entity(tableName = "symptoms", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val partId: String,
    val symptomId: String,
    val urgency: String,
    val summary: String,
    val epochDay: Long,
    val resolved: Boolean = false,
)

@Entity(tableName = "voltages", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class VoltageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val volts: Double,
    val nominalVolts: Double,
    val epochDay: Long,
)

@Entity(tableName = "fault_codes", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class FaultCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val code: String,
    val epochDay: Long,
    val cleared: Boolean = false,
)

@Entity(tableName = "odometer_log", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("vehicleId")])
data class OdometerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val km: Int,
    val epochDay: Long,
    /** manual | photo | gps | fuel | obd */
    val source: String,
)
