package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.AutoPilotApplication
import com.example.engine.AccessibilityExecutor
import com.example.model.CandidateSource
import com.example.model.RectBounds
import com.example.model.UiCandidate

class AutoPilotAccessibilityService : AccessibilityService(), AccessibilityExecutor {

  private var nodeCounter = 0

  override fun onServiceConnected() {
    super.onServiceConnected()
    val app = application as AutoPilotApplication
    updateScreenMetrics(app)
    app.engine.attachAccessibilityExecutor(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val app = application as AutoPilotApplication
    updateScreenMetrics(app)

    val pkg = event.packageName?.toString().orEmpty()
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg.isNotBlank()) {
      app.engine.onWindowPackageChanged(pkg)
    }

    val root = rootInActiveWindow ?: return
    val rootPkg = root.packageName?.toString().orEmpty().ifBlank { pkg }
    val candidates = mutableListOf<UiCandidate>()
    nodeCounter = 0
    traverseNodeTree(root, rootPkg, depth = 0, out = candidates)
    app.engine.onAccessibilityHierarchyCaptured(rootPkg, candidates)
  }

  override fun onInterrupt() {
    val app = application as AutoPilotApplication
    app.engine.attachAccessibilityExecutor(null)
  }

  override fun onDestroy() {
    super.onDestroy()
    val app = application as AutoPilotApplication
    app.engine.attachAccessibilityExecutor(null)
  }

  override fun clickCandidate(candidate: UiCandidate): Boolean {
    // 1. Try direct AccessibilityNodeInfo click first if candidate came from Accessibility tree
    if (candidate.source != CandidateSource.OCR) {
      val root = rootInActiveWindow
      if (root != null) {
        val matchingNode = findNodeMatchingBounds(root, candidate.bounds)
        if (matchingNode != null && matchingNode.isClickable && matchingNode.isEnabled) {
          val performed = matchingNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
          if (performed) return true
        }
      }
    }

    // 2. Fallback or OCR-only target: dispatch precise gesture tap at center coordinates
    val cx = candidate.bounds.centerX.toFloat()
    val cy = candidate.bounds.centerY.toFloat()
    if (cx <= 0f || cy <= 0f) return false

    val path = Path().apply { moveTo(cx, cy) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, null, null)
  }

  override fun performBackAction(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_BACK)
  }

  private fun traverseNodeTree(
    node: AccessibilityNodeInfo,
    fallbackPkg: String,
    depth: Int,
    out: MutableList<UiCandidate>,
  ) {
    if (depth > 22 || out.size >= 140) return

    val rect = Rect()
    node.getBoundsInScreen(rect)
    val text = node.text?.toString()?.trim().orEmpty()
    val desc = node.contentDescription?.toString()?.trim().orEmpty()
    val resId = node.viewIdResourceName?.trim().orEmpty()
    val cls = node.className?.toString().orEmpty()
    val pkg = node.packageName?.toString().orEmpty().ifBlank { fallbackPkg }

    val hasSignal = text.isNotEmpty() || desc.isNotEmpty() || resId.isNotEmpty() || node.isClickable
    if (hasSignal && rect.width() > 6 && rect.height() > 6 && node.isVisibleToUser) {
      out.add(
        UiCandidate(
          id = "acc_${nodeCounter++}",
          text = text,
          contentDescription = desc,
          viewIdResourceName = resId,
          className = cls,
          packageName = pkg,
          bounds = RectBounds(rect.left, rect.top, rect.right, rect.bottom),
          isClickable = node.isClickable,
          isEnabled = node.isEnabled,
          isVisibleToUser = node.isVisibleToUser,
          source = CandidateSource.ACCESSIBILITY,
        )
      )
    }

    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      traverseNodeTree(child, pkg, depth + 1, out)
    }
  }

  private fun findNodeMatchingBounds(
    node: AccessibilityNodeInfo,
    target: RectBounds,
    depth: Int = 0,
  ): AccessibilityNodeInfo? {
    if (depth > 22) return null
    val rect = Rect()
    node.getBoundsInScreen(rect)
    if (
      kotlin.math.abs(rect.left - target.left) <= 12 &&
      kotlin.math.abs(rect.top - target.top) <= 12 &&
      kotlin.math.abs(rect.right - target.right) <= 12 &&
      kotlin.math.abs(rect.bottom - target.bottom) <= 12
    ) {
      return node
    }
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val found = findNodeMatchingBounds(child, target, depth + 1)
      if (found != null) return found
    }
    return null
  }

  private fun updateScreenMetrics(app: AutoPilotApplication) {
    val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      app.engine.updateScreenDimensions(bounds.width(), bounds.height())
    } else {
      val dm = DisplayMetrics()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealMetrics(dm)
      app.engine.updateScreenDimensions(dm.widthPixels, dm.heightPixels)
    }
  }
}
