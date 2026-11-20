package com.example.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.model.PermissionStatus

object PermissionHelper {

  fun getPermissionStatus(
    context: Context,
    isAccessibilityConnectedInEngine: Boolean,
    isScreenCaptureActive: Boolean,
  ): PermissionStatus {
    val accessibilityEnabled =
      isAccessibilityConnectedInEngine || isAccessibilityServiceEnabledInSettings(context)
    val overlayEnabled = Settings.canDrawOverlays(context)
    val notificationsGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryIgnored =
      powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    return PermissionStatus(
      accessibilityEnabled = accessibilityEnabled,
      overlayEnabled = overlayEnabled,
      screenCaptureActive = isScreenCaptureActive,
      notificationsGranted = notificationsGranted,
      batteryOptimizationIgnored = batteryIgnored,
    )
  }

  fun isAccessibilityServiceEnabledInSettings(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
      ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expectedPackage = context.packageName
    return enabledServices.any { info ->
      info.resolveInfo?.serviceInfo?.packageName == expectedPackage
    }
  }

  fun openAccessibilitySettings(context: Context) {
    val intent =
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(intent)
  }

  fun openOverlaySettings(context: Context) {
    val intent =
      Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(intent)
  }

  fun openBatteryOptimizationSettings(context: Context) {
    val directIntent =
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(directIntent) }
      .onFailure {
        val fallback =
          Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(fallback)
      }
  }
}
