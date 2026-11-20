package com.example.model

enum class OperationMode(val labelPt: String, val descriptionPt: String) {
  OBSERVATION(
    labelPt = "Modo Observação",
    descriptionPt = "Identifica anúncios e botões, destaca na tela e registra decisões sem clicar.",
  ),
  AUTOMATIC(
    labelPt = "Modo Automático",
    descriptionPt = "Executa cliques seguros após validação e protege contra redirecionamentos.",
  ),
}

enum class AutomationPhase(val labelPt: String, val stepIndex: Int) {
  INACTIVE("Inativo", 0),
  MONITORING_GAME("Monitorando Jogo", 0),
  AD_DETECTED("1. Anúncio Identificado", 1),
  WATCHING_AD_PLAYBACK("2. Acompanhando Exibição", 2),
  SKIP_READY("3. Pronto para Avançar", 3),
  SEARCHING_BUTTON("4. Buscando Botão", 4),
  VERIFYING_SAFETY("5. Verificando Segurança", 5),
  EXECUTING_ACTION("6. Executando Ação", 6),
  VERIFYING_CLOSURE("7. Verificando Encerramento", 7),
  RETURNING_TO_GAME("8. Retornando ao Jogo", 8),
  PAUSED("Pausado pelo Usuário", 0),
}

enum class CandidateSource(val labelPt: String) {
  ACCESSIBILITY("Acessibilidade"),
  OCR("OCR Local"),
  VISUAL_SYMBOL("Análise Visual"),
  HYBRID("Híbrido (Árvore + OCR/Visual)"),
}

enum class VisualSymbolType(val labelPt: String) {
  NONE("Nenhum"),
  RIGHT_TRIANGLE("Triângulo para Direita"),
  ARROW_IN_CIRCLE("Seta em Círculo"),
  RIGHT_ARROW("Seta de Avanço"),
  DASH_CIRCLE("Traço e Círculo"),
  CHEVRON_ADVANCE("Seta Dupla / Avanço"),
}

enum class ButtonActionKind(val labelPt: String) {
  CLOSE_X("Fechar (X)"),
  ADVANCE_STEP("Avançar Etapa"),
  SKIP_AD("Pular Anúncio"),
  UNKNOWN("Indefinido"),
}

enum class CandidateClassification(val labelPt: String) {
  SAFE_CLOSE("Botão Seguro"),
  TRAP_AD_LINK("Armadilha / Link"),
  COUNTDOWN_ACTIVE("Contagem Ativa"),
  LOW_CONFIDENCE("Confiança Insuficiente"),
}

enum class DecisionType(val labelPt: String) {
  CLICKED_CLOSE("Fechado Automaticamente"),
  CLICKED_ADVANCE("Etapa Avançada Automaticamente"),
  OBSERVED_CANDIDATE("Observado (Sem Clique)"),
  BLOCKED_AD_TRAP("Armadilha Bloqueada"),
  REDIRECT_RECOVERED("Retorno de Redirecionamento"),
  REWARD_MESSAGE_DETECTED("Recompensa Exibida no Anúncio"),
  WAIT_PLAYBACK("Aguardando Término do Anúncio"),
  IGNORED_LOW_CONFIDENCE("Ignorado (Baixa Confiança)"),
}

enum class RegionBucket(val labelPt: String) {
  TOP_RIGHT("Canto Superior Direito"),
  TOP_LEFT("Canto Superior Esquerdo"),
  MID_RIGHT("Lateral Direita"),
  MID_LEFT("Lateral Esquerda"),
  BOTTOM_RIGHT("Canto Inferior Direito"),
  BOTTOM_LEFT("Canto Inferior Esquerdo"),
  TOP_BAR("Barra Superior"),
  CENTER("Centro / Corpo"),
  BOTTOM_BANNER("Faixa Inferior"),
}

data class RectBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  val width: Int get() = (right - left).coerceAtLeast(0)
  val height: Int get() = (bottom - top).coerceAtLeast(0)
  val centerX: Int get() = (left + right) / 2
  val centerY: Int get() = (top + bottom) / 2
  val area: Long get() = width.toLong() * height.toLong()

  fun overlaps(other: RectBounds, threshold: Float = 0.35f): Boolean {
    val xOverlap = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0)
    val yOverlap = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0)
    val interArea = xOverlap.toLong() * yOverlap.toLong()
    if (interArea <= 0L) return false
    val minArea = minOf(area, other.area).coerceAtLeast(1L)
    return interArea.toFloat() / minArea.toFloat() >= threshold
  }

  fun toCompactString(): String = "$left,$top,$right,$bottom"
}

data class UiCandidate(
  val id: String,
  val text: String,
  val contentDescription: String,
  val viewIdResourceName: String,
  val className: String,
  val packageName: String,
  val bounds: RectBounds,
  val isClickable: Boolean,
  val isEnabled: Boolean,
  val isVisibleToUser: Boolean,
  val source: CandidateSource,
  val visualSymbol: VisualSymbolType = VisualSymbolType.NONE,
  val visualSymbolConfidence: Float = 0f,
)

data class ScoredCandidate(
  val candidate: UiCandidate,
  val confidenceScore: Float,
  val classification: CandidateClassification,
  val regionBucket: RegionBucket,
  val signalBreakdown: List<String>,
  val primaryReason: String,
  val actionKind: ButtonActionKind = ButtonActionKind.CLOSE_X,
)

data class AdContextSnapshot(
  val foregroundPackage: String,
  val isLikelyAdScreen: Boolean,
  val adIndicators: List<String>,
  val hasActiveCountdown: Boolean,
  val countdownSecondsRemaining: Int?,
  val allVisibleTexts: List<String>,
  val rewardGrantedDetected: Boolean = false,
  val rewardMessageText: String? = null,
)

data class SafetyConfig(
  val operationMode: OperationMode = OperationMode.OBSERVATION,
  val confidenceThreshold: Float = 0.72f,
  val clickCooldownMs: Long = 2500L,
  val minAdWatchSeconds: Int = 5,
  val redirectProtectionEnabled: Boolean = true,
  val ocrEnabled: Boolean = true,
  val maxRedirectRetries: Int = 3,
  val observationValidatedCount: Int = 0,
)

data class PermissionStatus(
  val accessibilityEnabled: Boolean = false,
  val overlayEnabled: Boolean = false,
  val screenCaptureActive: Boolean = false,
  val notificationsGranted: Boolean = false,
  val batteryOptimizationIgnored: Boolean = false,
) {
  val isCoreReady: Boolean
    get() = accessibilityEnabled && overlayEnabled

  val missingMandatoryCount: Int
    get() {
      var missing = 0
      if (!accessibilityEnabled) missing++
      if (!overlayEnabled) missing++
      if (!notificationsGranted) missing++
      return missing
    }
}

data class EngineSnapshot(
  val isRunning: Boolean = false,
  val isPaused: Boolean = false,
  val phase: AutomationPhase = AutomationPhase.INACTIVE,
  val mode: OperationMode = OperationMode.OBSERVATION,
  val currentForegroundPackage: String = "",
  val lastGamePackage: String = "",
  val adWatchElapsedSeconds: Int = 0,
  val ocrActive: Boolean = false,
  val ocrProcessingLatencyMs: Long = 0L,
  val lastActionSummary: String = "Aguardando início do serviço",
  val latestCandidates: List<ScoredCandidate> = emptyList(),
  val redirectRecoveriesSession: Int = 0,
  val safeClicksSession: Int = 0,
  val blockedTrapsSession: Int = 0,
  val adStageCount: Int = 0,
  val rewardMessageObserved: Boolean = false,
)
