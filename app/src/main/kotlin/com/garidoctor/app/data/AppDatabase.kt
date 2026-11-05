package com.garidoctor.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VehicleEntity::class, ServiceEntity::class, FuelEntity::class, DocumentEntity::class, CheckEntity::class,
        SymptomEntity::class, VoltageEntity::class, FaultCodeEntity::class, OdometerEntity::class, IntervalOverrideEntity::class, ExpenseEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GariDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2: engine/chassis numbers and the driver's licence number on each vehicle; the expenses table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN engineNo TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN chassisNo TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN driverLicenceNo TEXT")
                // Same SQL Room generates for ExpenseEntity, so the schema check after migrating passes.
                db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `category` TEXT NOT NULL, `amountBdt` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT, `receiptPath` TEXT, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_vehicleId` ON `expenses` (`vehicleId`)")
            }
        }
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "garidoctor.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
