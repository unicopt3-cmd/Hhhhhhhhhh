package com.example.engine

import com.example.data.AdDecisionEntity
import com.example.model.AdContextSnapshot
import com.example.model.AutomationPhase
import com.example.model.CandidateClassification
import com.example.model.DecisionType
import com.example.model.OperationMode
import com.example.model.SafetyConfig
import com.example.model.ScoredCandidate

data class StateMachineStepResult(
  val nextPhase: AutomationPhase,
  val adWatchElapsedSeconds: Int,
  val candidateToClick: ScoredCandidate?,
  val overlayHighlights: List<ScoredCandidate>,
  val decisionToLog: AdDecisionEntity?,
  val statusSummaryPt: String,
)

class AutomationStateMachine {

  private var currentPhase: AutomationPhase = AutomationPhase.INACTIVE
  private var adStartTimestampMs: Long = 0L
  private var lastClickTimestampMs: Long = 0L
  private var lastLoggedSignature: String = ""
  private var lastLoggedTimestampMs: Long = 0L
  private var closureVerifyStartMs: Long = 0L

  fun reset(running: Boolean, paused: Boolean) {
    currentPhase =
      when {
        !running -> AutomationPhase.INACTIVE
        paused -> AutomationPhase.PAUSED
        else -> AutomationPhase.MONITORING_GAME
      }
    adStartTimestampMs = 0L
    closureVerifyStartMs = 0L
  }

  fun step(
    isRunning: Boolean,
    isPaused: Boolean,
    adContext: AdContextSnapshot,
    scoredCandidates: List<ScoredCandidate>,
    config: SafetyConfig,
    lastGamePackage: String,
  ): StateMachineStepResult {
    val now = System.currentTimeMillis()

    if (!isRunning) {
      currentPhase = AutomationPhase.INACTIVE
      return StateMachineStepResult(
        nextPhase = currentPhase,
        adWatchElapsedSeconds = 0,
        candidateToClick = null,
        overlayHighlights = emptyList(),
        decisionToLog = null,
        statusSummaryPt = "Serviço parado",
      )
    }

    if (isPaused) {
      currentPhase = AutomationPhase.PAUSED
      return StateMachineStepResult(
        nextPhase = currentPhase,
        adWatchElapsedSeconds = 0,
        candidateToClick = null,
        overlayHighlights = emptyList(),
        decisionToLog = null,
        statusSummaryPt = "Automação pausada",
      )
    }

    // 7. Verify if previously clicked ad has closed
    if (currentPhase == AutomationPhase.VERIFYING_CLOSURE) {
      if (!adContext.isLikelyAdScreen) {
        currentPhase = AutomationPhase.MONITORING_GAME
        adStartTimestampMs = 0L
        return StateMachineStepResult(
          nextPhase = currentPhase,
          adWatchElapsedSeconds = 0,
          candidateToClick = null,
          overlayHighlights = emptyList(),
          decisionToLog = null,
          statusSummaryPt = "Anúncio encerrado; monitorando jogo",
        )
      }
      if (now - closureVerifyStartMs < 2200L) {
        val elapsed = ((now - adStartTimestampMs) / 1000L).toInt().coerceAtLeast(0)
        return StateMachineStepResult(
          nextPhase = AutomationPhase.VERIFYING_CLOSURE,
          adWatchElapsedSeconds = elapsed,
          candidateToClick = null,
          overlayHighlights = scoredCandidates.take(4),
          decisionToLog = null,
          statusSummaryPt = "Confirmando encerramento do anúncio...",
        )
      }
    }

    if (!adContext.isLikelyAdScreen && scoredCandidates.none { it.classification == CandidateClassification.SAFE_CLOSE }) {
      adStartTimestampMs = 0L
      currentPhase = AutomationPhase.MONITORING_GAME
      return StateMachineStepResult(
        nextPhase = AutomationPhase.MONITORING_GAME,
        adWatchElapsedSeconds = 0,
        candidateToClick = null,
        overlayHighlights = emptyList(),
        decisionToLog = null,
        statusSummaryPt = "Monitorando interface (${adContext.foregroundPackage.ifBlank { "tela ativa" }})",
      )
    }

    // 1. Identify the ad
    if (adStartTimestampMs == 0L) {
      adStartTimestampMs = now
      currentPhase = AutomationPhase.AD_DETECTED
    }
    val elapsedSec = ((now - adStartTimestampMs) / 1000L).toInt().coerceAtLeast(0)

    // 2. Watch playback & respect minimum watch duration and active countdowns
    if (adContext.hasActiveCountdown || elapsedSec < config.minAdWatchSeconds) {
      currentPhase = AutomationPhase.WATCHING_AD_PLAYBACK
      val remaining =
        adContext.countdownSecondsRemaining
          ?: (config.minAdWatchSeconds - elapsedSec).coerceAtLeast(1)
      return StateMachineStepResult(
        nextPhase = currentPhase,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(4),
        decisionToLog = null,
        statusSummaryPt = "Acompanhando anúncio (${remaining}s restantes para segurança)",
      )
    }

    // 3 & 4. Playback ready -> Search for appropriate button
    currentPhase = AutomationPhase.SEARCHING_BUTTON
    val bestSafe =
      scoredCandidates.firstOrNull { it.classification == CandidateClassification.SAFE_CLOSE }
    val topTrap =
      scoredCandidates.firstOrNull { it.classification == CandidateClassification.TRAP_AD_LINK }

    if (bestSafe == null) {
      // Log blocked trap if a deceptive button was detected and skipped
      val trapDecision =
        if (topTrap != null && shouldLogSignature("TRAP:${topTrap.candidate.text}:${topTrap.candidate.viewIdResourceName}", now)) {
          buildDecisionEntity(
            scored = topTrap,
            gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
            adContext = adContext,
            decisionType = DecisionType.BLOCKED_AD_TRAP,
          )
        } else null

      return StateMachineStepResult(
        nextPhase = AutomationPhase.SKIP_READY,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(4),
        decisionToLog = trapDecision,
        statusSummaryPt =
          if (topTrap != null) "Armadilha ignorada (${topTrap.candidate.text.ifBlank { "CTA" }}); aguardando botão real"
          else "Aguardando botão seguro de fechar/pular",
      )
    }

    // 5. Verify safety & cooldown
    currentPhase = AutomationPhase.VERIFYING_SAFETY
    val cooldownElapsed = (now - lastClickTimestampMs) >= config.clickCooldownMs
    val signature = "${config.operationMode.name}:${bestSafe.candidate.text}:${bestSafe.candidate.viewIdResourceName}:${bestSafe.regionBucket.name}"

    // Observation mode: never execute automatic clicks; highlight & log for user confirmation
    if (config.operationMode == OperationMode.OBSERVATION) {
      val logEntry =
        if (shouldLogSignature(signature, now)) {
          buildDecisionEntity(
            scored = bestSafe,
            gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
            adContext = adContext,
            decisionType = DecisionType.OBSERVED_CANDIDATE,
          )
        } else null

      return StateMachineStepResult(
        nextPhase = AutomationPhase.VERIFYING_SAFETY,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(5),
        decisionToLog = logEntry,
        statusSummaryPt =
          "Observação: '${bestSafe.candidate.text.ifBlank { "Botão" }}' (${(bestSafe.confidenceScore * 100).toInt()}%) pronto para validação",
      )
    }

    // Automatic mode
    if (!cooldownElapsed) {
      return StateMachineStepResult(
        nextPhase = AutomationPhase.VERIFYING_SAFETY,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(4),
        decisionToLog = null,
        statusSummaryPt = "Aguardando intervalo de segurança entre cliques",
      )
    }

    // 6. Execute safe click
    lastClickTimestampMs = now
    closureVerifyStartMs = now
    currentPhase = AutomationPhase.VERIFYING_CLOSURE

    val clickDecision =
      if (shouldLogSignature(signature, now)) {
        buildDecisionEntity(
          scored = bestSafe,
          gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
          adContext = adContext,
          decisionType = DecisionType.CLICKED_CLOSE,
        )
      } else null

    return StateMachineStepResult(
      nextPhase = AutomationPhase.EXECUTING_ACTION,
      adWatchElapsedSeconds = elapsedSec,
      candidateToClick = bestSafe,
      overlayHighlights = listOf(bestSafe),
      decisionToLog = clickDecision,
      statusSummaryPt =
        "Fechando anúncio: '${bestSafe.candidate.text.ifBlank { bestSafe.regionBucket.labelPt }}' (${(bestSafe.confidenceScore * 100).toInt()}%)",
    )
  }

  private fun shouldLogSignature(signature: String, nowMs: Long): Boolean {
    if (signature == lastLoggedSignature && (nowMs - lastLoggedTimestampMs) < 5500L) {
      return false
    }
    lastLoggedSignature = signature
    lastLoggedTimestampMs = nowMs
    return true
  }

  private fun buildDecisionEntity(
    scored: ScoredCandidate,
    gamePackage: String,
    adContext: AdContextSnapshot,
    decisionType: DecisionType,
  ): AdDecisionEntity {
    return AdDecisionEntity(
      timestamp = System.currentTimeMillis(),
      gamePackage = gamePackage,
      adContext = adContext.adIndicators.joinToString(" | ").ifBlank { adContext.foregroundPackage },
      detectedText = scored.candidate.text.ifBlank { scored.candidate.contentDescription.ifBlank { "Ícone sem texto" } },
      elementId = scored.candidate.viewIdResourceName,
      source = scored.candidate.source.labelPt,
      regionBucket = scored.regionBucket.name,
      boundsCompact = scored.candidate.bounds.toCompactString(),
      confidenceScore = scored.confidenceScore,
      decisionType = decisionType.name,
      reason = scored.primaryReason,
      signalDetails = scored.signalBreakdown.joinToString("\n"),
      userConfirmed = null,
    )
  }
}
