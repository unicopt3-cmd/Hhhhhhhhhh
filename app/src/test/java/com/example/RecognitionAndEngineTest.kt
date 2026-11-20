package com.example

import com.example.data.LearnedPatternEntity
import com.example.engine.AutomationStateMachine
import com.example.engine.RecognitionEngine
import com.example.engine.RedirectProtector
import com.example.model.AdContextSnapshot
import com.example.model.AutomationPhase
import com.example.model.ButtonActionKind
import com.example.model.CandidateClassification
import com.example.model.CandidateSource
import com.example.model.DecisionType
import com.example.model.OperationMode
import com.example.model.RectBounds
import com.example.model.RegionBucket
import com.example.model.SafetyConfig
import com.example.model.UiCandidate
import com.example.model.VisualSymbolType
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

  @Test
  fun `recognizes all advance and continue text variants across left right and tablet positions`() {
    val adContext =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("SDK: applovin"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("Anúncio", "Next", "Continuar"),
      )

    val labelsAndRegions =
      listOf(
        Triple("Next", RectBounds(40, 40, 200, 110), RegionBucket.TOP_LEFT),
        Triple("Continue", RectBounds(1780, 40, 1960, 110), RegionBucket.TOP_RIGHT),
        Triple("Skip", RectBounds(1780, 540, 1960, 620), RegionBucket.MID_RIGHT),
        Triple("Continuar", RectBounds(30, 540, 220, 620), RegionBucket.MID_LEFT),
        Triple("Avançar", RectBounds(1740, 1020, 1950, 1110), RegionBucket.BOTTOM_RIGHT),
        Triple("Próximo", RectBounds(30, 1020, 230, 1110), RegionBucket.BOTTOM_LEFT),
        Triple("Pular", RectBounds(920, 35, 1100, 105), RegionBucket.TOP_BAR),
        Triple("Próxima etapa", RectBounds(1640, 540, 1960, 620), RegionBucket.MID_RIGHT),
        Triple("Continue watching", RectBounds(760, 540, 1240, 630), RegionBucket.CENTER),
      )

    labelsAndRegions.forEachIndexed { idx, (label, bounds, expectedRegion) ->
      val cand =
        UiCandidate(
          id = "adv_txt_$idx",
          text = label,
          contentDescription = "",
          viewIdResourceName = "",
          className = "android.widget.Button",
          packageName = "com.game.test",
          bounds = bounds,
          isClickable = true,
          isEnabled = true,
          isVisibleToUser = true,
          source = CandidateSource.HYBRID,
        )
      val scored =
        recognitionEngine.scoreCandidates(
          candidates = listOf(cand),
          adContext = adContext,
          learnedPatterns = emptyList(),
          screenWidth = 2000,
          screenHeight = 1200,
          config = config,
        )
      val item = scored.firstOrNull()
      assertNotNull("Expected candidate for '$label'", item)
      assertEquals("Expected SAFE_CLOSE for '$label'", CandidateClassification.SAFE_CLOSE, item!!.classification)
      assertEquals("Region mismatch for '$label'", expectedRegion, item.regionBucket)
      assertTrue(
        "ActionKind mismatch for '$label': ${item.actionKind}",
        item.actionKind == ButtonActionKind.ADVANCE_STEP || item.actionKind == ButtonActionKind.SKIP_AD,
      )
    }
  }

  @Test
  fun `recognizes textless graphical advance symbols in ad corners and mid sides while rejecting center video play triangles`() {
    val adContext =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("SDK: unityads"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("Sponsored"),
      )

    // Safe textless graphical controls on left, right, and intermediate heights
    val validSymbols =
      listOf(
        UiCandidate(
          id = "vis_left",
          text = "",
          contentDescription = "",
          viewIdResourceName = "",
          className = "VisualAdvanceSymbol:ARROW_IN_CIRCLE",
          packageName = "com.game.test",
          bounds = RectBounds(36, 480, 126, 570),
          isClickable = true,
          isEnabled = true,
          isVisibleToUser = true,
          source = CandidateSource.VISUAL_SYMBOL,
          visualSymbol = VisualSymbolType.ARROW_IN_CIRCLE,
          visualSymbolConfidence = 0.82f,
        ),
        UiCandidate(
          id = "vis_right",
          text = "",
          contentDescription = "",
          viewIdResourceName = "",
          className = "VisualAdvanceSymbol:DASH_CIRCLE",
          packageName = "com.game.test",
          bounds = RectBounds(1050, 920, 1165, 1005),
          isClickable = true,
          isEnabled = true,
          isVisibleToUser = true,
          source = CandidateSource.VISUAL_SYMBOL,
          visualSymbol = VisualSymbolType.DASH_CIRCLE,
          visualSymbolConfidence = 0.78f,
        ),
        UiCandidate(
          id = "vis_top_right_tri",
          text = "",
          contentDescription = "",
          viewIdResourceName = "",
          className = "VisualAdvanceSymbol:RIGHT_TRIANGLE",
          packageName = "com.game.test",
          bounds = RectBounds(1070, 50, 1150, 130),
          isClickable = true,
          isEnabled = true,
          isVisibleToUser = true,
          source = CandidateSource.VISUAL_SYMBOL,
          visualSymbol = VisualSymbolType.RIGHT_TRIANGLE,
          visualSymbolConfidence = 0.82f,
        ),
      )

    // Center video Play triangle & bottom CTA trap MUST NOT become SAFE_CLOSE
    val unsafeCenterPlayTriangle =
      UiCandidate(
        id = "center_play",
        text = "",
        contentDescription = "",
        viewIdResourceName = "",
        className = "VisualAdvanceSymbol:RIGHT_TRIANGLE",
        packageName = "com.game.test",
        bounds = RectBounds(520, 880, 680, 1040),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.VISUAL_SYMBOL,
        visualSymbol = VisualSymbolType.RIGHT_TRIANGLE,
        visualSymbolConfidence = 0.88f,
      )
    val explicitPlaybackButton =
      UiCandidate(
        id = "video_replay",
        text = "Replay",
        contentDescription = "Play video",
        viewIdResourceName = "com.ads:id/exo_play",
        className = "android.widget.ImageButton",
        packageName = "com.game.test",
        bounds = RectBounds(1050, 60, 1160, 140),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.ACCESSIBILITY,
      )

    val scored =
      recognitionEngine.scoreCandidates(
        candidates = validSymbols + listOf(unsafeCenterPlayTriangle, explicitPlaybackButton),
        adContext = adContext,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    validSymbols.forEach { sym ->
      val found = scored.firstOrNull { it.candidate.id == sym.id }
      assertNotNull("Expected scored candidate for ${sym.id}", found)
      assertEquals("Expected SAFE_CLOSE for ${sym.id}", CandidateClassification.SAFE_CLOSE, found!!.classification)
      assertEquals(ButtonActionKind.ADVANCE_STEP, found.actionKind)
    }

    val centerPlay = scored.firstOrNull { it.candidate.id == "center_play" }
    assertTrue(centerPlay == null || centerPlay.classification != CandidateClassification.SAFE_CLOSE)

    val replayBtn = scored.firstOrNull { it.candidate.id == "video_replay" }
    assertTrue(replayBtn == null || replayBtn.classification != CandidateClassification.SAFE_CLOSE)

    // Ambiguous symbol outside ad context must also NOT be trusted
    val nonAdContext = adContext.copy(isLikelyAdScreen = false, adIndicators = emptyList())
    val scoredNoAd =
      recognitionEngine.scoreCandidates(
        candidates = listOf(validSymbols.first()),
        adContext = nonAdContext,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )
    assertTrue(scoredNoAd.firstOrNull()?.classification != CandidateClassification.SAFE_CLOSE)
  }

  @Test
  fun `supports multi-step ads from advance icon to final X close and preserves observation safety`() {
    val stateMachine = AutomationStateMachine()
    stateMachine.reset(running = true, paused = false)

    val stage1Context =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("SDK: applovin"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("Sponsored", "Playable demo"),
      )

    val stage1AdvanceSymbol =
      UiCandidate(
        id = "stage1_arrow",
        text = "",
        contentDescription = "",
        viewIdResourceName = "",
        className = "VisualAdvanceSymbol:ARROW_IN_CIRCLE",
        packageName = "com.game.test",
        bounds = RectBounds(1060, 70, 1160, 170),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.VISUAL_SYMBOL,
        visualSymbol = VisualSymbolType.ARROW_IN_CIRCLE,
        visualSymbolConfidence = 0.85f,
      )

    val stage1Scored =
      recognitionEngine.scoreCandidates(
        candidates = listOf(stage1AdvanceSymbol),
        adContext = stage1Context,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    // Observation mode: highlights & logs graphical advance button, NEVER clicks
    val obsStep =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = stage1Context,
        scoredCandidates = stage1Scored,
        config = config.copy(operationMode = OperationMode.OBSERVATION),
        lastGamePackage = "com.game.test",
        nowMs = 10_000L,
      )
    assertNull(obsStep.candidateToClick)
    assertTrue(obsStep.overlayHighlights.isNotEmpty())
    assertEquals(DecisionType.OBSERVED_CANDIDATE.name, obsStep.decisionToLog?.decisionType)

    // Automatic mode: clicks graphical advance button (Stage 1)
    val autoStage1 =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = stage1Context,
        scoredCandidates = stage1Scored,
        config = config.copy(operationMode = OperationMode.AUTOMATIC, clickCooldownMs = 2000L),
        lastGamePackage = "com.game.test",
        nowMs = 12_500L,
      )
    assertNotNull(autoStage1.candidateToClick)
    assertEquals(ButtonActionKind.ADVANCE_STEP, autoStage1.candidateToClick!!.actionKind)
    assertEquals(DecisionType.CLICKED_ADVANCE.name, autoStage1.decisionToLog?.decisionType)

    // Stage 2 appears with new content (end-card showing final X close button)
    val stage2Context =
      AdContextSnapshot(
        foregroundPackage = "com.game.test",
        isLikelyAdScreen = true,
        adIndicators = listOf("SDK: applovin"),
        hasActiveCountdown = false,
        countdownSecondsRemaining = null,
        allVisibleTexts = listOf("End card", "Close"),
      )
    val stage2CloseX =
      UiCandidate(
        id = "stage2_x",
        text = "X",
        contentDescription = "Close ad",
        viewIdResourceName = "al_close",
        className = "android.widget.ImageButton",
        packageName = "com.game.test",
        bounds = RectBounds(1080, 40, 1170, 120),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.ACCESSIBILITY,
      )
    val stage2Scored =
      recognitionEngine.scoreCandidates(
        candidates = listOf(stage2CloseX),
        adContext = stage2Context,
        learnedPatterns = emptyList(),
        screenWidth = 1200,
        screenHeight = 1920,
        config = config,
      )

    // Immediately after cooldown elapses on changed stage 2, finds and clicks X
    val autoStage2 =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = stage2Context,
        scoredCandidates = stage2Scored,
        config = config.copy(operationMode = OperationMode.AUTOMATIC, clickCooldownMs = 2000L),
        lastGamePackage = "com.game.test",
        nowMs = 15_000L,
      )
    assertNotNull(autoStage2.candidateToClick)
    assertEquals(ButtonActionKind.CLOSE_X, autoStage2.candidateToClick!!.actionKind)
    assertEquals(DecisionType.CLICKED_CLOSE.name, autoStage2.decisionToLog?.decisionType)
    assertEquals(2, autoStage2.adStageCount)

    // Game returns after ad closed
    val backToGame =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = stage2Context.copy(isLikelyAdScreen = false, adIndicators = emptyList(), allVisibleTexts = listOf("Score: 100")),
        scoredCandidates = emptyList(),
        config = config.copy(operationMode = OperationMode.AUTOMATIC),
        lastGamePackage = "com.game.test",
        nowMs = 16_000L,
      )
    assertEquals(AutomationPhase.MONITORING_GAME, backToGame.nextPhase)
    assertNull(backToGame.candidateToClick)
  }

  @Test
  fun `detects reward granted messages without automatically closing screen`() {
    val rewardPhrases =
      listOf(
        "Recompensa concedida",
        "Recompensa recebida",
        "Reward granted",
        "Reward received",
        "Reward earned",
      )

    rewardPhrases.forEach { phrase ->
      val ctx =
        recognitionEngine.analyzeAdContext(
          foregroundPackage = "com.game.test",
          candidates = emptyList(),
          allOcrTexts = listOf(phrase),
        )
      assertTrue("Expected ad screen context for '$phrase'", ctx.isLikelyAdScreen)
      assertTrue("Expected rewardGrantedDetected for '$phrase'", ctx.rewardGrantedDetected)
      assertEquals(phrase, ctx.rewardMessageText)
    }

    val stateMachine = AutomationStateMachine()
    stateMachine.reset(running = true, paused = false)

    val rewardCtx =
      recognitionEngine.analyzeAdContext(
        foregroundPackage = "com.game.test",
        candidates = emptyList(),
        allOcrTexts = listOf("Reward granted"),
      )

    val step =
      stateMachine.step(
        isRunning = true,
        isPaused = false,
        adContext = rewardCtx,
        scoredCandidates = emptyList(),
        config = config.copy(operationMode = OperationMode.AUTOMATIC),
        lastGamePackage = "com.game.test",
        nowMs = 5_000L,
      )

    // Logs reward message accurately, stays on screen waiting for legitimate close/advance button, NEVER auto-closes
    assertNull(step.candidateToClick)
    assertTrue(step.rewardMessageObserved)
    assertNotNull(step.rewardDecisionToLog)
    assertEquals(DecisionType.REWARD_MESSAGE_DETECTED.name, step.rewardDecisionToLog?.decisionType)
    assertTrue(step.rewardDecisionToLog?.reason?.contains("sem fechamento automático") == true)
    assertEquals(AutomationPhase.SKIP_READY, step.nextPhase)
  }
}
