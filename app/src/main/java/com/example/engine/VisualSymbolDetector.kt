package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.CandidateSource
import com.example.model.RectBounds
import com.example.model.UiCandidate
import com.example.model.VisualSymbolType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class VisualShapeClassification(
  val symbolType: VisualSymbolType,
  val confidence: Float,
)

class VisualSymbolDetector {

  companion object {
    private const val GRID_SIZE = 24
    private const val MAX_WORK_DIMENSION = 280
    private const val MAX_VISUAL_CANDIDATES = 8
    private const val MIN_SYMBOL_CONFIDENCE = 0.58f
  }

  fun inspectAccessibilityCandidates(
    bitmap: Bitmap,
    scaleToScreenX: Float,
    scaleToScreenY: Float,
    accessibilityCandidates: List<UiCandidate>,
  ): List<UiCandidate> {
    if (bitmap.isRecycled || bitmap.width < 16 || bitmap.height < 16) return accessibilityCandidates
    val safeScaleX = scaleToScreenX.takeIf { it > 0f } ?: 1f
    val safeScaleY = scaleToScreenY.takeIf { it > 0f } ?: 1f
    val screenW = bitmap.width * safeScaleX
    val screenH = bitmap.height * safeScaleY

    return accessibilityCandidates.map { candidate ->
      if (!candidate.isVisibleToUser || candidate.visualSymbol != VisualSymbolType.NONE) {
        return@map candidate
      }
      val wRatio = candidate.bounds.width / screenW.coerceAtLeast(1f)
      val hRatio = candidate.bounds.height / screenH.coerceAtLeast(1f)
      val looksLikeControlSize = wRatio in 0.018f..0.28f && hRatio in 0.018f..0.18f
      val needsVisualCheck =
        candidate.text.isBlank() ||
          candidate.text.trim().length <= 3 ||
          candidate.className.contains("Image", ignoreCase = true)

      if (!looksLikeControlSize || !needsVisualCheck) {
        return@map candidate
      }

      val roiLeft = (candidate.bounds.left / safeScaleX).roundToInt().coerceIn(0, bitmap.width - 2)
      val roiTop = (candidate.bounds.top / safeScaleY).roundToInt().coerceIn(0, bitmap.height - 2)
      val roiRight = (candidate.bounds.right / safeScaleX).roundToInt().coerceIn(roiLeft + 2, bitmap.width)
      val roiBottom = (candidate.bounds.bottom / safeScaleY).roundToInt().coerceIn(roiTop + 2, bitmap.height)

      val shape = classifyBitmapRegion(bitmap, roiLeft, roiTop, roiRight, roiBottom)
      if (shape.symbolType != VisualSymbolType.NONE && shape.confidence >= MIN_SYMBOL_CONFIDENCE) {
        candidate.copy(
          visualSymbol = shape.symbolType,
          visualSymbolConfidence = shape.confidence,
        )
      } else {
        candidate
      }
    }
  }

  fun detectGraphicalAdvanceCandidates(
    bitmap: Bitmap,
    scaleToScreenX: Float,
    scaleToScreenY: Float,
    foregroundPackage: String,
  ): List<UiCandidate> {
    if (bitmap.isRecycled || bitmap.width < 32 || bitmap.height < 32) return emptyList()
    val safeScaleX = scaleToScreenX.takeIf { it > 0f } ?: 1f
    val safeScaleY = scaleToScreenY.takeIf { it > 0f } ?: 1f

    val maxDim = max(bitmap.width, bitmap.height)
    val downScale = if (maxDim > MAX_WORK_DIMENSION) MAX_WORK_DIMENSION.toFloat() / maxDim else 1f
    val workW = (bitmap.width * downScale).roundToInt().coerceAtLeast(32)
    val workH = (bitmap.height * downScale).roundToInt().coerceAtLeast(32)

    val workBitmap =
      if (downScale < 0.99f) {
        Bitmap.createScaledBitmap(bitmap, workW, workH, true)
      } else {
        bitmap
      }

    try {
      val pixels = IntArray(workW * workH)
      workBitmap.getPixels(pixels, 0, workW, 0, 0, workW, workH)

      val lum = IntArray(workW * workH)
      for (i in pixels.indices) {
        val c = pixels[i]
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        lum[i] = (r * 77 + g * 150 + b * 29) shr 8
      }

      // Integral image for O(W*H) local background estimation
      val sat = IntArray((workW + 1) * (workH + 1))
      for (y in 0 until workH) {
        var rowSum = 0
        val rowOffset = y * workW
        val satRow = (y + 1) * (workW + 1)
        val satPrev = y * (workW + 1)
        for (x in 0 until workW) {
          rowSum += lum[rowOffset + x]
          sat[satRow + x + 1] = sat[satPrev + x + 1] + rowSum
        }
      }

      val radius = max(5, min(workW, workH) / 18)
      val brightMask = BooleanArray(workW * workH)
      val darkMask = BooleanArray(workW * workH)

      for (y in 0 until workH) {
        val y0 = max(0, y - radius)
        val y1 = min(workH - 1, y + radius)
        for (x in 0 until workW) {
          val x0 = max(0, x - radius)
          val x1 = min(workW - 1, x + radius)
          val count = (x1 - x0 + 1) * (y1 - y0 + 1)
          val sum =
            sat[(y1 + 1) * (workW + 1) + (x1 + 1)] -
              sat[y0 * (workW + 1) + (x1 + 1)] -
              sat[(y1 + 1) * (workW + 1) + x0] +
              sat[y0 * (workW + 1) + x0]
          val localMean = sum / count.coerceAtLeast(1)
          val diff = lum[y * workW + x] - localMean
          if (diff >= 24) brightMask[y * workW + x] = true
          else if (diff <= -24) darkMask[y * workW + x] = true
        }
      }

      val rawBoxes = mutableListOf<RawBox>()
      rawBoxes.addAll(extractCompactComponents(brightMask, workW, workH))
      rawBoxes.addAll(extractCompactComponents(darkMask, workW, workH))

      // Merge horizontally adjacent sub-components (e.g., dash + circle or play + bar)
      val pairBoxes = buildAdjacentPairBoxes(rawBoxes, workW, workH)
      val allCandidateBoxes = (rawBoxes + pairBoxes).distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }

      val invDownScaleX = bitmap.width.toFloat() / workW.toFloat()
      val invDownScaleY = bitmap.height.toFloat() / workH.toFloat()

      val detected = mutableListOf<UiCandidate>()
      var idx = 0

      for (box in allCandidateBoxes) {
        val wRatio = (box.right - box.left).toFloat() / workW.toFloat()
        val hRatio = (box.bottom - box.top).toFloat() / workH.toFloat()
        val aspect = wRatio / hRatio.coerceAtLeast(0.001f)
        if (wRatio !in 0.022f..0.22f || hRatio !in 0.018f..0.16f || aspect !in 0.45f..2.75f) {
          continue
        }

        val padX = max(2, ((box.right - box.left) * invDownScaleX * 0.14f).roundToInt())
        val padY = max(2, ((box.bottom - box.top) * invDownScaleY * 0.14f).roundToInt())
        val bLeft = ((box.left * invDownScaleX).roundToInt() - padX).coerceIn(0, bitmap.width - 4)
        val bTop = ((box.top * invDownScaleY).roundToInt() - padY).coerceIn(0, bitmap.height - 4)
        val bRight = ((box.right * invDownScaleX).roundToInt() + padX).coerceIn(bLeft + 4, bitmap.width)
        val bBottom = ((box.bottom * invDownScaleY).roundToInt() + padY).coerceIn(bTop + 4, bitmap.height)

        val shape = classifyBitmapRegion(bitmap, bLeft, bTop, bRight, bBottom)
        if (shape.symbolType != VisualSymbolType.NONE && shape.confidence >= MIN_SYMBOL_CONFIDENCE) {
          val screenBounds =
            RectBounds(
              left = (bLeft * safeScaleX).roundToInt(),
              top = (bTop * safeScaleY).roundToInt(),
              right = (bRight * safeScaleX).roundToInt(),
              bottom = (bBottom * safeScaleY).roundToInt(),
            )
          detected.add(
            UiCandidate(
              id = "vis_sym_${idx++}",
              text = "",
              contentDescription = "",
              viewIdResourceName = "",
              className = "VisualAdvanceSymbol:${shape.symbolType.name}",
              packageName = foregroundPackage,
              bounds = screenBounds,
              isClickable = true,
              isEnabled = true,
              isVisibleToUser = true,
              source = CandidateSource.VISUAL_SYMBOL,
              visualSymbol = shape.symbolType,
              visualSymbolConfidence = shape.confidence,
            )
          )
        }
      }

      // Non-maximum suppression: keep highest-confidence non-overlapping visual symbols
      val kept = mutableListOf<UiCandidate>()
      for (cand in detected.sortedByDescending { it.visualSymbolConfidence }) {
        if (kept.none { it.bounds.overlaps(cand.bounds, threshold = 0.30f) }) {
          kept.add(cand)
          if (kept.size >= MAX_VISUAL_CANDIDATES) break
        }
      }
      return kept
    } finally {
      if (workBitmap !== bitmap) {
        runCatching { workBitmap.recycle() }
      }
    }
  }

  fun classifyBitmapRegion(
    bitmap: Bitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ): VisualShapeClassification {
    val w = (right - left).coerceAtLeast(1)
    val h = (bottom - top).coerceAtLeast(1)
    if (w < 8 || h < 8 || left < 0 || top < 0 || right > bitmap.width || bottom > bitmap.height) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, left, top, w, h)
    val lum = IntArray(w * h)
    for (i in pixels.indices) {
      val c = pixels[i]
      lum[i] = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
    }

    return classifyLuminancePatch(lum, w, h)
  }

  internal fun classifyLuminancePatch(lum: IntArray, w: Int, h: Int): VisualShapeClassification {
    if (w < 8 || h < 8 || lum.size < w * h) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    val borderSamples = mutableListOf<Int>()
    for (x in 0 until w) {
      borderSamples.add(lum[x])
      borderSamples.add(lum[(h - 1) * w + x])
    }
    for (y in 1 until h - 1) {
      borderSamples.add(lum[y * w])
      borderSamples.add(lum[y * w + (w - 1)])
    }
    borderSamples.sort()
    val bgLum = borderSamples[borderSamples.size / 2]

    var minLum = 255
    var maxLum = 0
    for (v in lum) {
      if (v < minLum) minLum = v
      if (v > maxLum) maxLum = v
    }
    val dynamicRange = maxLum - minLum
    if (dynamicRange < 28) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    // 1. Check circular container + inner right-pointing arrow/triangle/chevron
    val circleCheck = checkCircularContainerWithInnerAdvance(lum, w, h, bgLum)
    if (circleCheck.symbolType != VisualSymbolType.NONE && circleCheck.confidence >= MIN_SYMBOL_CONFIDENCE) {
      return circleCheck
    }

    // 2. Test direct bright foreground vs dark foreground relative to border background
    val brightThreshold = bgLum + max(24, ((maxLum - bgLum) * 0.36f).roundToInt())
    val darkThreshold = bgLum - max(24, ((bgLum - minLum) * 0.36f).roundToInt())

    val brightMask = BooleanArray(w * h) { lum[it] >= brightThreshold }
    val darkMask = BooleanArray(w * h) { lum[it] <= darkThreshold }

    val brightResult = evaluateMaskInPatch(brightMask, w, h)
    val darkResult = evaluateMaskInPatch(darkMask, w, h)

    return if (brightResult.confidence >= darkResult.confidence) brightResult else darkResult
  }

  private fun checkCircularContainerWithInnerAdvance(
    lum: IntArray,
    w: Int,
    h: Int,
    bgLum: Int,
  ): VisualShapeClassification {
    val minDim = min(w, h).toFloat()
    val cx = (w - 1) * 0.5f
    val cy = (h - 1) * 0.5f
    val ringR1 = minDim * 0.34f
    val ringR2 = minDim * 0.39f
    val innerMaxR = minDim * 0.31f

    var ringSum = 0
    var ringCount = 0
    var sectorsConsistentBright = 0
    var sectorsConsistentDark = 0
    val sectors = 20

    for (s in 0 until sectors) {
      val angle = (2.0 * Math.PI * s) / sectors
      val sx = (cx + cos(angle) * ringR1).roundToInt().coerceIn(0, w - 1)
      val sy = (cy + sin(angle) * ringR1).roundToInt().coerceIn(0, h - 1)
      val sx2 = (cx + cos(angle) * ringR2).roundToInt().coerceIn(0, w - 1)
      val sy2 = (cy + sin(angle) * ringR2).roundToInt().coerceIn(0, h - 1)
      val sample = (lum[sy * w + sx] + lum[sy2 * w + sx2]) / 2
      ringSum += sample
      ringCount++
      if (sample - bgLum >= 22) sectorsConsistentBright++
      if (bgLum - sample >= 22) sectorsConsistentDark++
    }

    if (ringCount == 0) return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    val ringMean = ringSum / ringCount
    val bestSectorAgreement = max(sectorsConsistentBright, sectorsConsistentDark).toFloat() / sectors.toFloat()

    // Check interior ring consistency (either solid circular disc OR stroked circle outline)
    val innerSamples = mutableListOf<Int>()
    var innerMin = 255
    var innerMax = 0
    for (y in 0 until h) {
      for (x in 0 until w) {
        if (hypot(x - cx, y - cy) <= innerMaxR) {
          val v = lum[y * w + x]
          innerSamples.add(v)
          if (v < innerMin) innerMin = v
          if (v > innerMax) innerMax = v
        }
      }
    }
    if (innerSamples.size < 16 || innerMax - innerMin < 28) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    innerSamples.sort()
    val innerBg = innerSamples[innerSamples.size / 2]

    // Require either filled disc differing from outside OR stroked ring outline differing from inner background
    val ringVsOutside = abs(ringMean - bgLum)
    val ringVsInner = abs(ringMean - innerBg)
    if (bestSectorAgreement < 0.65f && ringVsInner < 26) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }
    if (ringVsOutside < 20 && ringVsInner < 24) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    // Check glyph inside inner disc using opposite/contrasting polarity against innerBg
    val upRange = innerMax - innerBg
    val downRange = innerBg - innerMin
    val useBrightInner = upRange >= downRange
    val innerMask = BooleanArray(w * h)
    val thresh =
      if (useBrightInner) innerBg + max(22, (upRange * 0.42f).roundToInt())
      else innerBg - max(22, (downRange * 0.42f).roundToInt())

    for (y in 0 until h) {
      for (x in 0 until w) {
        if (hypot(x - cx, y - cy) <= innerMaxR) {
          val v = lum[y * w + x]
          innerMask[y * w + x] = if (useBrightInner) v >= thresh else v <= thresh
        }
      }
    }

    val normWithAspect = normalizeMaskWithAspect(innerMask, w, h) ?: return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    val innerShape = classifyGlyph24x24(normWithAspect.first, allowDashCircle = false, patchAspect = normWithAspect.second)
    if (innerShape.symbolType in setOf(VisualSymbolType.RIGHT_ARROW, VisualSymbolType.CHEVRON_ADVANCE, VisualSymbolType.RIGHT_TRIANGLE)) {
      val circleBoost = (bestSectorAgreement * 0.14f + 0.04f).coerceAtMost(0.16f)
      val totalConf = (innerShape.confidence * 0.86f + circleBoost).coerceIn(0f, 0.96f)
      return VisualShapeClassification(VisualSymbolType.ARROW_IN_CIRCLE, totalConf)
    }

    return VisualShapeClassification(VisualSymbolType.NONE, 0f)
  }

  private fun evaluateMaskInPatch(mask: BooleanArray, w: Int, h: Int): VisualShapeClassification {
    var activeCount = 0
    var borderActive = 0
    val borderTotal = 2 * w + 2 * max(0, h - 2)
    for (y in 0 until h) {
      for (x in 0 until w) {
        if (mask[y * w + x]) {
          activeCount++
          if (x == 0 || x == w - 1 || y == 0 || y == h - 1) borderActive++
        }
      }
    }
    val fill = activeCount.toFloat() / (w * h).toFloat()
    val borderFill = borderActive.toFloat() / borderTotal.coerceAtLeast(1).toFloat()
    if (fill !in 0.05f..0.72f || borderFill > 0.42f) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    val normWithAspect = normalizeMaskWithAspect(mask, w, h) ?: return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    return classifyGlyph24x24(normWithAspect.first, allowDashCircle = true, patchAspect = normWithAspect.second)
  }

  internal fun classifyGlyph24x24(
    grid: BooleanArray,
    allowDashCircle: Boolean = true,
    patchAspect: Float = 1f,
  ): VisualShapeClassification {
    if (grid.size != GRID_SIZE * GRID_SIZE) return VisualShapeClassification(VisualSymbolType.NONE, 0f)

    // 1. Check dash + circle or play + vertical bar (two distinct lobes or dash-circle signature)
    if (allowDashCircle) {
      val dashCircleScore = scoreDashCircle(grid, patchAspect)
      if (dashCircleScore >= MIN_SYMBOL_CONFIDENCE) {
        return VisualShapeClassification(VisualSymbolType.DASH_CIRCLE, dashCircleScore)
      }
    }

    // 2. Check directional rightward arrow / chevron / triangle
    val symY = computeVerticalSymmetry(grid)
    if (symY < 0.56f) {
      return VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }

    val arrowScore = scoreRightArrow(grid, symY)
    val chevronScore = scoreChevronAdvance(grid, symY)
    val triangleScore = scoreRightTriangle(grid, symY)

    val bestType: VisualSymbolType
    val bestScore: Float
    when {
      arrowScore >= chevronScore && arrowScore >= triangleScore -> {
        bestType = VisualSymbolType.RIGHT_ARROW
        bestScore = arrowScore
      }
      chevronScore >= triangleScore -> {
        bestType = VisualSymbolType.CHEVRON_ADVANCE
        bestScore = chevronScore
      }
      else -> {
        bestType = VisualSymbolType.RIGHT_TRIANGLE
        bestScore = triangleScore
      }
    }

    return if (bestScore >= MIN_SYMBOL_CONFIDENCE) {
      VisualShapeClassification(bestType, bestScore.coerceIn(0f, 0.96f))
    } else {
      VisualShapeClassification(VisualSymbolType.NONE, 0f)
    }
  }

  private fun scoreRightArrow(grid: BooleanArray, symY: Float): Float {
    // Characteristics of "→" / "➜" / "➔":
    // - Left half has a horizontal shaft centered vertically (rows 7..16) with empty corners
    // - Right-middle (cols 12..19) expands vertically into an arrowhead
    // - Far right tip (cols 21..23) narrows to a centered point
    val leftCornerFill = regionDensity(grid, 0, 10, 0, 6) + regionDensity(grid, 0, 10, 17, 23)
    if (leftCornerFill / 2f > 0.16f) return 0f

    val leftShaftCenter = regionDensity(grid, 1, 11, 8, 15)
    if (leftShaftCenter < 0.30f) return 0f

    val leftSpan = averageColumnSpan(grid, 1, 10)
    val headSpan = maxColumnSpan(grid, 11, 19)
    val tipSpan = averageColumnSpan(grid, 21, 23)

    if (headSpan < leftSpan * 1.35f || headSpan < 0.45f) return 0f
    if (tipSpan > headSpan * 0.58f) return 0f

    val rightCornerFill = (regionDensity(grid, 18, 23, 0, 4) + regionDensity(grid, 18, 23, 19, 23)) / 2f
    if (rightCornerFill > 0.18f) return 0f

    val yCenter = verticalCentroid(grid)
    if (yCenter !in 0.36f..0.64f) return 0f

    val expandBonus = ((headSpan - leftSpan) * 0.35f).coerceIn(0f, 0.25f)
    val taperBonus = ((headSpan - tipSpan) * 0.25f).coerceIn(0f, 0.20f)
    return (0.42f + symY * 0.20f + expandBonus + taperBonus).coerceIn(0f, 0.95f)
  }

  private fun scoreChevronAdvance(grid: BooleanArray, symY: Float): Float {
    // Characteristics of ">" or ">>":
    // - Left columns have top & bottom diagonal arms, hollow in middle-left (x 0..7, y 9..14)
    // - Right columns converge near middle-right (x 16..23, y 7..16) with empty corners
    val leftTop = regionDensity(grid, 0, 8, 0, 7)
    val leftBottom = regionDensity(grid, 0, 8, 16, 23)
    val leftMiddle = regionDensity(grid, 0, 6, 9, 14)
    val rightMiddle = regionDensity(grid, 16, 23, 8, 15)
    val rightCorners = (regionDensity(grid, 18, 23, 0, 4) + regionDensity(grid, 18, 23, 19, 23)) / 2f

    if (leftTop < 0.16f || leftBottom < 0.16f) return 0f
    if (leftMiddle > 0.30f) return 0f
    if (rightMiddle < 0.24f || rightCorners > 0.16f) return 0f

    val spanLeft = averageColumnSpan(grid, 1, 6)
    val spanRight = averageColumnSpan(grid, 19, 23)
    if (spanLeft < spanRight * 1.35f) return 0f

    val xCentroid = horizontalCentroid(grid)
    // Ensure not symmetric X (X has high rightCorners too, already rejected above)
    if (xCentroid > 0.65f) return 0f

    return (0.46f + symY * 0.24f + ((spanLeft - spanRight) * 0.22f).coerceIn(0f, 0.22f)).coerceIn(0f, 0.94f)
  }

  private fun scoreRightTriangle(grid: BooleanArray, symY: Float): Float {
    // Characteristics of right-pointing triangle (▶ or ▷):
    // - Left vertical base has tall span (>= 0.60) and solid/continuous left edge
    // - Middle third tapers monotonically
    // - Right edge narrows to centered apex, right corners empty
    // - Horizontal centroid shifted left (< 0.48)
    val leftSpan = averageColumnSpan(grid, 1, 6)
    val midSpan = averageColumnSpan(grid, 9, 14)
    val rightSpan = averageColumnSpan(grid, 18, 23)
    val xCentroid = horizontalCentroid(grid)
    val yCentroid = verticalCentroid(grid)
    val rightCorners = (regionDensity(grid, 17, 23, 0, 4) + regionDensity(grid, 17, 23, 19, 23)) / 2f

    if (leftSpan < 0.58f) return 0f
    if (midSpan >= leftSpan * 0.92f || midSpan < 0.20f) return 0f
    if (rightSpan > leftSpan * 0.48f) return 0f
    if (xCentroid !in 0.22f..0.48f || yCentroid !in 0.36f..0.64f) return 0f
    if (rightCorners > 0.15f) return 0f

    // Reject hollow chevron (triangle has solid center-left mass or closed vertical left wall)
    val leftWallDensity = regionDensity(grid, 0, 5, 5, 18)
    if (leftWallDensity < 0.28f) return 0f

    val taperQuality = ((leftSpan - rightSpan) * 0.35f).coerceIn(0f, 0.30f)
    return (0.44f + symY * 0.22f + taperQuality).coerceIn(0f, 0.94f)
  }

  private fun scoreDashCircle(grid: BooleanArray, patchAspect: Float = 1f): Float {
    val components = extractGridComponents24(grid).filter { it.count >= 8 }
    if (components.size == 2) {
      val c1 = components[0]
      val c2 = components[1]
      val pairAB = isDashAndCirclePair(c1, c2, grid, patchAspect)
      val pairBA = isDashAndCirclePair(c2, c1, grid, patchAspect)
      val playBar = max(isTriangleAndBarPair(c1, c2, grid), isTriangleAndBarPair(c2, c1, grid))
      val best = max(max(pairAB, pairBA), playBar)
      if (best >= MIN_SYMBOL_CONFIDENCE) return best
    }

    // Fallback when dash and circle barely touch in 24x24: inspect column density valley
    val colSum = IntArray(GRID_SIZE)
    for (x in 0 until GRID_SIZE) {
      var s = 0
      for (y in 0 until GRID_SIZE) if (grid[y * GRID_SIZE + x]) s++
      colSum[x] = s
    }
    var minColIdx = -1
    var minColVal = Int.MAX_VALUE
    for (x in 6..17) {
      if (colSum[x] < minColVal) {
        minColVal = colSum[x]
        minColIdx = x
      }
    }
    if (minColIdx in 6..17 && minColVal <= 2) {
      val leftComp = summarizeSubRegion(grid, 0, minColIdx - 1)
      val rightComp = summarizeSubRegion(grid, minColIdx + 1, GRID_SIZE - 1)
      if (leftComp != null && rightComp != null) {
        val pair = max(
          max(isDashAndCirclePair(leftComp, rightComp, grid, patchAspect), isDashAndCirclePair(rightComp, leftComp, grid, patchAspect)),
          max(isTriangleAndBarPair(leftComp, rightComp, grid), isTriangleAndBarPair(rightComp, leftComp, grid)),
        )
        if (pair >= MIN_SYMBOL_CONFIDENCE) return pair
      }
    }
    return 0f
  }

  private fun isDashAndCirclePair(
    dash: GridComp,
    circle: GridComp,
    grid: BooleanArray,
    patchAspect: Float = 1f,
  ): Float {
    val dashRealAspect = (dash.width.toFloat() / dash.height.coerceAtLeast(1).toFloat()) * patchAspect
    val isHorizontalDash = dash.width >= 4 && dash.height <= 8 && dashRealAspect >= 1.75f
    val isVerticalBar = dash.height >= 9 && dash.width <= 6 && dashRealAspect <= 0.55f
    if (!isHorizontalDash && !isVerticalBar) return 0f

    val circleAspect = (circle.width.toFloat() / circle.height.coerceAtLeast(1).toFloat()) * patchAspect
    if (circle.width < 4 || circle.height < 5 || circleAspect !in 0.65f..1.50f) return 0f

    val circleSymY = subBoxSymmetryY(grid, circle)
    val circleSymX = subBoxSymmetryX(grid, circle)
    val circleFill = circle.count.toFloat() / (circle.width * circle.height).coerceAtLeast(1).toFloat()
    if (circleSymY < 0.62f || circleSymX < 0.62f || circleFill !in 0.25f..0.95f) return 0f

    val yAlign = 1f - (abs(dash.centerY - circle.centerY) / GRID_SIZE.toFloat())
    if (yAlign < 0.72f) return 0f

    return (0.48f + ((circleSymY + circleSymX) * 0.14f) + (yAlign - 0.72f) * 0.35f).coerceIn(0f, 0.92f)
  }

  private fun isTriangleAndBarPair(
    leftTri: GridComp,
    rightBar: GridComp,
    grid: BooleanArray,
  ): Float {
    // Represents "▶|" skip-forward / next-stage icon
    if (leftTri.centerX >= rightBar.centerX) return 0f
    val isBar = rightBar.height >= 9 && rightBar.width <= 5 && (rightBar.height.toFloat() / rightBar.width.coerceAtLeast(1)) >= 2.0f
    if (!isBar || leftTri.width < 6 || leftTri.height < 6) return 0f

    // Resample leftTri subbox onto 24x24 and score as right triangle or chevron
    val subMask = BooleanArray(GRID_SIZE * GRID_SIZE)
    for (gy in 0 until GRID_SIZE) {
      val sy = (leftTri.top + (gy * leftTri.height) / GRID_SIZE).coerceIn(leftTri.top, leftTri.bottom)
      for (gx in 0 until GRID_SIZE) {
        val sx = (leftTri.left + (gx * leftTri.width) / GRID_SIZE).coerceIn(leftTri.left, leftTri.right)
        subMask[gy * GRID_SIZE + gx] = grid[sy * GRID_SIZE + sx]
      }
    }
    val symY = computeVerticalSymmetry(subMask)
    val triOrChev = max(scoreRightTriangle(subMask, symY), scoreChevronAdvance(subMask, symY))
    if (triOrChev < 0.54f) return 0f

    return (0.50f + triOrChev * 0.42f).coerceIn(0f, 0.93f)
  }

  private fun normalizeMaskWithAspect(mask: BooleanArray, w: Int, h: Int): Pair<BooleanArray, Float>? {
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    var count = 0
    for (y in 0 until h) {
      for (x in 0 until w) {
        if (mask[y * w + x]) {
          count++
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }
    if (count < 12) return null
    val bw = maxX - minX + 1
    val bh = maxY - minY + 1
    if (bw < 5 || bh < 5) return null

    val out = BooleanArray(GRID_SIZE * GRID_SIZE)
    for (gy in 0 until GRID_SIZE) {
      val sy = (minY + ((gy + 0.5f) / GRID_SIZE) * bh).toInt().coerceIn(minY, maxY)
      for (gx in 0 until GRID_SIZE) {
        val sx = (minX + ((gx + 0.5f) / GRID_SIZE) * bw).toInt().coerceIn(minX, maxX)
        out[gy * GRID_SIZE + gx] = mask[sy * w + sx]
      }
    }
    return out to (bw.toFloat() / bh.toFloat())
  }

  private fun computeVerticalSymmetry(grid: BooleanArray): Float {
    var matches = 0
    val total = (GRID_SIZE / 2) * GRID_SIZE
    for (y in 0 until GRID_SIZE / 2) {
      val mirrorY = GRID_SIZE - 1 - y
      for (x in 0 until GRID_SIZE) {
        if (grid[y * GRID_SIZE + x] == grid[mirrorY * GRID_SIZE + x]) matches++
      }
    }
    return matches.toFloat() / total.toFloat()
  }

  private fun regionDensity(grid: BooleanArray, x0: Int, x1: Int, y0: Int, y1: Int): Float {
    var hits = 0
    var total = 0
    for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(GRID_SIZE - 1)) {
      for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(GRID_SIZE - 1)) {
        total++
        if (grid[y * GRID_SIZE + x]) hits++
      }
    }
    return if (total == 0) 0f else hits.toFloat() / total.toFloat()
  }

  private fun columnSpan(grid: BooleanArray, x: Int): Float {
    var top = -1
    var bottom = -1
    for (y in 0 until GRID_SIZE) {
      if (grid[y * GRID_SIZE + x]) {
        if (top == -1) top = y
        bottom = y
      }
    }
    return if (top == -1) 0f else (bottom - top + 1).toFloat() / GRID_SIZE.toFloat()
  }

  private fun averageColumnSpan(grid: BooleanArray, xStart: Int, xEnd: Int): Float {
    var sum = 0f
    var count = 0
    for (x in xStart.coerceAtLeast(0)..xEnd.coerceAtMost(GRID_SIZE - 1)) {
      sum += columnSpan(grid, x)
      count++
    }
    return if (count == 0) 0f else sum / count
  }

  private fun maxColumnSpan(grid: BooleanArray, xStart: Int, xEnd: Int): Float {
    var m = 0f
    for (x in xStart.coerceAtLeast(0)..xEnd.coerceAtMost(GRID_SIZE - 1)) {
      val s = columnSpan(grid, x)
      if (s > m) m = s
    }
    return m
  }

  private fun horizontalCentroid(grid: BooleanArray): Float {
    var sumX = 0f
    var count = 0
    for (y in 0 until GRID_SIZE) {
      for (x in 0 until GRID_SIZE) {
        if (grid[y * GRID_SIZE + x]) {
          sumX += x
          count++
        }
      }
    }
    return if (count == 0) 0.5f else (sumX / count) / (GRID_SIZE - 1).toFloat()
  }

  private fun verticalCentroid(grid: BooleanArray): Float {
    var sumY = 0f
    var count = 0
    for (y in 0 until GRID_SIZE) {
      for (x in 0 until GRID_SIZE) {
        if (grid[y * GRID_SIZE + x]) {
          sumY += y
          count++
        }
      }
    }
    return if (count == 0) 0.5f else (sumY / count) / (GRID_SIZE - 1).toFloat()
  }

  private data class RawBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

  private fun extractCompactComponents(mask: BooleanArray, w: Int, h: Int): List<RawBox> {
    val visited = BooleanArray(w * h)
    val qx = IntArray(w * h)
    val qy = IntArray(w * h)
    val boxes = mutableListOf<RawBox>()

    for (sy in 0 until h) {
      for (sx in 0 until w) {
        val startIdx = sy * w + sx
        if (!mask[startIdx] || visited[startIdx]) continue

        var head = 0
        var tail = 0
        qx[tail] = sx
        qy[tail] = sy
        tail++
        visited[startIdx] = true

        var minX = sx
        var maxX = sx
        var minY = sy
        var maxY = sy
        var count = 0

        while (head < tail) {
          val cx = qx[head]
          val cy = qy[head]
          head++
          count++
          if (cx < minX) minX = cx
          if (cx > maxX) maxX = cx
          if (cy < minY) minY = cy
          if (cy > maxY) maxY = cy

          // 4-connected neighbors
          if (cx + 1 < w && mask[cy * w + cx + 1] && !visited[cy * w + cx + 1]) {
            visited[cy * w + cx + 1] = true
            qx[tail] = cx + 1
            qy[tail] = cy
            tail++
          }
          if (cx - 1 >= 0 && mask[cy * w + cx - 1] && !visited[cy * w + cx - 1]) {
            visited[cy * w + cx - 1] = true
            qx[tail] = cx - 1
            qy[tail] = cy
            tail++
          }
          if (cy + 1 < h && mask[(cy + 1) * w + cx] && !visited[(cy + 1) * w + cx]) {
            visited[(cy + 1) * w + cx] = true
            qx[tail] = cx
            qy[tail] = cy + 1
            tail++
          }
          if (cy - 1 >= 0 && mask[(cy - 1) * w + cx] && !visited[(cy - 1) * w + cx]) {
            visited[(cy - 1) * w + cx] = true
            qx[tail] = cx
            qy[tail] = cy - 1
            tail++
          }
        }

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (count >= 10 && bw >= 4 && bh >= 2 && bw <= w * 0.25f && bh <= h * 0.18f) {
          boxes.add(RawBox(minX, minY, maxX + 1, maxY + 1))
        }
      }
    }
    return boxes
  }

  private fun buildAdjacentPairBoxes(boxes: List<RawBox>, workW: Int, workH: Int): List<RawBox> {
    val pairs = mutableListOf<RawBox>()
    val smallBoxes =
      boxes.filter {
        (it.right - it.left) <= workW * 0.14f && (it.bottom - it.top) <= workH * 0.12f
      }
    for (i in smallBoxes.indices) {
      val a = smallBoxes[i]
      val aCy = (a.top + a.bottom) * 0.5f
      val aH = (a.bottom - a.top).toFloat()
      for (j in i + 1 until smallBoxes.size) {
        val b = smallBoxes[j]
        val bCy = (b.top + b.bottom) * 0.5f
        val bH = (b.bottom - b.top).toFloat()
        val maxH = max(aH, bH)
        if (abs(aCy - bCy) > maxH * 0.70f) continue

        val hGap = max(0, max(a.left, b.left) - min(a.right, b.right))
        if (hGap <= max(maxH * 0.95f, 10f)) {
          val merged =
            RawBox(
              left = min(a.left, b.left),
              top = min(a.top, b.top),
              right = max(a.right, b.right),
              bottom = max(a.bottom, b.bottom),
            )
          val mw = (merged.right - merged.left).toFloat() / workW
          val mh = (merged.bottom - merged.top).toFloat() / workH
          if (mw <= 0.22f && mh <= 0.16f) {
            pairs.add(merged)
          }
        }
      }
    }
    return pairs
  }

  private data class GridComp(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val count: Int,
  ) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
  }

  private fun extractGridComponents24(grid: BooleanArray): List<GridComp> {
    val visited = BooleanArray(GRID_SIZE * GRID_SIZE)
    val q = IntArray(GRID_SIZE * GRID_SIZE)
    val out = mutableListOf<GridComp>()
    for (y in 0 until GRID_SIZE) {
      for (x in 0 until GRID_SIZE) {
        val idx = y * GRID_SIZE + x
        if (!grid[idx] || visited[idx]) continue
        var head = 0
        var tail = 0
        q[tail++] = idx
        visited[idx] = true
        var minX = x
        var maxX = x
        var minY = y
        var maxY = y
        var count = 0
        while (head < tail) {
          val cur = q[head++]
          val cx = cur % GRID_SIZE
          val cy = cur / GRID_SIZE
          count++
          if (cx < minX) minX = cx
          if (cx > maxX) maxX = cx
          if (cy < minY) minY = cy
          if (cy > maxY) maxY = cy
          for (dy in -1..1) {
            for (dx in -1..1) {
              if (dx == 0 && dy == 0) continue
              val nx = cx + dx
              val ny = cy + dy
              if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                val nIdx = ny * GRID_SIZE + nx
                if (grid[nIdx] && !visited[nIdx]) {
                  visited[nIdx] = true
                  q[tail++] = nIdx
                }
              }
            }
          }
        }
        out.add(GridComp(minX, minY, maxX, maxY, count))
      }
    }
    return out.sortedBy { it.left }
  }

  private fun summarizeSubRegion(grid: BooleanArray, xStart: Int, xEnd: Int): GridComp? {
    var minX = GRID_SIZE
    var maxX = -1
    var minY = GRID_SIZE
    var maxY = -1
    var count = 0
    for (y in 0 until GRID_SIZE) {
      for (x in xStart..xEnd) {
        if (grid[y * GRID_SIZE + x]) {
          count++
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }
    return if (count >= 6) GridComp(minX, minY, maxX, maxY, count) else null
  }

  private fun subBoxSymmetryY(grid: BooleanArray, c: GridComp): Float {
    var match = 0
    var total = 0
    for (y in c.top..c.bottom) {
      val my = c.top + c.bottom - y
      for (x in c.left..c.right) {
        total++
        if (grid[y * GRID_SIZE + x] == grid[my * GRID_SIZE + x]) match++
      }
    }
    return if (total == 0) 0f else match.toFloat() / total.toFloat()
  }

  private fun subBoxSymmetryX(grid: BooleanArray, c: GridComp): Float {
    var match = 0
    var total = 0
    for (y in c.top..c.bottom) {
      for (x in c.left..c.right) {
        val mx = c.left + c.right - x
        total++
        if (grid[y * GRID_SIZE + x] == grid[y * GRID_SIZE + mx]) match++
      }
    }
    return if (total == 0) 0f else match.toFloat() / total.toFloat()
  }
}
