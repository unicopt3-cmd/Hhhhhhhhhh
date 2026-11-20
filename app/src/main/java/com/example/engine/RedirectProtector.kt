package com.example.engine

data class RedirectEvaluation(
  val isExternalRedirect: Boolean,
  val targetAppLabel: String,
  val shouldAttemptBack: Boolean,
  val recoveryVerified: Boolean,
  val summaryPt: String,
)

class RedirectProtector {

  companion object {
    val EXTERNAL_REDIRECT_PACKAGES =
      mapOf(
        "com.android.vending" to "Google Play Store",
        "com.sec.android.app.samsungapps" to "Samsung Galaxy Store",
        "com.google.android.packageinstaller" to "Instalador de Pacotes",
        "com.android.packageinstaller" to "Instalador de Pacotes",
        "com.samsung.android.packageinstaller" to "Instalador Samsung",
        "com.android.chrome" to "Google Chrome",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "org.mozilla.firefox" to "Firefox",
        "com.microsoft.emmx" to "Microsoft Edge",
        "com.opera.browser" to "Opera",
      )

    val IGNORED_SYSTEM_PACKAGES =
      setOf(
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.settings",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin",
      )
  }

  private var lastGamePackage: String = ""
  private var lastAdActivityTimestamp: Long = 0L
  private var redirectAttemptsForCurrentIncident: Int = 0
  private var lastRecoveryAttemptMs: Long = 0L
  private var awaitingReturnVerification: Boolean = false
  private var ownPackageName: String = "com.aistudio.ptautopilot.kx9v2m"

  fun setOwnPackageName(pkg: String) {
    ownPackageName = pkg
  }

  fun markAdSessionActive() {
    lastAdActivityTimestamp = System.currentTimeMillis()
  }

  fun getLastGamePackage(): String = lastGamePackage

  fun resetIncidentState() {
    redirectAttemptsForCurrentIncident = 0
    awaitingReturnVerification = false
  }

  fun onForegroundPackageChanged(
    newPackage: String,
    protectionEnabled: Boolean,
    maxRetries: Int,
  ): RedirectEvaluation? {
    if (newPackage.isBlank() || newPackage == ownPackageName || newPackage in IGNORED_SYSTEM_PACKAGES) {
      return null
    }

    val now = System.currentTimeMillis()
    val redirectTargetName = EXTERNAL_REDIRECT_PACKAGES[newPackage]

    // Verify if the game returned after a recovery attempt
    if (awaitingReturnVerification && newPackage == lastGamePackage) {
      awaitingReturnVerification = false
      redirectAttemptsForCurrentIncident = 0
      return RedirectEvaluation(
        isExternalRedirect = false,
        targetAppLabel = newPackage,
        shouldAttemptBack = false,
        recoveryVerified = true,
        summaryPt = "Retorno ao jogo confirmado ($newPackage)",
      )
    }

    if (redirectTargetName != null) {
      val recentlyInGameOrAd =
        lastGamePackage.isNotBlank() &&
          (now - lastAdActivityTimestamp <= 90_000L || awaitingReturnVerification)

      if (protectionEnabled && recentlyInGameOrAd) {
        val cooldownPassed = (now - lastRecoveryAttemptMs) >= 1400L
        val underLimit = redirectAttemptsForCurrentIncident < maxRetries

        return if (underLimit && cooldownPassed) {
          redirectAttemptsForCurrentIncident++
          lastRecoveryAttemptMs = now
          awaitingReturnVerification = true
          RedirectEvaluation(
            isExternalRedirect = true,
            targetAppLabel = redirectTargetName,
            shouldAttemptBack = true,
            recoveryVerified = false,
            summaryPt =
              "Redirecionamento para $redirectTargetName detectado (tentativa $redirectAttemptsForCurrentIncident/$maxRetries para voltar a $lastGamePackage)",
          )
        } else if (!underLimit) {
          awaitingReturnVerification = false
          RedirectEvaluation(
            isExternalRedirect = true,
            targetAppLabel = redirectTargetName,
            shouldAttemptBack = false,
            recoveryVerified = false,
            summaryPt =
              "Limite de $maxRetries tentativas atingido em $redirectTargetName — ciclo interrompido com segurança",
          )
        } else {
          null
        }
      }
      return null
    }

    // Track legitimate foreground game/app
    lastGamePackage = newPackage
    lastAdActivityTimestamp = now
    redirectAttemptsForCurrentIncident = 0
    return null
  }
}
