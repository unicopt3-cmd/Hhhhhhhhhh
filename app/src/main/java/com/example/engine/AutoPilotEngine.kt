package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.data.AdDecisionEntity
import com.example.data.AutoPilotRepository
import com.example.data.LearnedPatternEntity
import com.example.data.SettingsRepository
import com.example.model.AutomationPhase
import com.example.model.CandidateSource
import com.example.model.DecisionType
import com.example.model.EngineSnapshot
import com.example.model.OperationMode
import com.example.model.SafetyConfig
import com.example.model.ScoredCandidate
import com.example.model.UiCandidate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface AccessibilityExecutor {
  fun clickCandidate(candidate: UiCandidate): Boolean

  fun performBackAction(): Boolean
}

class AutoPilotEngine(
  private val appContext: Context,
  private val repository: AutoPilotRepository,
  private val settingsRepository: SettingsRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val recognitionEngine = RecognitionEngine()
  private val ocrProcessor = OcrProcessor()
  private val visualSymbolDetector = VisualSymbolDetector()
  private val redirectProtector = RedirectProtector().apply {
    setOwnPackageName(appContext.packageName)
  }
  private val stateMachine = AutomationStateMachine()

  private val _snapshot = MutableStateFlow(EngineSnapshot())
  val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

  private val _config = MutableStateFlow(SafetyConfig())
  val config: StateFlow<SafetyConfig> = _config.asStateFlow()

  private val _accessibilityConnected = MutableStateFlow(false)
  val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

  private val _screenCaptureActive = MutableStateFlow(false)
  val screenCaptureActive: StateFlow<Boolean> = _screenCaptureActive.asStateFlow()

  private val _overlayVisible = MutableStateFlow(false)
  val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

  private val _overlayHighlights = MutableStateFlow<List<ScoredCandidate>>(emptyList())
  val overlayHighlights: StateFlow<List<ScoredCandidate>> = _overlayHighlights.asStateFlow()

  @Volatile private var accessibilityExecutor: AccessibilityExecutor? = null
  @Volatile private var learnedPatternsCache: List<LearnedPatternEntity> = emptyList()
  @Volatile private var latestOcrCandidates: List<UiCandidate> = emptyList()
  @Volatile private var latestOcrTexts: List<String> = emptyList()
  @Volatile private var latestOcrTimestampMs: Long = 0L
  @Volatile private var latestVisualCandidates: List<UiCandidate> = emptyList()
  @Volatile private var latestVisualTimestampMs: Long = 0L
  @Volatile private var latestAccessibilityCandidates: List<UiCandidate> = emptyList()
  @Volatile private var screenWidthPx: Int = 1200
  @Volatile private var screenHeightPx: Int = 1920

  private val evaluationInFlight = AtomicBoolean(false)

  init {
    scope.launch {
      settingsRepository.safetyConfig.collect { cfg ->
        _config.value = cfg
        _snapshot.update { it.copy(mode = cfg.operationMode) }
      }
    }
    scope.launch {
      repository.learnedPatterns.collect { patterns ->
        learnedPatternsCache = patterns
      }
    }
  }

  fun updateScreenDimensions(width: Int, height: Int) {
    if (width > 0 && height > 0) {
      screenWidthPx = width
      screenHeightPx = height
    }
  }

  fun attachAccessibilityExecutor(executor: AccessibilityExecutor?) {
    accessibilityExecutor = executor
    _accessibilityConnected.value = executor != null
  }

  fun setScreenCaptureActive(active: Boolean) {
    _screenCaptureActive.value = active
    _snapshot.update { it.copy(ocrActive = active && _config.value.ocrEnabled) }
  }

  fun setOverlayVisible(visible: Boolean) {
    _overlayVisible.value = visible
  }

  fun startAutomation() {
    val currentMode = _config.value.operationMode
    stateMachine.reset(running = true, paused = false)
    redirectProtector.resetIncidentState()
    _snapshot.update {
      it.copy(
        isRunning = true,
        isPaused = false,
        mode = currentMode,
        phase = AutomationPhase.MONITORING_GAME,
        lastActionSummary = "Monitorando aplicativos e anúncios (${currentMode.labelPt})",
      )
    }
  }

  fun togglePause() {
    val currentlyRunning = _snapshot.value.isRunning
    if (!currentlyRunning) {
      startAutomation()
      return
    }
    val nextPaused = !_snapshot.value.isPaused
    stateMachine.reset(running = true, paused = nextPaused)
    if (nextPaused) {
      _overlayHighlights.value = emptyList()
    }
    _snapshot.update {
      it.copy(
        isPaused = nextPaused,
        phase = if (nextPaused) AutomationPhase.PAUSED else AutomationPhase.MONITORING_GAME,
        lastActionSummary = if (nextPaused) "Pausado pela bolha flutuante" else "Monitoramento retomado",
      )
    }
  }

  fun stopAutomation() {
    stateMachine.reset(running = false, paused = false)
    redirectProtector.resetIncidentState()
    _overlayHighlights.value = emptyList()
    _snapshot.update {
      it.copy(
        isRunning = false,
        isPaused = false,
        phase = AutomationPhase.INACTIVE,
        adWatchElapsedSeconds = 0,
        latestCandidates = emptyList(),
        lastActionSummary = "Automação interrompida imediatamente",
      )
    }
  }

  fun toggleOperationMode() {
    scope.launch {
      val nextMode =
        if (_config.value.operationMode == OperationMode.OBSERVATION) {
          OperationMode.AUTOMATIC
        } else {
          OperationMode.OBSERVATION
        }
      settingsRepository.setOperationMode(nextMode)
    }
  }

  fun onWindowPackageChanged(packageName: String) {
    _snapshot.update { it.copy(currentForegroundPackage = packageName) }
    val snap = _snapshot.value
    if (!snap.isRunning || snap.isPaused) {
      // Still track last legitimate game package passively if not in redirect target
      redirectProtector.onForegroundPackageChanged(
        newPackage = packageName,
        protectionEnabled = false,
        maxRetries = _config.value.maxRedirectRetries,
      )
      _snapshot.update { it.copy(lastGamePackage = redirectProtector.getLastGamePackage()) }
      return
    }

    val cfg = _config.value
    val redirectEval =
      redirectProtector.onForegroundPackageChanged(
        newPackage = packageName,
        protectionEnabled = cfg.redirectProtectionEnabled,
        maxRetries = cfg.maxRedirectRetries,
      )

    _snapshot.update { it.copy(lastGamePackage = redirectProtector.getLastGamePackage()) }

    if (redirectEval != null) {
      if (redirectEval.shouldAttemptBack) {
        _snapshot.update {
          it.copy(
            phase = AutomationPhase.RETURNING_TO_GAME,
            lastActionSummary = redirectEval.summaryPt,
          )
        }
        val backSent = accessibilityExecutor?.performBackAction() ?: false
        if (backSent) {
          _snapshot.update { it.copy(redirectRecoveriesSession = it.redirectRecoveriesSession + 1) }
        }
        scope.launch {
          repository.logDecision(
            AdDecisionEntity(
              gamePackage = redirectProtector.getLastGamePackage().ifBlank { packageName },
              adContext = "Redirecionamento externo: ${redirectEval.targetAppLabel}",
              detectedText = redirectEval.targetAppLabel,
              elementId = packageName,
              source = CandidateSource.ACCESSIBILITY.labelPt,
              regionBucket = "CENTER",
              boundsCompact = "0,0,0,0",
              confidenceScore = 1.0f,
              decisionType = DecisionType.REDIRECT_RECOVERED.name,
              reason = redirectEval.summaryPt,
              signalDetails = "Ação global Voltar executada: $backSent",
              userConfirmed = true,
            )
          )
        }
      } else {
        _snapshot.update { it.copy(lastActionSummary = redirectEval.summaryPt) }
        if (redirectEval.recoveryVerified) {
          _snapshot.update { it.copy(phase = AutomationPhase.MONITORING_GAME) }
        }
      }
    }
  }

  fun onAccessibilityHierarchyCaptured(
    foregroundPackage: String,
    candidates: List<UiCandidate>,
  ) {
    if (foregroundPackage == appContext.packageName) return
    latestAccessibilityCandidates = candidates
    evaluatePipeline(foregroundPackage)
  }

  suspend fun onScreenBitmapCaptured(
    bitmap: Bitmap,
    scaleX: Float,
    scaleY: Float,
  ) {
    val snap = _snapshot.value
    val cfg = _config.value
    if (!snap.isRunning || snap.isPaused || !cfg.ocrEnabled) return
    val pkg = snap.currentForegroundPackage
    if (pkg == appContext.packageName) return

    runCatching {
      val ocrResult =
        ocrProcessor.processBitmap(
          bitmap = bitmap,
          scaleToScreenX = scaleX,
          scaleToScreenY = scaleY,
          foregroundPackage = pkg,
        )
      val visualCandidates =
        visualSymbolDetector.detectGraphicalAdvanceCandidates(
          bitmap = bitmap,
          scaleToScreenX = scaleX,
          scaleToScreenY = scaleY,
          foregroundPackage = pkg,
        )
      val enrichedAccessibility =
        visualSymbolDetector.inspectAccessibilityCandidates(
          bitmap = bitmap,
          scaleToScreenX = scaleX,
          scaleToScreenY = scaleY,
          accessibilityCandidates = latestAccessibilityCandidates,
        )

      latestAccessibilityCandidates = enrichedAccessibility
      latestOcrCandidates = ocrResult.candidates
      latestOcrTexts = ocrResult.allTexts
      latestOcrTimestampMs = System.currentTimeMillis()
      latestVisualCandidates = visualCandidates
      latestVisualTimestampMs = latestOcrTimestampMs
      _snapshot.update {
        it.copy(
          ocrActive = true,
          ocrProcessingLatencyMs = ocrResult.latencyMs,
        )
      }
      evaluatePipeline(pkg)
    }
  }

  private fun evaluatePipeline(foregroundPackage: String) {
    val snap = _snapshot.value
    if (!snap.isRunning || snap.isPaused) return
    if (foregroundPackage in RedirectProtector.EXTERNAL_REDIRECT_PACKAGES.keys) return
    if (!evaluationInFlight.compareAndSet(false, true)) return

    scope.launch {
      try {
        val cfg = _config.value
        val now = System.currentTimeMillis()
        val ocrFresh = cfg.ocrEnabled && (now - latestOcrTimestampMs) <= 3500L
        val ocrCandidates = if (ocrFresh) latestOcrCandidates else emptyList()
        val ocrTexts = if (ocrFresh) latestOcrTexts else emptyList()
        val visualFresh = (now - latestVisualTimestampMs) <= 3500L
        val visualCandidates = if (visualFresh) latestVisualCandidates else emptyList()

        val merged =
          recognitionEngine.mergeCandidates(
            accessibilityCandidates = latestAccessibilityCandidates,
            ocrCandidates = ocrCandidates,
            visualCandidates = visualCandidates,
          )

        val adContext =
          recognitionEngine.analyzeAdContext(
            foregroundPackage = foregroundPackage,
            candidates = merged,
            allOcrTexts = ocrTexts,
          )

        if (adContext.isLikelyAdScreen) {
          redirectProtector.markAdSessionActive()
        }

        val scored =
          recognitionEngine.scoreCandidates(
            candidates = merged,
            adContext = adContext,
            learnedPatterns = learnedPatternsCache,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            config = cfg,
          )

        val stepResult =
          stateMachine.step(
            isRunning = _snapshot.value.isRunning,
            isPaused = _snapshot.value.isPaused,
            adContext = adContext,
            scoredCandidates = scored,
            config = cfg,
            lastGamePackage = redirectProtector.getLastGamePackage(),
          )

        _overlayHighlights.value =
          if (cfg.operationMode == OperationMode.OBSERVATION) {
            stepResult.overlayHighlights
          } else {
            emptyList()
          }

        if (stepResult.candidateToClick != null && cfg.operationMode == OperationMode.AUTOMATIC) {
          val clicked = accessibilityExecutor?.clickCandidate(stepResult.candidateToClick.candidate) ?: false
          if (clicked) {
            _snapshot.update { it.copy(safeClicksSession = it.safeClicksSession + 1) }
          }
        }

        stepResult.decisionToLog?.let { entry ->
          repository.logDecision(entry)
          if (entry.decisionType == DecisionType.BLOCKED_AD_TRAP.name) {
            _snapshot.update { it.copy(blockedTrapsSession = it.blockedTrapsSession + 1) }
          }
        }
        stepResult.rewardDecisionToLog?.let { rewardEntry ->
          repository.logDecision(rewardEntry)
        }

        _snapshot.update {
          it.copy(
            phase = stepResult.nextPhase,
            adWatchElapsedSeconds = stepResult.adWatchElapsedSeconds,
            latestCandidates = scored.take(6),
            lastActionSummary = stepResult.statusSummaryPt,
            adStageCount = stepResult.adStageCount,
            rewardMessageObserved = stepResult.rewardMessageObserved,
          )
        }
      } finally {
        evaluationInFlight.set(false)
      }
    }
  }
}
