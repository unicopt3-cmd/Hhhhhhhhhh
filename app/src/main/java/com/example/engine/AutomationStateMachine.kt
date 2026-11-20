package com.example.engine

import com.example.data.AdDecisionEntity
import com.example.model.AdContextSnapshot
import com.example.model.AutomationPhase
import com.example.model.ButtonActionKind
import com.example.model.CandidateClassification
import com.example.model.DecisionType
import com.example.model.OperationMode
import com.example.model.SafetyConfig
import com.example.model.ScoredCandidate
import com.example.model.VisualSymbolType

data class StateMachineStepResult(
  val nextPhase: AutomationPhase,
  val adWatchElapsedSeconds: Int,
  val candidateToClick: ScoredCandidate?,
  val overlayHighlights: List<ScoredCandidate>,
  val decisionToLog: AdDecisionEntity?,
  val statusSummaryPt: String,
  val adStageCount: Int = 0,
  val rewardMessageObserved: Boolean = false,
  val rewardDecisionToLog: AdDecisionEntity? = null,
)

class AutomationStateMachine {

  companion object {
    private const val MAX_ADVANCE_CLICKS_PER_AD = 4
  }

  private var currentPhase: AutomationPhase = AutomationPhase.INACTIVE
  private var adStartTimestampMs: Long = 0L
  private var lastClickTimestampMs: Long = 0L
  private var lastLoggedSignature: String = ""
  private var lastLoggedTimestampMs: Long = 0L
  private var closureVerifyStartMs: Long = 0L

  // Multi-step ad & reward-message state
  private var adStageCount: Int = 0
  private var advanceClicksInCurrentAd: Int = 0
  private var lastClickedActionKind: ButtonActionKind? = null
  private var preClickContentSignature: String = ""
  private var lastBlockedNoChangeSignature: String = ""
  private var noChangeAttemptsOnStage: Int = 0
  private var rewardMessageObservedInAd: Boolean = false
  private var lastLoggedRewardMessage: String? = null

  fun reset(running: Boolean, paused: Boolean) {
    currentPhase =
      when {
        !running -> AutomationPhase.INACTIVE
        paused -> AutomationPhase.PAUSED
        else -> AutomationPhase.MONITORING_GAME
      }
    clearAdSessionState()
  }

  private fun clearAdSessionState() {
    adStartTimestampMs = 0L
    closureVerifyStartMs = 0L
    adStageCount = 0
    advanceClicksInCurrentAd = 0
    lastClickedActionKind = null
    preClickContentSignature = ""
    lastBlockedNoChangeSignature = ""
    noChangeAttemptsOnStage = 0
    rewardMessageObservedInAd = false
    lastLoggedRewardMessage = null
  }

  fun step(
    isRunning: Boolean,
    isPaused: Boolean,
    adContext: AdContextSnapshot,
    scoredCandidates: List<ScoredCandidate>,
    config: SafetyConfig,
    lastGamePackage: String,
    nowMs: Long = System.currentTimeMillis(),
  ): StateMachineStepResult {
    if (!isRunning) {
      currentPhase = AutomationPhase.INACTIVE
      clearAdSessionState()
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
        adStageCount = adStageCount,
        rewardMessageObserved = rewardMessageObservedInAd,
      )
    }

    val contentSignature = buildContentSignature(adContext, scoredCandidates)
    var rewardLog: AdDecisionEntity? = null
    if (adContext.rewardGrantedDetected) {
      rewardMessageObservedInAd = true
      val msg = adContext.rewardMessageText ?: "Recompensa exibida no anúncio"
      if (msg != lastLoggedRewardMessage) {
        lastLoggedRewardMessage = msg
        rewardLog =
          AdDecisionEntity(
            timestamp = nowMs,
            gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
            adContext = adContext.adIndicators.joinToString(" | ").ifBlank { adContext.foregroundPackage },
            detectedText = msg.take(60),
            elementId = "",
            source = "Contexto do Anúncio",
            regionBucket = "CENTER",
            boundsCompact = "0,0,0,0",
            confidenceScore = 0.90f,
            decisionType = DecisionType.REWARD_MESSAGE_DETECTED.name,
            reason = "Mensagem exibida no anúncio: '$msg' (sem fechamento automático e sem confirmação externa de crédito)",
            signalDetails = adContext.adIndicators.joinToString("\n"),
            userConfirmed = null,
          )
      }
    }

    // 7. Verify if previously clicked ad has closed or transitioned to a new stage
    if (currentPhase == AutomationPhase.VERIFYING_CLOSURE) {
      if (!adContext.isLikelyAdScreen && scoredCandidates.none { it.classification == CandidateClassification.SAFE_CLOSE }) {
        currentPhase = AutomationPhase.MONITORING_GAME
        clearAdSessionState()
        return StateMachineStepResult(
          nextPhase = currentPhase,
          adWatchElapsedSeconds = 0,
          candidateToClick = null,
          overlayHighlights = emptyList(),
          decisionToLog = null,
          statusSummaryPt = "Anúncio encerrado; monitorando jogo",
          rewardDecisionToLog = rewardLog,
        )
      }

      if (lastClickedActionKind == ButtonActionKind.ADVANCE_STEP) {
        if (contentSignature != preClickContentSignature) {
          adStageCount++
          noChangeAttemptsOnStage = 0
          lastBlockedNoChangeSignature = ""
          lastClickedActionKind = null
          currentPhase = AutomationPhase.SEARCHING_BUTTON
          // Continue directly to analyze the new ad stage below
        } else if (nowMs - closureVerifyStartMs < 1800L) {
          val elapsed = ((nowMs - adStartTimestampMs) / 1000L).toInt().coerceAtLeast(0)
          return StateMachineStepResult(
            nextPhase = AutomationPhase.VERIFYING_CLOSURE,
            adWatchElapsedSeconds = elapsed,
            candidateToClick = null,
            overlayHighlights = scoredCandidates.take(4),
            decisionToLog = null,
            statusSummaryPt = "Aguardando transição para próxima etapa do anúncio...",
            adStageCount = adStageCount,
            rewardMessageObserved = rewardMessageObservedInAd,
            rewardDecisionToLog = rewardLog,
          )
        } else {
          noChangeAttemptsOnStage++
          if (noChangeAttemptsOnStage >= 2) {
            lastBlockedNoChangeSignature = preClickContentSignature
          }
          lastClickedActionKind = null
        }
      } else if (nowMs - closureVerifyStartMs < 2200L) {
        val elapsed = ((nowMs - adStartTimestampMs) / 1000L).toInt().coerceAtLeast(0)
        return StateMachineStepResult(
          nextPhase = AutomationPhase.VERIFYING_CLOSURE,
          adWatchElapsedSeconds = elapsed,
          candidateToClick = null,
          overlayHighlights = scoredCandidates.take(4),
          decisionToLog = null,
          statusSummaryPt = "Confirmando encerramento do anúncio...",
          adStageCount = adStageCount,
          rewardMessageObserved = rewardMessageObservedInAd,
          rewardDecisionToLog = rewardLog,
        )
      }
    }

    if (!adContext.isLikelyAdScreen && scoredCandidates.none { it.classification == CandidateClassification.SAFE_CLOSE }) {
      clearAdSessionState()
      currentPhase = AutomationPhase.MONITORING_GAME
      return StateMachineStepResult(
        nextPhase = AutomationPhase.MONITORING_GAME,
        adWatchElapsedSeconds = 0,
        candidateToClick = null,
        overlayHighlights = emptyList(),
        decisionToLog = null,
        statusSummaryPt = "Monitorando interface (${adContext.foregroundPackage.ifBlank { "tela ativa" }})",
        rewardDecisionToLog = rewardLog,
      )
    }

    // 1. Identify the ad
    if (adStartTimestampMs == 0L) {
      adStartTimestampMs = nowMs
      adStageCount = 1
      currentPhase = AutomationPhase.AD_DETECTED
    }
    val elapsedSec = ((nowMs - adStartTimestampMs) / 1000L).toInt().coerceAtLeast(0)

    // 2. Watch playback & respect minimum watch duration and active countdowns
    if (adContext.hasActiveCountdown || elapsedSec < config.minAdWatchSeconds) {
      currentPhase = AutomationPhase.WATCHING_AD_PLAYBACK
      val remaining =
        adContext.countdownSecondsRemaining
          ?: (config.minAdWatchSeconds - elapsedSec).coerceAtLeast(1)
      val rewardNote = if (rewardMessageObservedInAd) " • Recompensa exibida" else ""
      return StateMachineStepResult(
        nextPhase = currentPhase,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(4),
        decisionToLog = null,
        statusSummaryPt = "Acompanhando anúncio (${remaining}s restantes para segurança$rewardNote)",
        adStageCount = adStageCount,
        rewardMessageObserved = rewardMessageObservedInAd,
        rewardDecisionToLog = rewardLog,
      )
    }

    // 3 & 4. Playback ready -> Search for appropriate button (prefer CLOSE_X > SKIP_AD > ADVANCE_STEP)
    currentPhase = AutomationPhase.SEARCHING_BUTTON
    val safeCandidates = scoredCandidates.filter { it.classification == CandidateClassification.SAFE_CLOSE }
    val bestSafe =
      safeCandidates.firstOrNull { it.actionKind == ButtonActionKind.CLOSE_X }
        ?: safeCandidates.firstOrNull { it.actionKind == ButtonActionKind.SKIP_AD }
        ?: safeCandidates.firstOrNull { it.actionKind == ButtonActionKind.ADVANCE_STEP }
        ?: safeCandidates.firstOrNull()

    val topTrap =
      scoredCandidates.firstOrNull { it.classification == CandidateClassification.TRAP_AD_LINK }

    if (bestSafe == null) {
      val trapDecision =
        if (topTrap != null && shouldLogSignature("TRAP:${topTrap.candidate.text}:${topTrap.candidate.viewIdResourceName}", nowMs)) {
          buildDecisionEntity(
            scored = topTrap,
            gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
            adContext = adContext,
            decisionType = DecisionType.BLOCKED_AD_TRAP,
            timestampMs = nowMs,
          )
        } else null

      val rewardPrefix = if (rewardMessageObservedInAd) "Recompensa exibida; " else ""
      return StateMachineStepResult(
        nextPhase = AutomationPhase.SKIP_READY,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(5),
        decisionToLog = trapDecision,
        statusSummaryPt =
          if (topTrap != null) "${rewardPrefix}Armadilha ignorada (${topTrap.candidate.text.ifBlank { "CTA" }}); aguardando botão real"
          else "${rewardPrefix}Aguardando botão seguro de fechar/avançar (etapa $adStageCount)",
        adStageCount = adStageCount,
        rewardMessageObserved = rewardMessageObservedInAd,
        rewardDecisionToLog = rewardLog,
      )
    }

    // 5. Verify safety & cooldown
    currentPhase = AutomationPhase.VERIFYING_SAFETY
    val cooldownElapsed = (nowMs - lastClickTimestampMs) >= config.clickCooldownMs
    val labelForLog =
      bestSafe.candidate.text.ifBlank {
        if (bestSafe.candidate.visualSymbol != VisualSymbolType.NONE) {
          bestSafe.candidate.visualSymbol.labelPt
        } else {
          bestSafe.regionBucket.labelPt
        }
      }
    val signature =
      "${config.operationMode.name}:${bestSafe.actionKind.name}:$labelForLog:${bestSafe.candidate.viewIdResourceName}:${bestSafe.regionBucket.name}:s$adStageCount"

    // Observation mode: never execute automatic clicks; highlight & log for user confirmation
    if (config.operationMode == OperationMode.OBSERVATION) {
      val logEntry =
        if (shouldLogSignature(signature, nowMs)) {
          buildDecisionEntity(
            scored = bestSafe,
            gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
            adContext = adContext,
            decisionType = DecisionType.OBSERVED_CANDIDATE,
            timestampMs = nowMs,
          )
        } else null

      return StateMachineStepResult(
        nextPhase = AutomationPhase.VERIFYING_SAFETY,
        adWatchElapsedSeconds = elapsedSec,
        candidateToClick = null,
        overlayHighlights = scoredCandidates.take(5),
        decisionToLog = logEntry,
        statusSummaryPt =
          "Observação (etapa $adStageCount): '$labelForLog' [${bestSafe.actionKind.labelPt}] (${(bestSafe.confidenceScore * 100).toInt()}%)",
        adStageCount = adStageCount,
        rewardMessageObserved = rewardMessageObservedInAd,
        rewardDecisionToLog = rewardLog,
      )
    }

    // Guard against looping on advance buttons that did not change the stage
    if (bestSafe.actionKind == ButtonActionKind.ADVANCE_STEP) {
      if (advanceClicksInCurrentAd >= MAX_ADVANCE_CLICKS_PER_AD) {
        return StateMachineStepResult(
          nextPhase = AutomationPhase.SKIP_READY,
          adWatchElapsedSeconds = elapsedSec,
          candidateToClick = null,
          overlayHighlights = scoredCandidates.take(4),
          decisionToLog = null,
          statusSummaryPt = "Limite de avanços na mesma propaganda atingido; aguardando botão X",
          adStageCount = adStageCount,
          rewardMessageObserved = rewardMessageObservedInAd,
          rewardDecisionToLog = rewardLog,
        )
      }
      if (lastBlockedNoChangeSignature.isNotEmpty() && contentSignature == lastBlockedNoChangeSignature) {
        return StateMachineStepResult(
          nextPhase = AutomationPhase.SKIP_READY,
          adWatchElapsedSeconds = elapsedSec,
          candidateToClick = null,
          overlayHighlights = scoredCandidates.take(4),
          decisionToLog = null,
          statusSummaryPt = "Tela não mudou após avanço anterior; aguardando botão de fechamento",
          adStageCount = adStageCount,
          rewardMessageObserved = rewardMessageObservedInAd,
          rewardDecisionToLog = rewardLog,
        )
      }
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
        adStageCount = adStageCount,
        rewardMessageObserved = rewardMessageObservedInAd,
        rewardDecisionToLog = rewardLog,
      )
    }

    // 6. Execute safe click (either closing or advancing to next ad stage)
    lastClickTimestampMs = nowMs
    closureVerifyStartMs = nowMs
    lastClickedActionKind = bestSafe.actionKind
    preClickContentSignature = contentSignature
    if (bestSafe.actionKind == ButtonActionKind.ADVANCE_STEP) {
      advanceClicksInCurrentAd++
    }
    currentPhase = AutomationPhase.VERIFYING_CLOSURE

    val decisionType =
      if (bestSafe.actionKind == ButtonActionKind.ADVANCE_STEP) {
        DecisionType.CLICKED_ADVANCE
      } else {
        DecisionType.CLICKED_CLOSE
      }

    val clickDecision =
      if (shouldLogSignature(signature, nowMs)) {
        buildDecisionEntity(
          scored = bestSafe,
          gamePackage = lastGamePackage.ifBlank { adContext.foregroundPackage },
          adContext = adContext,
          decisionType = decisionType,
          timestampMs = nowMs,
        )
      } else null

    val actionVerb =
      if (bestSafe.actionKind == ButtonActionKind.ADVANCE_STEP) "Avançando etapa $adStageCount"
      else "Fechando anúncio"

    return StateMachineStepResult(
      nextPhase = AutomationPhase.EXECUTING_ACTION,
      adWatchElapsedSeconds = elapsedSec,
      candidateToClick = bestSafe,
      overlayHighlights = listOf(bestSafe),
      decisionToLog = clickDecision,
      statusSummaryPt =
        "$actionVerb: '$labelForLog' (${(bestSafe.confidenceScore * 100).toInt()}%)",
      adStageCount = adStageCount,
      rewardMessageObserved = rewardMessageObservedInAd,
      rewardDecisionToLog = rewardLog,
    )
  }

  private fun buildContentSignature(
    adContext: AdContextSnapshot,
    scoredCandidates: List<ScoredCandidate>,
  ): String {
    val textsPart = adContext.allVisibleTexts.take(8).joinToString("|") { it.trim().lowercase() }
    val candPart =
      scoredCandidates.take(4).joinToString(";") {
        "${it.candidate.text}:${it.candidate.visualSymbol.name}:${it.regionBucket.name}:${it.candidate.bounds.centerX / 40},${it.candidate.bounds.centerY / 40}"
      }
    return "${adContext.foregroundPackage}#${adContext.countdownSecondsRemaining}#$textsPart#$candPart"
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
    timestampMs: Long = System.currentTimeMillis(),
  ): AdDecisionEntity {
    val detectedLabel =
      scored.candidate.text.ifBlank {
        scored.candidate.contentDescription.ifBlank {
          if (scored.candidate.visualSymbol != VisualSymbolType.NONE) {
            "Símbolo: ${scored.candidate.visualSymbol.labelPt}"
          } else {
            "Ícone sem texto"
          }
        }
      }
    return AdDecisionEntity(
      timestamp = timestampMs,
      gamePackage = gamePackage,
      adContext = adContext.adIndicators.joinToString(" | ").ifBlank { adContext.foregroundPackage },
      detectedText = detectedLabel,
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
