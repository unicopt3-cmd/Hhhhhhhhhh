package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.OperationMode
import com.example.model.SafetyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pt_autopilot_prefs")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val OPERATION_MODE = stringPreferencesKey("operation_mode")
    val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    val CLICK_COOLDOWN_MS = longPreferencesKey("click_cooldown_ms")
    val MIN_AD_WATCH_SECONDS = intPreferencesKey("min_ad_watch_seconds")
    val REDIRECT_PROTECTION_ENABLED = booleanPreferencesKey("redirect_protection_enabled")
    val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
    val MAX_REDIRECT_RETRIES = intPreferencesKey("max_redirect_retries")
    val OBSERVATION_VALIDATED_COUNT = intPreferencesKey("observation_validated_count")
  }

  val safetyConfig: Flow<SafetyConfig> =
    context.dataStore.data.map { prefs ->
      val modeName = prefs[Keys.OPERATION_MODE] ?: OperationMode.OBSERVATION.name
      val mode = runCatching { OperationMode.valueOf(modeName) }.getOrDefault(OperationMode.OBSERVATION)
      SafetyConfig(
        operationMode = mode,
        confidenceThreshold = (prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.72f).coerceIn(0.50f, 0.95f),
        clickCooldownMs = (prefs[Keys.CLICK_COOLDOWN_MS] ?: 2500L).coerceIn(1000L, 6000L),
        minAdWatchSeconds = (prefs[Keys.MIN_AD_WATCH_SECONDS] ?: 5).coerceIn(0, 20),
        redirectProtectionEnabled = prefs[Keys.REDIRECT_PROTECTION_ENABLED] ?: true,
        ocrEnabled = prefs[Keys.OCR_ENABLED] ?: true,
        maxRedirectRetries = (prefs[Keys.MAX_REDIRECT_RETRIES] ?: 3).coerceIn(1, 5),
        observationValidatedCount = prefs[Keys.OBSERVATION_VALIDATED_COUNT] ?: 0,
      )
    }

  suspend fun setOperationMode(mode: OperationMode) {
    context.dataStore.edit { it[Keys.OPERATION_MODE] = mode.name }
  }

  suspend fun setConfidenceThreshold(value: Float) {
    context.dataStore.edit { it[Keys.CONFIDENCE_THRESHOLD] = value.coerceIn(0.50f, 0.95f) }
  }

  suspend fun setClickCooldownMs(ms: Long) {
    context.dataStore.edit { it[Keys.CLICK_COOLDOWN_MS] = ms.coerceIn(1000L, 6000L) }
  }

  suspend fun setMinAdWatchSeconds(seconds: Int) {
    context.dataStore.edit { it[Keys.MIN_AD_WATCH_SECONDS] = seconds.coerceIn(0, 20) }
  }

  suspend fun setRedirectProtectionEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.REDIRECT_PROTECTION_ENABLED] = enabled }
  }

  suspend fun setOcrEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.OCR_ENABLED] = enabled }
  }

  suspend fun setMaxRedirectRetries(retries: Int) {
    context.dataStore.edit { it[Keys.MAX_REDIRECT_RETRIES] = retries.coerceIn(1, 5) }
  }

  suspend fun incrementValidatedObservations() {
    context.dataStore.edit { prefs ->
      val current = prefs[Keys.OBSERVATION_VALIDATED_COUNT] ?: 0
      prefs[Keys.OBSERVATION_VALIDATED_COUNT] = current + 1
    }
  }
}
