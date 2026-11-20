package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.AutoPilotApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

  companion object {
    const val ACTION_START_OVERLAY_ONLY = "com.example.action.START_OVERLAY_ONLY"
    const val ACTION_START_PROJECTION = "com.example.action.START_PROJECTION"
    const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    private const val CHANNEL_ID = "pt_autopilot_channel"
    private const val NOTIFICATION_ID = 1001
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val mainHandler = Handler(Looper.getMainLooper())

  private var overlayManager: OverlayManager? = null
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var captureJob: Job? = null
  private var stateObserveJob: Job? = null

  private var fullWidth = 1200
  private var fullHeight = 1920
  private var captureWidth = 600
  private var captureHeight = 960
  private var screenDensityDpi = 240

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        mainHandler.post {
          stopProjectionResources()
        }
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
          fullWidth = width
          fullHeight = height
          (application as AutoPilotApplication).engine.updateScreenDimensions(width, height)
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    val app = application as AutoPilotApplication
    overlayManager = OverlayManager(this, app.engine)
    observeEngineForOverlay(app)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action

    if (action == ACTION_STOP_SERVICE) {
      val app = application as AutoPilotApplication
      app.engine.stopAutomation()
      stopProjectionResources()
      overlayManager?.hide()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    if (action == ACTION_START_PROJECTION) {
      val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
      val resultData =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

      promoteToForeground(includeMediaProjection = true)
      overlayManager?.showIfPermitted()

      if (resultCode == Activity.RESULT_OK && resultData != null) {
        startMediaProjection(resultCode, resultData)
      }
      return START_STICKY
    }

    promoteToForeground(includeMediaProjection = false)
    overlayManager?.showIfPermitted()
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    stopProjectionResources()
    overlayManager?.hide()
    stateObserveJob?.cancel()
    serviceScope.cancel()
  }

  private fun promoteToForeground(includeMediaProjection: Boolean) {
    val notification = buildNotification(includeMediaProjection)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val type =
        if (includeMediaProjection) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
      startForeground(NOTIFICATION_ID, notification, type)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && includeMediaProjection) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun observeEngineForOverlay(app: AutoPilotApplication) {
    stateObserveJob?.cancel()
    stateObserveJob =
      serviceScope.launch {
        launch {
          app.engine.snapshot.collect { snap ->
            mainHandler.post {
              overlayManager?.updateSnapshot(snap)
            }
          }
        }
        launch {
          app.engine.overlayHighlights.collect { highlights ->
            mainHandler.post {
              overlayManager?.updateHighlights(highlights)
            }
          }
        }
      }
  }

  private fun startMediaProjection(resultCode: Int, data: Intent) {
    stopProjectionResources()
    refreshDisplayMetrics()

    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
      ?: return
    val projection = mpManager.getMediaProjection(resultCode, data) ?: return

    mediaProjection = projection
    // Mandatory on Android 14+: register callback BEFORE createVirtualDisplay()
    projection.registerCallback(projectionCallback, mainHandler)

    val reader =
      ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
    imageReader = reader

    virtualDisplay =
      projection.createVirtualDisplay(
        "PTAutoPilotScreenCapture",
        captureWidth,
        captureHeight,
        screenDensityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        mainHandler,
      )

    val app = application as AutoPilotApplication
    app.engine.setScreenCaptureActive(true)

    captureJob =
      serviceScope.launch {
        while (isActive) {
          delay(1150L)
          val snap = app.engine.snapshot.value
          if (snap.isRunning && !snap.isPaused && app.engine.config.value.ocrEnabled) {
            captureAndProcessLatestFrame(app)
          }
        }
      }
  }

  private suspend fun captureAndProcessLatestFrame(app: AutoPilotApplication) {
    val reader = imageReader ?: return
    val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
    var bitmap: Bitmap? = null
    try {
      val planes = image.planes
      if (planes.isEmpty()) return
      val buffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val rowPadding = rowStride - pixelStride * captureWidth
      val paddedWidth = captureWidth + rowPadding / pixelStride

      val rawBitmap = Bitmap.createBitmap(paddedWidth, captureHeight, Bitmap.Config.ARGB_8888)
      rawBitmap.copyPixelsFromBuffer(buffer)
      bitmap =
        if (paddedWidth != captureWidth) {
          Bitmap.createBitmap(rawBitmap, 0, 0, captureWidth, captureHeight).also {
            rawBitmap.recycle()
          }
        } else {
          rawBitmap
        }

      val scaleX = fullWidth.toFloat() / captureWidth.toFloat()
      val scaleY = fullHeight.toFloat() / captureHeight.toFloat()
      app.engine.onScreenBitmapCaptured(bitmap, scaleX, scaleY)
    } finally {
      runCatching { bitmap?.recycle() }
      runCatching { image.close() }
    }
  }

  private fun stopProjectionResources() {
    captureJob?.cancel()
    captureJob = null
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching {
      mediaProjection?.unregisterCallback(projectionCallback)
      mediaProjection?.stop()
    }
    mediaProjection = null
    (application as? AutoPilotApplication)?.engine?.setScreenCaptureActive(false)
  }

  private fun refreshDisplayMetrics() {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      fullWidth = bounds.width().coerceAtLeast(480)
      fullHeight = bounds.height().coerceAtLeast(480)
      screenDensityDpi = resources.configuration.densityDpi
    } else {
      val dm = DisplayMetrics()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealMetrics(dm)
      fullWidth = dm.widthPixels.coerceAtLeast(480)
      fullHeight = dm.heightPixels.coerceAtLeast(480)
      screenDensityDpi = dm.densityDpi
    }
    // Scale down to 50% for fast, battery-friendly local OCR on Samsung SM-X210
    captureWidth = (fullWidth / 2).coerceAtLeast(320)
    captureHeight = (fullHeight / 2).coerceAtLeast(320)
    (application as AutoPilotApplication).engine.updateScreenDimensions(fullWidth, fullHeight)
  }

  private fun buildNotification(projectionActive: Boolean): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP_SERVICE },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val contentText =
      if (projectionActive) {
        "Captura local (OCR) e bolha flutuante ativas sobre jogos."
      } else {
        "Bolha flutuante e proteção ativa. Ative captura para OCR visual."
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("PT Pilot em execução")
      .setContentText(contentText)
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(0, "Parar Serviço", stopIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notification_channel_desc)
        }
      val nm = getSystemService(NotificationManager::class.java)
      nm?.createNotificationChannel(channel)
    }
  }
}
