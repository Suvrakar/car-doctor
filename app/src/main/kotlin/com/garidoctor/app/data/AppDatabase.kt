package com.garidoctor.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VehicleEntity::class, ServiceEntity::class, FuelEntity::class, DocumentEntity::class, CheckEntity::class,
        SymptomEntity::class, VoltageEntity::class, FaultCodeEntity::class, OdometerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GariDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "garidoctor.db").build().also { instance = it }
        }
    }
}
