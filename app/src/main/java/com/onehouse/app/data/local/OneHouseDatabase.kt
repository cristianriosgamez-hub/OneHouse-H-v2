package com.onehouse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AcsConsumptionEntity::class, EnergyReadingEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OneHouseDatabase : RoomDatabase() {
    abstract fun acsConsumptionDao(): AcsConsumptionDao
    abstract fun energyReadingDao(): EnergyReadingDao

    companion object {
        @Volatile
        private var instance: OneHouseDatabase? = null

        fun getInstance(context: Context): OneHouseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OneHouseDatabase::class.java,
                    "onehouse.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
