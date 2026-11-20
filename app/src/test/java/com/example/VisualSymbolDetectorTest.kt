package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.engine.VisualSymbolDetector
import com.example.model.RectBounds
import com.example.model.UiCandidate
import com.example.model.CandidateSource
import com.example.model.VisualSymbolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualSymbolDetectorTest {

  private val detector = VisualSymbolDetector()

  @Test
  fun `detects arrow inside circle across left and right sides in portrait and landscape`() {
    // Portrait capture bitmap (600x960 -> scale 2x => 1200x1920)
    val portrait = Bitmap.createBitmap(600, 960, Bitmap.Config.ARGB_8888)
    val canvasP = Canvas(portrait)
    canvasP.drawColor(Color.rgb(20, 26, 40))

    // Draw arrow-in-circle on LEFT side mid-upper
    drawArrowInCircle(canvasP, cx = 48f, cy = 320f, radius = 26f)
    // Draw arrow-in-circle on RIGHT side top
    drawArrowInCircle(canvasP, cx = 548f, cy = 72f, radius = 26f)

    val foundPortrait =
      detector.detectGraphicalAdvanceCandidates(
        bitmap = portrait,
        scaleToScreenX = 2f,
        scaleToScreenY = 2f,
        foregroundPackage = "com.game.ads",
      )

    val leftHits = foundPortrait.filter { it.bounds.centerX < 400 && it.visualSymbol == VisualSymbolType.ARROW_IN_CIRCLE }
    val rightHits = foundPortrait.filter { it.bounds.centerX > 800 && it.visualSymbol == VisualSymbolType.ARROW_IN_CIRCLE }
    assertTrue("Expected arrow-in-circle on left side, got: $foundPortrait", leftHits.isNotEmpty())
    assertTrue("Expected arrow-in-circle on right side, got: $foundPortrait", rightHits.isNotEmpty())
    portrait.recycle()

    // Landscape tablet capture bitmap (1000x600 -> scale 2x => 2000x1200)
    val landscape = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888)
    val canvasL = Canvas(landscape)
    canvasL.drawColor(Color.rgb(25, 25, 35))
    drawArrowInCircle(canvasL, cx = 920f, cy = 300f, radius = 28f)

    val foundLandscape =
      detector.detectGraphicalAdvanceCandidates(
        bitmap = landscape,
        scaleToScreenX = 2f,
        scaleToScreenY = 2f,
        foregroundPackage = "com.game.ads",
      )
    assertTrue(
      "Expected arrow-in-circle in landscape mid-right, got: $foundLandscape",
      foundLandscape.any { it.visualSymbol == VisualSymbolType.ARROW_IN_CIRCLE && it.bounds.centerX > 1500 },
    )
    landscape.recycle()
  }

  @Test
  fun `detects right triangle, right arrow, chevron, and dash-circle symbols`() {
    // 1. Right-pointing triangle
    val bmpTri = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    Canvas(bmpTri).apply {
      drawColor(Color.BLACK)
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
      val path = Path().apply {
        moveTo(32f, 26f)
        lineTo(94f, 60f)
        lineTo(32f, 94f)
        close()
      }
      drawPath(path, p)
    }
    val resTri = detector.classifyBitmapRegion(bmpTri, 0, 0, 120, 120)
    assertEquals(VisualSymbolType.RIGHT_TRIANGLE, resTri.symbolType)
    assertTrue(resTri.confidence >= 0.70f)
    bmpTri.recycle()

    // 2. Simple right arrow
    val bmpArrow = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    Canvas(bmpArrow).apply {
      drawColor(Color.BLACK)
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 9f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
      }
      drawLine(24f, 60f, 94f, 60f, p)
      drawLine(68f, 34f, 94f, 60f, p)
      drawLine(68f, 86f, 94f, 60f, p)
    }
    val resArrow = detector.classifyBitmapRegion(bmpArrow, 0, 0, 120, 120)
    assertTrue(
      "Expected RIGHT_ARROW or CHEVRON_ADVANCE, got ${resArrow.symbolType}",
      resArrow.symbolType == VisualSymbolType.RIGHT_ARROW || resArrow.symbolType == VisualSymbolType.CHEVRON_ADVANCE,
    )
    assertTrue(resArrow.confidence >= 0.64f)
    bmpArrow.recycle()

    // 3. Dash + circle symbol (─ ○)
    val bmpDashCircle = Bitmap.createBitmap(140, 100, Bitmap.Config.ARGB_8888)
    Canvas(bmpDashCircle).apply {
      drawColor(Color.rgb(18, 22, 32))
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.FILL
      }
      // Horizontal dash on left
      drawRect(22f, 46f, 62f, 54f, p)
      // Circle on right
      drawCircle(96f, 50f, 18f, p)
    }
    val resDashCircle = detector.classifyBitmapRegion(bmpDashCircle, 0, 0, 140, 100)
    assertEquals(VisualSymbolType.DASH_CIRCLE, resDashCircle.symbolType)
    assertTrue(resDashCircle.confidence >= 0.64f)
    bmpDashCircle.recycle()

    // 4. Right chevron (>)
    val bmpChevron = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    Canvas(bmpChevron).apply {
      drawColor(Color.BLACK)
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
      }
      drawLine(36f, 28f, 84f, 60f, p)
      drawLine(36f, 92f, 84f, 60f, p)
    }
    val resChevron = detector.classifyBitmapRegion(bmpChevron, 0, 0, 120, 120)
    assertEquals(VisualSymbolType.CHEVRON_ADVANCE, resChevron.symbolType)
    assertTrue(resChevron.confidence >= 0.64f)
    bmpChevron.recycle()
  }

  @Test
  fun `does not confuse X close glyph or left-back arrow with advance symbol`() {
    // 1. X glyph
    val bmpX = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    Canvas(bmpX).apply {
      drawColor(Color.BLACK)
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 10f }
      drawLine(25f, 25f, 75f, 75f, p)
      drawLine(75f, 25f, 25f, 75f, p)
    }
    val resX = detector.classifyBitmapRegion(bmpX, 0, 0, 100, 100)
    assertEquals(VisualSymbolType.NONE, resX.symbolType)
    bmpX.recycle()

    // 2. Left-pointing back arrow (←)
    val bmpLeft = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    Canvas(bmpLeft).apply {
      drawColor(Color.BLACK)
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 9f; style = Paint.Style.STROKE }
      drawLine(26f, 60f, 96f, 60f, p)
      drawLine(52f, 34f, 26f, 60f, p)
      drawLine(52f, 86f, 26f, 60f, p)
    }
    val resLeft = detector.classifyBitmapRegion(bmpLeft, 0, 0, 120, 120)
    assertNotEquals(VisualSymbolType.RIGHT_ARROW, resLeft.symbolType)
    assertNotEquals(VisualSymbolType.RIGHT_TRIANGLE, resLeft.symbolType)
    bmpLeft.recycle()
  }

  @Test
  fun `enriches textless clickable accessibility ImageView using bitmap region`() {
    val bmp = Bitmap.createBitmap(600, 960, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(15, 18, 28))
    drawArrowInCircle(c, cx = 540f, cy = 840f, radius = 28f)

    val accNode =
      UiCandidate(
        id = "acc_0",
        text = "",
        contentDescription = "",
        viewIdResourceName = "",
        className = "android.widget.ImageButton",
        packageName = "com.game.ads",
        bounds = RectBounds(1010, 1610, 1150, 1750),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
        source = CandidateSource.ACCESSIBILITY,
      )

    val enriched =
      detector.inspectAccessibilityCandidates(
        bitmap = bmp,
        scaleToScreenX = 2f,
        scaleToScreenY = 2f,
        accessibilityCandidates = listOf(accNode),
      )

    assertEquals(VisualSymbolType.ARROW_IN_CIRCLE, enriched.first().visualSymbol)
    assertTrue(enriched.first().visualSymbolConfidence >= 0.64f)
    bmp.recycle()
  }

  private fun drawArrowInCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(12, 16, 26)
      style = Paint.Style.FILL
    }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = 4f
    }
    val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(240, 244, 255)
      style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, circleFill)
    canvas.drawCircle(cx, cy, radius, ringPaint)

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(16, 20, 32)
      style = Paint.Style.STROKE
      strokeWidth = 4.5f
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    val half = radius * 0.50f
    canvas.drawLine(cx - half, cy, cx + half, cy, arrowPaint)
    canvas.drawLine(cx + half * 0.12f, cy - half * 0.72f, cx + half, cy, arrowPaint)
    canvas.drawLine(cx + half * 0.12f, cy + half * 0.72f, cx + half, cy, arrowPaint)
    // Keep bgPaint referenced cleanly
    bgPaint.alpha = 255
  }
}
