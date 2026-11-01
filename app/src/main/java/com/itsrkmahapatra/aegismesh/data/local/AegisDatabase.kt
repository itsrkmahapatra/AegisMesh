package com.itsrkmahapatra.aegismesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.itsrkmahapatra.aegismesh.data.local.dao.AlertDao
import com.itsrkmahapatra.aegismesh.data.local.dao.WeatherDao
import com.itsrkmahapatra.aegismesh.data.local.entity.EmergencyAlertEntity
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity

@Database(
    entities = [
        EmergencyAlertEntity::class,
        WeatherGridPointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun weatherDao(): WeatherDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        fun getDatabase(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AegisDatabase::class.java,
                    "aegis_emergency.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

