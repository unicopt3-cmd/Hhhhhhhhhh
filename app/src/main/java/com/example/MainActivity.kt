package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.PermissionHelper
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryLearningScreen
import com.example.ui.screens.SafetySettingsScreen
import com.example.ui.screens.SetupGuideScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PTAutoPilotTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.viewmodel.MainViewModel

private data class NavTab(
  val route: AppRoute,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PTAutoPilotTheme {
        PTAutoPilotAppShell(viewModel = viewModel)
      }
    }
  }
}

@Composable
private fun PTAutoPilotAppShell(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val snapshot by viewModel.engineSnapshot.collectAsStateWithLifecycle()
  val config by viewModel.safetyConfig.collectAsStateWithLifecycle()
  val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
  val overlayVisible by viewModel.overlayVisible.collectAsStateWithLifecycle()
  val decisions by viewModel.decisions.collectAsStateWithLifecycle()
  val learnedPatterns by viewModel.learnedPatterns.collectAsStateWithLifecycle()

  // Refresh permissions whenever user returns from Settings
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      viewModel.refreshPermissions()
    }

  val mediaProjectionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        viewModel.startScreenCapture(result.resultCode, result.data!!)
      }
      viewModel.refreshPermissions()
    }

  val launchMediaProjectionPrompt: () -> Unit = {
    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val captureIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        mpManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
      } else {
        mpManager.createScreenCaptureIntent()
      }
    mediaProjectionLauncher.launch(captureIntent)
  }

  val requestNotificationsIfNeeded: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionStatus.notificationsGranted) {
      notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val tabs =
    listOf(
      NavTab(
        route = AppRoute.Dashboard,
        label = "Painel",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard",
      ),
      NavTab(
        route = AppRoute.HistoryLearning,
        label = "Aprendizagem",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history",
      ),
      NavTab(
        route = AppRoute.SafetySettings,
        label = "Segurança",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        testTag = "nav_safety",
      ),
      NavTab(
        route = AppRoute.SetupGuide,
        label = "SM-X210",
        selectedIcon = Icons.Filled.TabletAndroid,
        unselectedIcon = Icons.Outlined.TabletAndroid,
        testTag = "nav_guide",
      ),
    )

  val navigateToTab: (AppRoute) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = ObsidianBg,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar(containerColor = SlateSurface) {
            tabs.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(containerColor = SlateSurface) {
            tabs.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = AppRoute.Dashboard,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable<AppRoute.Dashboard> {
            DashboardScreen(
              snapshot = snapshot,
              config = config,
              permissionStatus = permissionStatus,
              overlayVisible = overlayVisible,
              decisions = decisions,
              onToggleStartStop = { viewModel.toggleStartStop() },
              onSelectMode = { viewModel.setOperationMode(it) },
              onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
              onOpenOverlay = { PermissionHelper.openOverlaySettings(context) },
              onRequestNotifications = requestNotificationsIfNeeded,
              onOpenBatterySettings = { PermissionHelper.openBatteryOptimizationSettings(context) },
              onRequestScreenCapture = launchMediaProjectionPrompt,
              onNavigateToHistory = { navigateToTab(AppRoute.HistoryLearning) },
            )
          }

          composable<AppRoute.HistoryLearning> {
            HistoryLearningScreen(
              decisions = decisions,
              learnedPatterns = learnedPatterns,
              onValidateDecision = { id, isTrueClose ->
                viewModel.validateDecision(id, isTrueClose)
              },
              onDeletePattern = { viewModel.deleteLearnedPattern(it) },
              onClearHistory = { viewModel.clearHistory() },
              onClearLibrary = { viewModel.clearLearnedLibrary() },
            )
          }

          composable<AppRoute.SafetySettings> {
            SafetySettingsScreen(
              config = config,
              onConfidenceThresholdChange = { viewModel.setConfidenceThreshold(it) },
              onMinAdWatchChange = { viewModel.setMinAdWatchSeconds(it) },
              onClickCooldownChange = { viewModel.setClickCooldownMs(it) },
              onRedirectProtectionChange = { viewModel.setRedirectProtectionEnabled(it) },
              onMaxRedirectRetriesChange = { viewModel.setMaxRedirectRetries(it) },
              onOcrEnabledChange = { viewModel.setOcrEnabled(it) },
            )
          }

          composable<AppRoute.SetupGuide> {
            SetupGuideScreen(
              permissionStatus = permissionStatus,
              onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
              onOpenOverlay = { PermissionHelper.openOverlaySettings(context) },
              onOpenBatterySettings = { PermissionHelper.openBatteryOptimizationSettings(context) },
              onRequestScreenCapture = launchMediaProjectionPrompt,
            )
          }
        }
      }
    }
  }
}
