package com.example

import android.app.Application
import com.example.data.AutoPilotDatabase
import com.example.data.AutoPilotRepository
import com.example.data.SettingsRepository
import com.example.engine.AutoPilotEngine

class AutoPilotApplication : Application() {

  lateinit var database: AutoPilotDatabase
    private set

  lateinit var settingsRepository: SettingsRepository
    private set

  lateinit var repository: AutoPilotRepository
    private set

  lateinit var engine: AutoPilotEngine
    private set

  override fun onCreate() {
    super.onCreate()
    database = AutoPilotDatabase.getInstance(this)
    settingsRepository = SettingsRepository(this)
    repository = AutoPilotRepository(database.autoPilotDao(), settingsRepository)
    engine = AutoPilotEngine(this, repository, settingsRepository)
  }
}
