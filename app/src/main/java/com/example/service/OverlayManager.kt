package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.engine.AutoPilotEngine
import com.example.model.AutomationPhase
import com.example.model.CandidateClassification
import com.example.model.EngineSnapshot
import com.example.model.OperationMode
import com.example.model.ScoredCandidate
import com.example.model.VisualSymbolType
import kotlin.math.abs

class OverlayManager(
  private val context: Context,
  private val engine: AutoPilotEngine,
) {
  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

  private var bubbleRoot: LinearLayout? = null
  private var bubbleParams: WindowManager.LayoutParams? = null
  private var statusPillView: TextView? = null
  private var controlsPanel: LinearLayout? = null
  private var pauseButtonView: TextView? = null
  private var modeButtonView: TextView? = null

  private var highlightView: HighlightOverlayView? = null
  private var isExpanded = false

  private val overlayWindowType: Int
    get() =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
      }

  fun showIfPermitted() {
    if (!Settings.canDrawOverlays(context)) {
      engine.setOverlayVisible(false)
      return
    }
    if (highlightView == null) {
      attachHighlightOverlay()
    }
    if (bubbleRoot == null) {
      attachBubbleOverlay()
    }
    engine.setOverlayVisible(true)
  }

  fun hide() {
    runCatching { bubbleRoot?.let { windowManager.removeView(it) } }
    runCatching { highlightView?.let { windowManager.removeView(it) } }
    bubbleRoot = null
    highlightView = null
    engine.setOverlayVisible(false)
  }

  fun updateSnapshot(snapshot: EngineSnapshot) {
    val pill = statusPillView ?: return
    val statusDotColor =
      when {
        !snapshot.isRunning -> "#6B7BA6"
        snapshot.isPaused -> "#FFC107"
        snapshot.phase == AutomationPhase.RETURNING_TO_GAME -> "#FF4D6D"
        snapshot.phase.stepIndex in 1..7 -> "#8B5CF6"
        else -> "#65FF00"
      }
    val modeShort = if (snapshot.mode == OperationMode.OBSERVATION) "OBS" else "AUTO"
    val stateText =
      when {
        !snapshot.isRunning -> "PT Pilot • Parado"
        snapshot.isPaused -> "PT Pilot • Pausado"
        snapshot.phase == AutomationPhase.MONITORING_GAME -> "PT Pilot [$modeShort]"
        else -> "PT Pilot • ${snapshot.phase.labelPt.take(16)}"
      }

    pill.text = stateText
    pill.setTextColor(Color.WHITE)
    val bg =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(Color.parseColor("#EB070B1E"))
        setStroke(dp(1.5f), Color.parseColor(statusDotColor))
      }
    pill.background = bg

    pauseButtonView?.text =
      when {
        !snapshot.isRunning -> "Iniciar"
        snapshot.isPaused -> "Retomar"
        else -> "Pausar"
      }
    modeButtonView?.text =
      if (snapshot.mode == OperationMode.OBSERVATION) {
        "Mudar p/ Automático"
      } else {
        "Mudar p/ Observação"
      }
  }

  fun updateHighlights(candidates: List<ScoredCandidate>) {
    highlightView?.updateCandidates(candidates)
  }

  private fun attachHighlightOverlay() {
    val view = HighlightOverlayView(context)
    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
      )
    runCatching {
      windowManager.addView(view, params)
      highlightView = view
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachBubbleOverlay() {
    val container =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
      }

    val pill =
      TextView(context).apply {
        text = "PT Pilot • Pronto"
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background =
          GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor("#EB070B1E"))
            setStroke(dp(1.5f), Color.parseColor("#00C2FF"))
          }
        elevation = dp(6).toFloat()
      }

    val panel =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background =
          GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#F50E1530"))
            setStroke(dp(1.2f), Color.parseColor("#8B5CF6"))
          }
      }

    val btnStartPause = createActionChip("Iniciar / Pausar", "#65FF00") {
      engine.togglePause()
    }
    val btnSwitchMode = createActionChip("Alternar Obs/Auto", "#00C2FF") {
      engine.toggleOperationMode()
    }
    val btnStop = createActionChip("Interromper Agora", "#FF4D6D") {
      engine.stopAutomation()
      isExpanded = false
      panel.visibility = View.GONE
    }
    val btnOpenApp = createActionChip("Abrir Painel", "#94A3B8") {
      val launch =
        Intent(context, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      context.startActivity(launch)
      isExpanded = false
      panel.visibility = View.GONE
    }

    pauseButtonView = btnStartPause
    modeButtonView = btnSwitchMode

    panel.addView(btnStartPause)
    panel.addView(btnSwitchMode)
    panel.addView(btnStop)
    panel.addView(btnOpenApp)

    val panelLp =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = dp(6) }

    container.addView(pill)
    container.addView(panel, panelLp)

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(24)
        y = dp(140)
      }

    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var moved = false

    pill.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          moved = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - initialTouchX).toInt()
          val dy = (event.rawY - initialTouchY).toInt()
          if (abs(dx) > 8 || abs(dy) > 8) {
            moved = true
            params.x = initialX + dx
            params.y = initialY + dy
            runCatching { windowManager.updateViewLayout(container, params) }
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!moved) {
            isExpanded = !isExpanded
            panel.visibility = if (isExpanded) View.VISIBLE else View.GONE
          }
          true
        }
        else -> false
      }
    }

    runCatching {
      windowManager.addView(container, params)
      bubbleRoot = container
      bubbleParams = params
      statusPillView = pill
      controlsPanel = panel
    }
  }

  private fun createActionChip(
    label: String,
    accentHex: String,
    onClick: () -> Unit,
  ): TextView {
    return TextView(context).apply {
      text = label
      textSize = 12f
      typeface = Typeface.DEFAULT_BOLD
      setTextColor(Color.parseColor(accentHex))
      setPadding(dp(12), dp(8), dp(12), dp(8))
      minHeight = dp(40)
      gravity = Gravity.CENTER_VERTICAL
      background =
        GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(10).toFloat()
          setColor(Color.parseColor("#182232"))
        }
      layoutParams =
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(6) }
      setOnClickListener { onClick() }
    }
  }

  private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()
  private fun dp(value: Int): Int = dp(value.toFloat())

  private class HighlightOverlayView(context: Context) : View(context) {
    private var items: List<ScoredCandidate> = emptyList()

    private val boxPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
      }
    private val labelBgPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D9090D14")
      }
    private val labelTextPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
      }

    fun updateCandidates(newItems: List<ScoredCandidate>) {
      items = newItems
      postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      for (item in items) {
        val colorHex =
          when (item.classification) {
            CandidateClassification.SAFE_CLOSE -> "#65FF00"
            CandidateClassification.TRAP_AD_LINK -> "#FF4D6D"
            CandidateClassification.COUNTDOWN_ACTIVE -> "#FFC107"
            CandidateClassification.LOW_CONFIDENCE -> "#8B5CF6"
          }
        boxPaint.color = Color.parseColor(colorHex)
        val b = item.candidate.bounds
        val rect = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        canvas.drawRoundRect(rect, 14f, 14f, boxPaint)

        val rawTitle =
          item.candidate.text.ifBlank {
            if (item.candidate.visualSymbol != VisualSymbolType.NONE) {
              item.candidate.visualSymbol.labelPt
            } else {
              "Botão"
            }
          }
        val label =
          "${rawTitle.take(16)} ${(item.confidenceScore * 100).toInt()}%"
        val textWidth = labelTextPaint.measureText(label)
        val tagTop = (b.top - 42f).coerceAtLeast(8f)
        val tagRect = RectF(b.left.toFloat(), tagTop, b.left + textWidth + 20f, tagTop + 36f)
        canvas.drawRoundRect(tagRect, 8f, 8f, labelBgPaint)
        labelTextPaint.color = Color.parseColor(colorHex)
        canvas.drawText(label, tagRect.left + 10f, tagRect.bottom - 9f, labelTextPaint)
      }
    }
  }
}
