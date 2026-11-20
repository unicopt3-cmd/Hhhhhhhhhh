package com.example

import com.example.data.LearnedPatternEntity
import com.example.engine.AutomationStateMachine
import com.example.engine.RecognitionEngine
import com.example.engine.RedirectProtector
import com.example.model.AdContextSnapshot
import com.example.model.AutomationPhase
import com.example.model.CandidateClassification
import com.example.model.CandidateSource
import com.example.model.OperationMode
import com.example.model.RectBounds
import com.example.model.SafetyConfig
import com.example.model.UiCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAndEngineTest {

  private val recognitionEngine = RecognitionEngine()
  private val config = SafetyConfig(confidenceThreshold = 0.72f, minAdWatchSeconds = 0)

  @Test
  fun `blocks install trap button even if labeled close-like or in banner`() {
    val adContext =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("Anúncio"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("Instalar agora", "Pular"),
      )

    val trapCandidate =
      UiCandidate(
        id = "trap_1",
        text = "Instalar agora",
        contentDescription = "Fechar e instalar",
        viewIdResourceName = "com.ads:id/cta_install_btn",
        className = "android.widget.Button",
        packageName = "com.game.test",
        bounds = RectBounds(100, 1700, 1100, 1850),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.ACCESSIBILITY,
      )

    val safeCandidate =
      UiCandidate(
        id = "safe_1",
        text = "Pular anúncio",
        contentDescription = "Skip ad",
        viewIdResourceName = "com.ads:id/skip_btn",
        className = "android.widget.TextView",
        packageName = "com.game.test",
        bounds = RectBounds(1050, 40, 1170, 110),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.HYBRID,
      )

    val scored =
      recognitionEngine.scoreCandidates(
        candidates = listOf(trapCandidate, safeCandidate),
        adContext = adContext,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    val topSafe = scored.firstOrNull { it.candidate.id == "safe_1" }
    val trap = scored.firstOrNull { it.candidate.id == "trap_1" }

    assertNotNull(topSafe)
    assertEquals(CandidateClassification.SAFE_CLOSE, topSafe?.classification)
    assertNotNull(trap)
    assertEquals(CandidateClassification.TRAP_AD_LINK, trap?.classification)
  }

  @Test
  fun `does not trust isolated center X without corroboration`() {
    val adContext =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = false,
        adIndicators = emptyList(),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("X"),
      )

    val isolatedCenterX =
      UiCandidate(
        id = "x_center",
        text = "X",
        contentDescription = "",
        viewIdResourceName = "",
        className = "OcrTextElement",
        packageName = "com.game.test",
        bounds = RectBounds(560, 900, 620, 960),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.OCR,
      )

    val scored =
      recognitionEngine.scoreCandidates(
        candidates = listOf(isolatedCenterX),
        adContext = adContext,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    val item = scored.firstOrNull()
    assertTrue(item == null || item.classification != CandidateClassification.SAFE_CLOSE)
  }

  @Test
  fun `observation mode never emits candidateToClick`() {
    val stateMachine = AutomationStateMachine()
    stateMachine.reset(running = true, paused = false)

    val adContext =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("SDK: mbridge"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("Skip"),
      )

    val safeCandidate =
      UiCandidate(
        id = "safe_2",
        text = "Skip",
        contentDescription = "Close ad",
        viewIdResourceName = "mbridge_windown_close",
        className = "android.widget.ImageView",
        packageName = "com.game.test",
        bounds = RectBounds(1080, 30, 1170, 110),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.ACCESSIBILITY,
      )

    val scored =
      recognitionEngine.scoreCandidates(
        candidates = listOf(safeCandidate),
        adContext = adContext,
        learnedPatterns = listOf(
          LearnedPatternEntity(
            normalizedText = "skip",
            resourceIdPattern = "mbridge_windown_close",
            regionBucket = "TOP_RIGHT",
            contextPackage = "com.game.test",
            isSafeClose = true,
            confirmationCount = 2,
          )
        ),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    val obsResult =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = adContext,
        scoredCandidates = scored,
        config = config.copy(operationMode = OperationMode.OBSERVATION),
        lastGamePackage = "com.game.test",
      )

    assertNull(obsResult.candidateToClick)
    assertTrue(obsResult.overlayHighlights.isNotEmpty())

    val autoResult =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = adContext,
        scoredCandidates = scored,
        config = config.copy(operationMode = OperationMode.AUTOMATIC),
        lastGamePackage = "com.game.test",
      )

    assertNotNull(autoResult.candidateToClick)
    assertEquals(AutomationPhase.EXECUTING_ACTION, autoResult.nextPhase)
  }

  @Test
  fun `redirect protector triggers bounded back and confirms game return`() {
    val protector = RedirectProtector()
    protector.onForegroundPackageChanged("com.supergame.puzzle", protectionEnabled = true, maxRetries = 2)
    protector.markAdSessionActive()

    val firstRedirect =
      protector.onForegroundPackageChanged("com.android.vending", protectionEnabled = true, maxRetries = 2)
    assertNotNull(firstRedirect)
    assertTrue(firstRedirect!!.isExternalRedirect)
    assertTrue(firstRedirect.shouldAttemptBack)

    val returnEval =
      protector.onForegroundPackageChanged("com.supergame.puzzle", protectionEnabled = true, maxRetries = 2)
    assertNotNull(returnEval)
    assertFalse(returnEval!!.isExternalRedirect)
    assertTrue(returnEval.recoveryVerified)
  }
}
