package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [AdDecisionEntity::class, LearnedPatternEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class AutoPilotDatabase : RoomDatabase() {
  abstract fun autoPilotDao(): AutoPilotDao

  companion object {
    @Volatile private var instance: AutoPilotDatabase? = null

    fun getInstance(context: Context): AutoPilotDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                AutoPilotDatabase::class.java,
                "pt_autopilot_local.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
