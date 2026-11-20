package com.example.data

import kotlinx.coroutines.flow.Flow

class AutoPilotRepository(
  private val dao: AutoPilotDao,
  private val settingsRepository: SettingsRepository,
) {
  val decisions: Flow<List<AdDecisionEntity>> = dao.observeDecisions()
  val learnedPatterns: Flow<List<LearnedPatternEntity>> = dao.observeLearnedPatterns()

  suspend fun logDecision(decision: AdDecisionEntity): Long {
    return dao.insertDecision(decision)
  }

  suspend fun getAllLearnedPatterns(): List<LearnedPatternEntity> {
    return dao.getAllLearnedPatternsSnapshot()
  }

  suspend fun validateDecision(decisionId: Long, isTrueCloseButton: Boolean) {
    val decision = dao.getDecisionById(decisionId) ?: return
    dao.updateDecisionConfirmation(decisionId, isTrueCloseButton)

    val normText = normalizeTextForPattern(decision.detectedText)
    val normId = decision.elementId.substringAfterLast('/').trim().lowercase()
    val existing =
      dao.findMatchingPattern(
        text = normText,
        resId = normId,
        region = decision.regionBucket,
      )

    val updated =
      if (existing != null) {
        existing.copy(
          isSafeClose = isTrueCloseButton,
          confirmationCount =
            if (existing.isSafeClose == isTrueCloseButton) existing.confirmationCount + 1 else 1,
          contextPackage = decision.gamePackage,
          lastUpdated = System.currentTimeMillis(),
        )
      } else {
        LearnedPatternEntity(
          normalizedText = normText,
          resourceIdPattern = normId,
          regionBucket = decision.regionBucket,
          contextPackage = decision.gamePackage,
          isSafeClose = isTrueCloseButton,
          confirmationCount = 1,
          lastUpdated = System.currentTimeMillis(),
        )
      }
    dao.upsertPattern(updated)

    if (isTrueCloseButton) {
      settingsRepository.incrementValidatedObservations()
    }
  }

  suspend fun deleteLearnedPattern(patternId: Long) {
    dao.deletePatternById(patternId)
  }

  suspend fun clearHistory() {
    dao.clearDecisions()
  }

  suspend fun clearLearnedLibrary() {
    dao.clearPatterns()
  }

  companion object {
    fun normalizeTextForPattern(raw: String): String {
      return raw.trim().lowercase().replace(Regex("\\s+"), " ").take(48)
    }
  }
}
