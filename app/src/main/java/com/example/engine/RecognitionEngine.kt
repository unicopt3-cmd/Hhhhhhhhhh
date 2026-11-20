package com.example.engine

import com.example.data.AutoPilotRepository
import com.example.data.LearnedPatternEntity
import com.example.model.AdContextSnapshot
import com.example.model.CandidateClassification
import com.example.model.CandidateSource
import com.example.model.RectBounds
import com.example.model.RegionBucket
import com.example.model.SafetyConfig
import com.example.model.ScoredCandidate
import com.example.model.UiCandidate

class RecognitionEngine {

  companion object {
    private val POSITIVE_TEXT_KEYWORDS =
      listOf(
        "pular anúncio",
        "pular anuncio",
        "skip ad",
        "skip video",
        "fechar anúncio",
        "fechar anuncio",
        "close ad",
        "pular",
        "skip",
        "fechar",
        "close",
        "avançar",
        "avancar",
        "continuar",
        "continue",
        "dispensar",
        "dismiss",
        "agora não",
        "agora nao",
        "no thanks",
        "não, obrigado",
        "nao, obrigado",
        "next",
        "próximo",
        "proximo",
        "done",
        "concluir",
      )

    private val SYMBOLIC_X_TOKENS = setOf("x", "×", "✕", "✖", "╳", "✗", "✘", ">>", ">|")

    private val TRAP_KEYWORDS =
      listOf(
        "instalar",
        "install",
        "baixar",
        "download",
        "obter",
        "get it",
        "get now",
        "visitar",
        "visit",
        "abrir",
        "open",
        "saiba mais",
        "learn more",
        "jogar agora",
        "play now",
        "jogue agora",
        "compre",
        "buy now",
        "shop now",
        "ver mais",
        "see more",
        "google play",
        "app store",
        "galaxy store",
        "http://",
        "https://",
        "market://",
        "adchoices",
        "ad choices",
        "patrocinado",
        "sponsored",
        "assinar",
        "subscribe",
        "testar",
        "try now",
        "reivindicar",
        "claim",
      )

    private val POSITIVE_ID_TOKENS =
      listOf(
        "close",
        "skip",
        "dismiss",
        "cancel",
        "fechar",
        "pular",
        "btn_close",
        "close_btn",
        "skip_btn",
        "ia_tv_close",
        "mbridge_windown_close",
        "al_close",
        "tt_video_ad_close",
        "ad_close",
        "reward_close",
      )

    private val TRAP_ID_TOKENS =
      listOf(
        "install",
        "download",
        "cta",
        "click_area",
        "ad_container",
        "ad_cta",
        "store",
        "visit",
        "banner_body",
        "open_app",
        "action_button",
      )

    private val AD_SDK_HINTS =
      listOf(
        "admob",
        "ads",
        "applovin",
        "mbridge",
        "unityads",
        "ironsource",
        "vungle",
        "inmobi",
        "fyber",
        "chartboost",
        "bytedance",
        "pangle",
        "adcolony",
        "google.android.gms.ads",
        "ia_tv",
        "tt_video",
      )

    private val AD_CONTEXT_TEXTS =
      listOf(
        "anúncio",
        "anuncio",
        "patrocinado",
        "sponsored",
        "recompensa",
        "reward",
        "instalar",
        "install",
        "google play",
        "pular anúncio",
        "skip ad",
        "ad choices",
        "saiba mais",
        "learn more",
      )

    private val COUNTDOWN_REGEX_LIST =
      listOf(
        Regex("""\b(?:recompensa em|reward in|pular em|skip in|fecha em|closes in)\s*(\d{1,2})\s*s?\b""", RegexOption.IGNORE_CASE),
        Regex("""^(\d{1,2})\s*s$""", RegexOption.IGNORE_CASE),
        Regex("""^0:(\d{2})$"""),
        Regex("""\b(\d{1,2})\s*segundos?\b""", RegexOption.IGNORE_CASE),
      )
  }

  fun analyzeAdContext(
    foregroundPackage: String,
    candidates: List<UiCandidate>,
    allOcrTexts: List<String>,
  ): AdContextSnapshot {
    val indicators = mutableListOf<String>()
    val allTexts =
      (candidates.flatMap { listOf(it.text, it.contentDescription) } + allOcrTexts)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    for (candidate in candidates) {
      val idLower = candidate.viewIdResourceName.lowercase()
      val clsLower = candidate.className.lowercase()
      if (AD_SDK_HINTS.any { idLower.contains(it) || clsLower.contains(it) }) {
        indicators.add("SDK/ID: ${candidate.viewIdResourceName.substringAfterLast('/')}")
      }
      if (clsLower.contains("webview") && candidates.size <= 16) {
        indicators.add("WebView em tela cheia")
      }
    }

    var countdownSeconds: Int? = null
    for (txt in allTexts) {
      val lower = txt.lowercase()
      if (AD_CONTEXT_TEXTS.any { lower.contains(it) }) {
        indicators.add("Texto: $txt".take(36))
      }
      for (regex in COUNTDOWN_REGEX_LIST) {
        val match = regex.find(txt)
        if (match != null) {
          val sec = match.groupValues.getOrNull(1)?.toIntOrNull()
          if (sec != null && sec in 1..90) {
            countdownSeconds =
              if (countdownSeconds == null) sec else minOf(countdownSeconds, sec)
          }
        }
      }
    }

    val distinctIndicators = indicators.distinct().take(6)
    val isLikelyAd =
      distinctIndicators.isNotEmpty() || countdownSeconds != null

    return AdContextSnapshot(
      foregroundPackage = foregroundPackage,
      isLikelyAdScreen = isLikelyAd,
      adIndicators = distinctIndicators,
      hasActiveCountdown = countdownSeconds != null && countdownSeconds > 0,
      countdownSecondsRemaining = countdownSeconds,
      allVisibleTexts = allTexts.take(40),
    )
  }

  fun mergeCandidates(
    accessibilityCandidates: List<UiCandidate>,
    ocrCandidates: List<UiCandidate>,
  ): List<UiCandidate> {
    if (ocrCandidates.isEmpty()) return accessibilityCandidates
    if (accessibilityCandidates.isEmpty()) return ocrCandidates

    val merged = accessibilityCandidates.toMutableList()
    val matchedOcrIds = mutableSetOf<String>()

    for (i in merged.indices) {
      val acc = merged[i]
      val matchingOcr =
        ocrCandidates.firstOrNull { ocr ->
          ocr.id !in matchedOcrIds && acc.bounds.overlaps(ocr.bounds, threshold = 0.25f)
        }
      if (matchingOcr != null) {
        matchedOcrIds.add(matchingOcr.id)
        val combinedText =
          if (acc.text.isBlank()) matchingOcr.text
          else if (matchingOcr.text.isBlank() || acc.text.equals(matchingOcr.text, ignoreCase = true)) acc.text
          else "${acc.text} | ${matchingOcr.text}"
        merged[i] =
          acc.copy(
            text = combinedText,
            source = CandidateSource.HYBRID,
          )
      }
    }

    for (ocr in ocrCandidates) {
      if (ocr.id !in matchedOcrIds) {
        merged.add(ocr)
      }
    }
    return merged
  }

  fun scoreCandidates(
    candidates: List<UiCandidate>,
    adContext: AdContextSnapshot,
    learnedPatterns: List<LearnedPatternEntity>,
    screenWidth: Int,
    screenHeight: Int,
    config: SafetyConfig,
  ): List<ScoredCandidate> {
    val safeW = screenWidth.coerceAtLeast(1)
    val safeH = screenHeight.coerceAtLeast(1)
    val screenArea = safeW.toLong() * safeH.toLong()

    return candidates
      .filter { it.isVisibleToUser && it.bounds.width >= 10 && it.bounds.height >= 10 }
      .mapNotNull { candidate ->
        scoreSingleCandidate(
          candidate = candidate,
          adContext = adContext,
          learnedPatterns = learnedPatterns,
          screenWidth = safeW,
          screenHeight = safeH,
          screenArea = screenArea,
          config = config,
        )
      }
      .sortedByDescending { it.confidenceScore }
  }

  private fun scoreSingleCandidate(
    candidate: UiCandidate,
    adContext: AdContextSnapshot,
    learnedPatterns: List<LearnedPatternEntity>,
    screenWidth: Int,
    screenHeight: Int,
    screenArea: Long,
    config: SafetyConfig,
  ): ScoredCandidate? {
    val textLower = candidate.text.trim().lowercase()
    val descLower = candidate.contentDescription.trim().lowercase()
    val idLower = candidate.viewIdResourceName.substringAfterLast('/').trim().lowercase()
    val combined = "$textLower $descLower $idLower".trim()

    // Skip candidates with zero textual or ID cues unless they are small clickable corner buttons with an ID
    if (textLower.isBlank() && descLower.isBlank() && idLower.isBlank()) {
      return null
    }

    val region = classifyRegion(candidate.bounds, screenWidth, screenHeight)
    val areaRatio = candidate.bounds.area.toFloat() / screenArea.coerceAtLeast(1L).toFloat()
    val widthRatio = candidate.bounds.width.toFloat() / screenWidth.toFloat()

    var score = 0.15f
    val signals = mutableListOf<String>()
    var hasTrapSignal = false
    var isCountdown = false

    // Signal 1: Presented Text
    if (COUNTDOWN_REGEX_LIST.any { it.containsMatchIn(candidate.text.trim()) }) {
      isCountdown = true
      score -= 0.40f
      signals.add("Contagem regressiva detectada no texto (${candidate.text})")
    }

    val trapMatch = TRAP_KEYWORDS.firstOrNull { textLower.contains(it) || descLower.contains(it) }
    if (trapMatch != null) {
      hasTrapSignal = true
      score -= 0.65f
      signals.add("Risco de link/instalação no texto: '$trapMatch'")
    }

    val exactPositive =
      POSITIVE_TEXT_KEYWORDS.firstOrNull { kw ->
        textLower == kw || descLower == kw
      }
    val partialPositive =
      if (exactPositive == null) {
        POSITIVE_TEXT_KEYWORDS.firstOrNull { kw ->
          textLower.contains(kw) || descLower.contains(kw)
        }
      } else null

    val isSymbolicX = textLower in SYMBOLIC_X_TOKENS || descLower in SYMBOLIC_X_TOKENS

    when {
      exactPositive != null -> {
        score += 0.42f
        signals.add("Texto exato de dispensa: '$exactPositive'")
      }
      partialPositive != null -> {
        score += 0.30f
        signals.add("Texto compatível com dispensa: '$partialPositive'")
      }
      isSymbolicX -> {
        // Explicit rule: do NOT trust an "X" drawing/character alone!
        score += 0.14f
        signals.add("Símbolo 'X' isolado (exige confirmação adicional)")
      }
    }

    // Signal 2: Accessibility ContentDescription
    if (descLower.isNotEmpty() && POSITIVE_TEXT_KEYWORDS.any { descLower.contains(it) }) {
      score += 0.14f
      signals.add("Descrição de acessibilidade confirma fechamento")
    }

    // Signal 3: Resource ID
    val posIdMatch = POSITIVE_ID_TOKENS.firstOrNull { idLower.contains(it) }
    val trapIdMatch = TRAP_ID_TOKENS.firstOrNull { idLower.contains(it) }
    if (posIdMatch != null) {
      score += 0.22f
      signals.add("ID do elemento indica fechar/pular: '$posIdMatch'")
    }
    if (trapIdMatch != null) {
      hasTrapSignal = true
      score -= 0.50f
      signals.add("ID indica área promocional/CTA: '$trapIdMatch'")
    }

    // Signal 4: Interactivity & Geometry sanity
    if (candidate.isClickable && candidate.isEnabled) {
      score += 0.08f
      signals.add("Elemento interativo e habilitado")
    } else if (candidate.source == CandidateSource.ACCESSIBILITY && !candidate.isClickable) {
      score -= 0.12f
      signals.add("Nó não marcado como clicável na árvore")
    }

    if (areaRatio > 0.18f || widthRatio > 0.55f) {
      hasTrapSignal = true
      score -= 0.45f
      signals.add("Tamanho excessivo (${(widthRatio * 100).toInt()}% largura) — provável banner")
    } else if (widthRatio <= 0.18f && candidate.bounds.height <= screenHeight * 0.14f) {
      score += 0.08f
      signals.add("Dimensão compacta compatível com botão")
    }

    // Signal 5: Screen Location
    when (region) {
      RegionBucket.TOP_RIGHT, RegionBucket.TOP_LEFT -> {
        score += 0.14f
        signals.add("Posição típica de dispensa (${region.labelPt})")
      }
      RegionBucket.TOP_BAR -> {
        score += 0.08f
        signals.add("Localizado na barra superior")
      }
      RegionBucket.BOTTOM_BANNER -> {
        score -= 0.22f
        signals.add("Localizado em faixa inferior (risco de CTA)")
      }
      RegionBucket.CENTER -> {
        score -= 0.10f
        signals.add("Localizado no centro da tela")
      }
      else -> {
        signals.add("Região: ${region.labelPt}")
      }
    }

    // Signal 6: Ad Context & Learned Pattern Library
    if (adContext.isLikelyAdScreen) {
      score += 0.06f
      signals.add("Contexto de anúncio ativo confirmado")
    } else {
      score -= 0.12f
      signals.add("Sem indícios claros de tela publicitária")
    }

    val normText = AutoPilotRepository.normalizeTextForPattern(candidate.text)
    val matchingPattern =
      learnedPatterns.firstOrNull { p ->
        (p.normalizedText == normText && normText.isNotEmpty()) ||
          (idLower.isNotEmpty() && p.resourceIdPattern == idLower)
      }
    if (matchingPattern != null) {
      if (matchingPattern.isSafeClose) {
        val boost = (0.12f + (matchingPattern.confirmationCount * 0.03f)).coerceAtMost(0.24f)
        score += boost
        signals.add("Confirmado pela biblioteca local (${matchingPattern.confirmationCount}x)")
      } else {
        hasTrapSignal = true
        score -= 0.60f
        signals.add("Marcado anteriormente como armadilha pelo usuário")
      }
    }

    // Signal 7: Navigation / Link Opening Clues & Active Countdown Guard
    if (combined.contains("http") || combined.contains("market://") || combined.contains("play.google")) {
      hasTrapSignal = true
      score -= 0.70f
      signals.add("Contém indício direto de URL/loja externa")
    }
    if (candidate.source == CandidateSource.HYBRID) {
      score += 0.08f
      signals.add("Convergência Acessibilidade + OCR Local")
    }

    val clampedScore = score.coerceIn(0.0f, 0.99f)

    val classification =
      when {
        isCountdown -> CandidateClassification.COUNTDOWN_ACTIVE
        hasTrapSignal -> CandidateClassification.TRAP_AD_LINK
        clampedScore >= config.confidenceThreshold &&
          (!isSymbolicX || posIdMatch != null || matchingPattern?.isSafeClose == true ||
            (region in setOf(RegionBucket.TOP_RIGHT, RegionBucket.TOP_LEFT) && widthRatio <= 0.12f && adContext.isLikelyAdScreen)) ->
          CandidateClassification.SAFE_CLOSE
        else -> CandidateClassification.LOW_CONFIDENCE
      }

    val primaryReason = signals.firstOrNull() ?: "Análise multi-sinal concluída"

    // Filter out totally irrelevant low-score noise unless it's a trap or high enough to inspect
    if (clampedScore < 0.25f && !hasTrapSignal && !isCountdown) {
      return null
    }

    return ScoredCandidate(
      candidate = candidate,
      confidenceScore = clampedScore,
      classification = classification,
      regionBucket = region,
      signalBreakdown = signals,
      primaryReason = primaryReason,
    )
  }

  fun classifyRegion(bounds: RectBounds, screenWidth: Int, screenHeight: Int): RegionBucket {
    val cx = bounds.centerX.toFloat() / screenWidth.coerceAtLeast(1).toFloat()
    val cy = bounds.centerY.toFloat() / screenHeight.coerceAtLeast(1).toFloat()
    val widthRatio = bounds.width.toFloat() / screenWidth.coerceAtLeast(1).toFloat()

    return when {
      cy <= 0.22f && cx >= 0.72f -> RegionBucket.TOP_RIGHT
      cy <= 0.22f && cx <= 0.28f -> RegionBucket.TOP_LEFT
      cy <= 0.18f -> RegionBucket.TOP_BAR
      cy >= 0.78f && widthRatio >= 0.40f -> RegionBucket.BOTTOM_BANNER
      cy >= 0.78f && cx >= 0.70f -> RegionBucket.BOTTOM_RIGHT
      cy >= 0.78f && cx <= 0.30f -> RegionBucket.BOTTOM_LEFT
      else -> RegionBucket.CENTER
    }
  }
}
