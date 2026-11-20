package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoPilotApplication
import com.example.data.AdDecisionEntity
import com.example.data.LearnedPatternEntity
import com.example.model.EngineSnapshot
import com.example.model.OperationMode
import com.example.model.PermissionStatus
import com.example.model.SafetyConfig
import com.example.service.PermissionHelper
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as AutoPilotApplication
  private val engine = app.engine
  private val repository = app.repository
  private val settingsRepository = app.settingsRepository

  val engineSnapshot: StateFlow<EngineSnapshot> = engine.snapshot

  val safetyConfig: StateFlow<SafetyConfig> =
    settingsRepository.safetyConfig.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = SafetyConfig(),
    )

  val decisions: StateFlow<List<AdDecisionEntity>> =
    repository.decisions.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val learnedPatterns: StateFlow<List<LearnedPatternEntity>> =
    repository.learnedPatterns.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val overlayVisible: StateFlow<Boolean> = engine.overlayVisible

  private val _permissionRefreshTrigger = MutableStateFlow(0)
  private val _permissionStatus = MutableStateFlow(PermissionStatus())
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  init {
    viewModelScope.launch {
      combine(
        engine.accessibilityConnected,
        engine.screenCaptureActive,
        _permissionRefreshTrigger,
      ) { accConnected, captureActive, _ ->
        PermissionHelper.getPermissionStatus(
          context = app,
          isAccessibilityConnectedInEngine = accConnected,
          isScreenCaptureActive = captureActive,
        )
      }.collect { status ->
        _permissionStatus.value = status
      }
    }
  }

  fun refreshPermissions() {
    _permissionRefreshTrigger.value += 1
  }

  fun toggleStartStop() {
    if (engineSnapshot.value.isRunning) {
      engine.stopAutomation()
      val stopIntent =
        Intent(app, ScreenCaptureService::class.java).apply {
          action = ScreenCaptureService.ACTION_STOP_SERVICE
        }
      runCatching { app.startService(stopIntent) }
    } else {
      startOverlayAndEngine()
    }
  }

  fun startOverlayAndEngine() {
    val startIntent =
      Intent(app, ScreenCaptureService::class.java).apply {
        action = ScreenCaptureService.ACTION_START_OVERLAY_ONLY
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      app.startForegroundService(startIntent)
    } else {
      app.startService(startIntent)
    }
    engine.startAutomation()
  }

  fun startScreenCapture(resultCode: Int, resultData: Intent) {
    val intent =
      Intent(app, ScreenCaptureService::class.java).apply {
        action = ScreenCaptureService.ACTION_START_PROJECTION
        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      app.startForegroundService(intent)
    } else {
      app.startService(intent)
    }
    if (!engineSnapshot.value.isRunning) {
      engine.startAutomation()
    }
  }

  fun setOperationMode(mode: OperationMode) {
    viewModelScope.launch {
      settingsRepository.setOperationMode(mode)
    }
  }

  fun setConfidenceThreshold(value: Float) {
    viewModelScope.launch { settingsRepository.setConfidenceThreshold(value) }
  }

  fun setMinAdWatchSeconds(seconds: Int) {
    viewModelScope.launch { settingsRepository.setMinAdWatchSeconds(seconds) }
  }

  fun setClickCooldownMs(ms: Long) {
    viewModelScope.launch { settingsRepository.setClickCooldownMs(ms) }
  }

  fun setRedirectProtectionEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setRedirectProtectionEnabled(enabled) }
  }

  fun setOcrEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setOcrEnabled(enabled) }
  }

  fun setMaxRedirectRetries(retries: Int) {
    viewModelScope.launch { settingsRepository.setMaxRedirectRetries(retries) }
  }

  fun validateDecision(decisionId: Long, isTrueCloseButton: Boolean) {
    viewModelScope.launch {
      repository.validateDecision(decisionId, isTrueCloseButton)
    }
  }

  fun deleteLearnedPattern(patternId: Long) {
    viewModelScope.launch {
      repository.deleteLearnedPattern(patternId)
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      repository.clearHistory()
    }
  }

  fun clearLearnedLibrary() {
    viewModelScope.launch {
      repository.clearLearnedLibrary()
    }
  }
}
