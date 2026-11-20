package com.example.engine

import com.example.data.AutoPilotRepository
import com.example.data.LearnedPatternEntity
import com.example.model.AdContextSnapshot
import com.example.model.ButtonActionKind
import com.example.model.CandidateClassification
import com.example.model.CandidateSource
import com.example.model.RectBounds
import com.example.model.RegionBucket
import com.example.model.SafetyConfig
import com.example.model.ScoredCandidate
import com.example.model.UiCandidate
import com.example.model.VisualSymbolType
import java.text.Normalizer

class RecognitionEngine {

  companion object {
    private val CLOSE_KEYWORDS =
      listOf(
        "fechar anúncio",
        "fechar anuncio",
        "close ad",
        "fechar",
        "close",
        "dispensar",
        "dismiss",
        "agora não",
        "agora nao",
        "no thanks",
        "não, obrigado",
        "nao, obrigado",
        "done",
        "concluir",
      )

    private val SKIP_KEYWORDS =
      listOf(
        "pular anúncio",
        "pular anuncio",
        "skip ad",
        "skip video",
        "pular",
        "skip",
      )

    private val ADVANCE_KEYWORDS =
      listOf(
        "próxima etapa",
        "proxima etapa",
        "next step",
        "next stage",
        "continue watching",
        "continuar",
        "continue",
        "avançar",
        "avancar",
        "próximo",
        "proximo",
        "next",
      )

    private val POSITIVE_TEXT_KEYWORDS =
      SKIP_KEYWORDS + CLOSE_KEYWORDS + ADVANCE_KEYWORDS

    private val SYMBOLIC_X_TOKENS = setOf("x", "×", "✕", "✖", "╳", "✗", "✘")

    private val SYMBOLIC_ADVANCE_TOKENS =
      setOf(">", ">>", ">|", "→", "➔", "➜", "➤", "▶|", "|▶", "›", "»", "─○", "○─")

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
        "abrir loja",
        "open store",
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

    private val PLAYBACK_TRAP_KEYWORDS =
      listOf(
        "play video",
        "watch video",
        "assistir vídeo",
        "assistir video",
        "assistir novamente",
        "ver novamente",
        "replay",
        "pause",
        "pausar",
        "retomar vídeo",
        "retomar video",
        "unmute",
        "mute",
        "silenciar",
        "ativar som",
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

    private val ADVANCE_ID_TOKENS =
      listOf(
        "next_btn",
        "btn_next",
        "continue_btn",
        "btn_continue",
        "step_next",
        "stage_next",
        "skip_forward",
        "advance",
        "avancar",
        "continuar",
        "proximo",
        "next",
        "continue",
        "forward",
      )

    private val PLAYBACK_ID_TOKENS =
      listOf(
        "play_btn",
        "btn_play",
        "video_play",
        "replay",
        "pause_btn",
        "btn_pause",
        "exo_play",
        "exo_pause",
        "media_controller",
        "seekbar",
        "unmute",
        "mute",
        "volume",
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

    private val REWARD_GRANTED_TEXTS =
      listOf(
        "recompensa concedida",
        "recompensa recebida",
        "reward granted",
        "reward received",
        "reward earned",
      )

    private val COUNTDOWN_REGEX_LIST =
      listOf(
        Regex("""\b(?:recompensa em|reward in|pular em|skip in|fecha em|closes in)\s*(\d{1,2})\s*s?\b""", RegexOption.IGNORE_CASE),
        Regex("""^(\d{1,2})\s*s$""", RegexOption.IGNORE_CASE),
        Regex("""^0:(\d{2})$"""),
        Regex("""\b(\d{1,2})\s*segundos?\b""", RegexOption.IGNORE_CASE),
      )

    private fun foldAccents(input: String): String {
      val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
      return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
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
    var rewardGrantedDetected = false
    var rewardMessageText: String? = null

    for (txt in allTexts) {
      val lower = txt.lowercase()
      val folded = foldAccents(lower)
      if (AD_CONTEXT_TEXTS.any { lower.contains(it) || folded.contains(foldAccents(it)) }) {
        indicators.add("Texto: $txt".take(36))
      }
      if (!rewardGrantedDetected && REWARD_GRANTED_TEXTS.any { folded.contains(it) }) {
        rewardGrantedDetected = true
        rewardMessageText = txt
        indicators.add("Recompensa exibida: $txt".take(38))
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
      distinctIndicators.isNotEmpty() || countdownSeconds != null || rewardGrantedDetected

    return AdContextSnapshot(
      foregroundPackage = foregroundPackage,
      isLikelyAdScreen = isLikelyAd,
      adIndicators = distinctIndicators,
      hasActiveCountdown = countdownSeconds != null && countdownSeconds > 0,
      countdownSecondsRemaining = countdownSeconds,
      allVisibleTexts = allTexts.take(40),
      rewardGrantedDetected = rewardGrantedDetected,
      rewardMessageText = rewardMessageText,
    )
  }

  fun mergeCandidates(
    accessibilityCandidates: List<UiCandidate>,
    ocrCandidates: List<UiCandidate>,
    visualCandidates: List<UiCandidate> = emptyList(),
  ): List<UiCandidate> {
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

    if (visualCandidates.isEmpty()) return merged

    val matchedVisualIds = mutableSetOf<String>()
    for (i in merged.indices) {
      val existing = merged[i]
      val matchingVisual =
        visualCandidates.firstOrNull { vis ->
          vis.id !in matchedVisualIds && existing.bounds.overlaps(vis.bounds, threshold = 0.30f)
        }
      if (matchingVisual != null) {
        matchedVisualIds.add(matchingVisual.id)
        val upgradedSource =
          if (existing.source == CandidateSource.ACCESSIBILITY) CandidateSource.HYBRID else existing.source
        val bestSym =
          if (existing.visualSymbol != VisualSymbolType.NONE &&
            existing.visualSymbolConfidence >= matchingVisual.visualSymbolConfidence
          ) {
            existing.visualSymbol
          } else {
            matchingVisual.visualSymbol
          }
        val bestConf = maxOf(existing.visualSymbolConfidence, matchingVisual.visualSymbolConfidence)
        merged[i] =
          existing.copy(
            source = upgradedSource,
            visualSymbol = bestSym,
            visualSymbolConfidence = bestConf,
          )
      }
    }

    for (vis in visualCandidates) {
      if (vis.id !in matchedVisualIds) {
        merged.add(vis)
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
    val textFolded = foldAccents(textLower)
    val descFolded = foldAccents(descLower)
    val idLower = candidate.viewIdResourceName.substringAfterLast('/').trim().lowercase()
    val combined = "$textLower $descLower $idLower".trim()
    val hasVisualSymbol = candidate.visualSymbol != VisualSymbolType.NONE

    // Skip candidates with zero textual, ID, or graphical symbol cues
    if (textLower.isBlank() && descLower.isBlank() && idLower.isBlank() && !hasVisualSymbol) {
      return null
    }

    val region = classifyRegion(candidate.bounds, screenWidth, screenHeight)
    val areaRatio = candidate.bounds.area.toFloat() / screenArea.coerceAtLeast(1L).toFloat()
    val widthRatio = candidate.bounds.width.toFloat() / screenWidth.toFloat()
    val heightRatio = candidate.bounds.height.toFloat() / screenHeight.toFloat()
    val cx = candidate.bounds.centerX.toFloat() / screenWidth.toFloat()
    val cy = candidate.bounds.centerY.toFloat() / screenHeight.toFloat()

    var score = 0.15f
    val signals = mutableListOf<String>()
    var hasTrapSignal = false
    var isCountdown = false
    var isPlaybackControl = false

    // Signal 1: Presented Text
    if (COUNTDOWN_REGEX_LIST.any { it.containsMatchIn(candidate.text.trim()) }) {
      isCountdown = true
      score -= 0.40f
      signals.add("Contagem regressiva detectada no texto (${candidate.text})")
    }

    val trapMatch =
      TRAP_KEYWORDS.firstOrNull { kw ->
        val kwFolded = foldAccents(kw)
        textFolded.contains(kwFolded) || descFolded.contains(kwFolded)
      }
    if (trapMatch != null) {
      hasTrapSignal = true
      score -= 0.65f
      signals.add("Risco de link/instalação no texto: '$trapMatch'")
    }

    val playbackMatch =
      PLAYBACK_TRAP_KEYWORDS.firstOrNull { kw ->
        val kwFolded = foldAccents(kw)
        textFolded.contains(kwFolded) || descFolded.contains(kwFolded)
      }
    if (playbackMatch != null) {
      isPlaybackControl = true
      score -= 0.55f
      signals.add("Controle de reprodução de vídeo detectado: '$playbackMatch'")
    }

    val exactPositive =
      POSITIVE_TEXT_KEYWORDS.firstOrNull { kw ->
        val kwFolded = foldAccents(kw)
        textFolded == kwFolded || descFolded == kwFolded
      }
    val partialPositive =
      if (exactPositive == null) {
        POSITIVE_TEXT_KEYWORDS.firstOrNull { kw ->
          val kwFolded = foldAccents(kw)
          textFolded.contains(kwFolded) || descFolded.contains(kwFolded)
        }
      } else null

    val isContinueWatchingText =
      textFolded.contains("continue watching") || descFolded.contains("continue watching")

    val isSymbolicX = textLower in SYMBOLIC_X_TOKENS || descLower in SYMBOLIC_X_TOKENS
    val isSymbolicAdvance =
      textLower in SYMBOLIC_ADVANCE_TOKENS || descLower in SYMBOLIC_ADVANCE_TOKENS

    val matchedPositiveText = exactPositive ?: partialPositive
    val isAdvanceText =
      matchedPositiveText != null &&
        ADVANCE_KEYWORDS.any { foldAccents(it) == foldAccents(matchedPositiveText) }
    val isSkipText =
      matchedPositiveText != null &&
        SKIP_KEYWORDS.any { foldAccents(it) == foldAccents(matchedPositiveText) }

    when {
      exactPositive != null -> {
        if (isContinueWatchingText) {
          score += if (adContext.isLikelyAdScreen) 0.40f else 0.22f
          signals.add("Texto 'Continue watching' detectado (validado pelo contexto do anúncio)")
        } else {
          score += 0.42f
          signals.add("Texto exato de dispensa/avanço: '$exactPositive'")
        }
      }
      partialPositive != null -> {
        if (isContinueWatchingText) {
          score += if (adContext.isLikelyAdScreen) 0.34f else 0.18f
          signals.add("Texto compatível com avanço condicional: '$partialPositive'")
        } else {
          score += 0.30f
          signals.add("Texto compatível com dispensa/avanço: '$partialPositive'")
        }
      }
      isSymbolicAdvance -> {
        score += 0.26f
        signals.add("Símbolo textual de avanço detectado ('${candidate.text.ifBlank { candidate.contentDescription }}')")
      }
      isSymbolicX -> {
        // Explicit rule: do NOT trust an "X" drawing/character alone!
        score += 0.14f
        signals.add("Símbolo 'X' isolado (exige confirmação adicional)")
      }
    }

    // Signal 1B: Graphical Visual Symbol Recognition (textless or icon-backed)
    if (hasVisualSymbol) {
      val symBoost =
        when (candidate.visualSymbol) {
          VisualSymbolType.ARROW_IN_CIRCLE -> 0.26f + candidate.visualSymbolConfidence * 0.12f
          VisualSymbolType.DASH_CIRCLE,
          VisualSymbolType.RIGHT_ARROW,
          VisualSymbolType.CHEVRON_ADVANCE -> 0.24f + candidate.visualSymbolConfidence * 0.12f
          VisualSymbolType.RIGHT_TRIANGLE -> 0.16f + candidate.visualSymbolConfidence * 0.10f
          VisualSymbolType.NONE -> 0f
        }
      score += symBoost
      signals.add(
        "Símbolo gráfico: ${candidate.visualSymbol.labelPt} (${(candidate.visualSymbolConfidence * 100).toInt()}%)",
      )

      // Guard against center video Play triangles mistaken for advance buttons
      if (
        candidate.visualSymbol == VisualSymbolType.RIGHT_TRIANGLE &&
        cx in 0.28f..0.72f &&
        cy in 0.26f..0.74f &&
        !isAdvanceText &&
        idLower.isBlank()
      ) {
        isPlaybackControl = true
        score -= 0.45f
        signals.add("Triângulo central provável controle de reprodução de vídeo")
      }
    }

    // Signal 2: Accessibility ContentDescription
    if (descLower.isNotEmpty() && POSITIVE_TEXT_KEYWORDS.any { descFolded.contains(foldAccents(it)) }) {
      score += 0.14f
      signals.add("Descrição de acessibilidade confirma fechamento/avanço")
    }

    // Signal 3: Resource ID
    val posIdMatch = POSITIVE_ID_TOKENS.firstOrNull { idLower.contains(it) }
    val advIdMatch = ADVANCE_ID_TOKENS.firstOrNull { idLower.contains(it) }
    val playbackIdMatch = PLAYBACK_ID_TOKENS.firstOrNull { idLower.contains(it) }
    val trapIdMatch = TRAP_ID_TOKENS.firstOrNull { idLower.contains(it) }

    if (posIdMatch != null) {
      score += 0.22f
      signals.add("ID do elemento indica fechar/pular: '$posIdMatch'")
    }
    if (advIdMatch != null && trapIdMatch == null) {
      score += 0.22f
      signals.add("ID do elemento indica avanço/etapa: '$advIdMatch'")
    }
    if (playbackIdMatch != null) {
      isPlaybackControl = true
      score -= 0.55f
      signals.add("ID indica controle de mídia/vídeo: '$playbackIdMatch'")
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

    val isCompactButton =
      (widthRatio <= 0.22f && heightRatio <= 0.15f) ||
        (isAdvanceText && widthRatio <= 0.38f && heightRatio <= 0.14f)

    if (areaRatio > 0.18f || widthRatio > 0.55f) {
      hasTrapSignal = true
      score -= 0.45f
      signals.add("Tamanho excessivo (${(widthRatio * 100).toInt()}% largura) — provável banner")
    } else if (isCompactButton) {
      score += 0.08f
      signals.add("Dimensão compacta compatível com botão")
    }

    val isAdvanceLikeCandidate = isAdvanceText || isSymbolicAdvance || hasVisualSymbol || advIdMatch != null

    // Signal 5: Screen Location (supports left, right, corners, intermediate sides, and heights)
    when (region) {
      RegionBucket.TOP_RIGHT, RegionBucket.TOP_LEFT -> {
        score += 0.14f
        signals.add("Posição típica de dispensa (${region.labelPt})")
      }
      RegionBucket.TOP_BAR -> {
        score += 0.08f
        signals.add("Localizado na barra superior")
      }
      RegionBucket.MID_RIGHT, RegionBucket.MID_LEFT -> {
        if (isAdvanceLikeCandidate && widthRatio <= 0.24f) {
          score += 0.11f
          signals.add("Posição lateral de avanço (${region.labelPt})")
        } else {
          signals.add("Região: ${region.labelPt}")
        }
      }
      RegionBucket.BOTTOM_RIGHT, RegionBucket.BOTTOM_LEFT -> {
        if (isAdvanceLikeCandidate && widthRatio <= 0.24f) {
          score += 0.10f
          signals.add("Posição inferior de avanço (${region.labelPt})")
        } else {
          signals.add("Região: ${region.labelPt}")
        }
      }
      RegionBucket.BOTTOM_BANNER -> {
        score -= 0.22f
        signals.add("Localizado em faixa inferior (risco de CTA)")
      }
      RegionBucket.CENTER -> {
        score -= if (isAdvanceText && widthRatio <= 0.35f) 0.02f else 0.10f
        signals.add("Localizado no centro da tela")
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

    val normText =
      AutoPilotRepository.normalizeTextForPattern(
        candidate.text.ifBlank {
          if (hasVisualSymbol) "simbolo ${candidate.visualSymbol.labelPt}" else ""
        },
      )
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

    // Signal 7: Navigation / Link Opening Clues & Convergence
    if (combined.contains("http") || combined.contains("market://") || combined.contains("play.google")) {
      hasTrapSignal = true
      score -= 0.70f
      signals.add("Contém indício direto de URL/loja externa")
    }
    if (candidate.source == CandidateSource.HYBRID) {
      score += 0.08f
      signals.add("Convergência Acessibilidade + OCR/Visual")
    }

    val clampedScore = score.coerceIn(0.0f, 0.99f)

    // Contextual safety gates before allowing SAFE_CLOSE / automatic action
    val isPureTextlessVisual =
      textLower.isBlank() && descLower.isBlank() && hasVisualSymbol

    val visualSymbolCorroborated =
      !isPureTextlessVisual ||
        (
          adContext.isLikelyAdScreen &&
            (
              matchingPattern?.isSafeClose == true ||
                posIdMatch != null ||
                advIdMatch != null ||
                (
                  region != RegionBucket.BOTTOM_BANNER &&
                    widthRatio <= 0.22f &&
                    heightRatio <= 0.15f &&
                    (
                      region != RegionBucket.CENTER ||
                        (candidate.source == CandidateSource.HYBRID && candidate.visualSymbol != VisualSymbolType.RIGHT_TRIANGLE)
                      ) &&
                    (
                      if (candidate.visualSymbol == VisualSymbolType.RIGHT_TRIANGLE) {
                        candidate.visualSymbolConfidence >= 0.70f &&
                          region in setOf(
                            RegionBucket.TOP_LEFT,
                            RegionBucket.TOP_RIGHT,
                            RegionBucket.TOP_BAR,
                            RegionBucket.MID_LEFT,
                            RegionBucket.MID_RIGHT,
                            RegionBucket.BOTTOM_LEFT,
                            RegionBucket.BOTTOM_RIGHT,
                          )
                      } else {
                        candidate.visualSymbolConfidence >= 0.64f
                      }
                      )
                  )
              )
          )

    val symbolicAdvanceCorroborated =
      !isSymbolicAdvance ||
        posIdMatch != null ||
        advIdMatch != null ||
        matchingPattern?.isSafeClose == true ||
        (region != RegionBucket.CENTER && region != RegionBucket.BOTTOM_BANNER && widthRatio <= 0.20f && adContext.isLikelyAdScreen)

    val continueWatchingCorroborated =
      !isContinueWatchingText ||
        (adContext.isLikelyAdScreen && !adContext.hasActiveCountdown && widthRatio <= 0.45f) ||
        advIdMatch != null ||
        matchingPattern?.isSafeClose == true

    val symbolicXCorroborated =
      !isSymbolicX ||
        posIdMatch != null ||
        matchingPattern?.isSafeClose == true ||
        (region in setOf(RegionBucket.TOP_RIGHT, RegionBucket.TOP_LEFT) && widthRatio <= 0.12f && adContext.isLikelyAdScreen)

    val classification =
      when {
        isCountdown -> CandidateClassification.COUNTDOWN_ACTIVE
        hasTrapSignal -> CandidateClassification.TRAP_AD_LINK
        isPlaybackControl -> CandidateClassification.LOW_CONFIDENCE
        clampedScore >= config.confidenceThreshold &&
          symbolicXCorroborated &&
          visualSymbolCorroborated &&
          symbolicAdvanceCorroborated &&
          continueWatchingCorroborated ->
          CandidateClassification.SAFE_CLOSE
        else -> CandidateClassification.LOW_CONFIDENCE
      }

    val isCloseText =
      matchedPositiveText != null &&
        CLOSE_KEYWORDS.any { foldAccents(it) == foldAccents(matchedPositiveText) }

    val actionKind =
      when {
        hasTrapSignal || isPlaybackControl || classification != CandidateClassification.SAFE_CLOSE ->
          ButtonActionKind.UNKNOWN
        isCloseText || isSymbolicX -> ButtonActionKind.CLOSE_X
        isAdvanceText || isSymbolicAdvance || advIdMatch != null || hasVisualSymbol ->
          ButtonActionKind.ADVANCE_STEP
        isSkipText -> ButtonActionKind.SKIP_AD
        else -> ButtonActionKind.CLOSE_X
      }

    val primaryReason = signals.firstOrNull() ?: "Análise multi-sinal concluída"

    // Filter out irrelevant noise unless it's a trap, countdown, or visual advance candidate worth inspecting
    if (clampedScore < 0.25f && !hasTrapSignal && !isCountdown && !hasVisualSymbol) {
      return null
    }

    return ScoredCandidate(
      candidate = candidate,
      confidenceScore = clampedScore,
      classification = classification,
      regionBucket = region,
      signalBreakdown = signals,
      primaryReason = primaryReason,
      actionKind = actionKind,
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
      cx >= 0.76f -> RegionBucket.MID_RIGHT
      cx <= 0.24f -> RegionBucket.MID_LEFT
      else -> RegionBucket.CENTER
    }
  }
}
