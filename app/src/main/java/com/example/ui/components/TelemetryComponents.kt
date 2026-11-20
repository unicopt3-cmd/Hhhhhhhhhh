package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AutomationPhase
import com.example.model.EngineSnapshot
import com.example.model.OperationMode
import com.example.model.PermissionStatus
import com.example.model.SafetyConfig
import com.example.ui.theme.AmberObserve
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated

@Composable
fun PTPilotBrandHeader(
  sectionTitle: String,
  sectionSubtitle: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = SlateSurface,
    border =
      BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(CyanElectric, NeonPurple, EmeraldSafe)),
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_pt_pilot_emblem),
        contentDescription = "Identidade visual oficial PT Pilot",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, CyanElectric.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text =
            buildAnnotatedString {
              withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append("PT ")
              }
              withStyle(SpanStyle(color = EmeraldSafe, fontWeight = FontWeight.Bold)) {
                append("Pilot")
              }
              withStyle(SpanStyle(color = CyanElectric, fontWeight = FontWeight.Medium)) {
                append(" • $sectionTitle")
              }
            },
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = sectionSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun HeroControlCard(
  snapshot: EngineSnapshot,
  permissionsReady: Boolean,
  onToggleStartStop: () -> Unit,
  onRequestMediaProjection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val statusColor by
    animateColorAsState(
      targetValue =
        when {
          !snapshot.isRunning -> NeonPurple
          snapshot.isPaused -> AmberObserve
          snapshot.mode == OperationMode.OBSERVATION -> CyanElectric
          else -> EmeraldSafe
        },
      label = "heroStatusColor",
    )

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    border =
      BorderStroke(
        width = 1.5.dp,
        brush =
          Brush.linearGradient(
            colors = listOf(CyanElectric, NeonPurple, EmeraldSafe)
          ),
      ),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Official PT Pilot Brand Showcase Row (3D Character + Robotic Mascot + Neon Badge)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.width(126.dp)
              .height(178.dp)
              .clip(RoundedCornerShape(18.dp))
              .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(listOf(CyanElectric, NeonPurple, EmeraldSafe)),
                shape = RoundedCornerShape(18.dp),
              )
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_pt_pilot_official),
            contentDescription = "Personagem oficial 3D e mascote robótico PT Pilot",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                buildAnnotatedString {
                  withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append("PT ")
                  }
                  withStyle(SpanStyle(color = EmeraldSafe, fontWeight = FontWeight.Bold)) {
                    append("Pilot")
                  }
                },
              style = MaterialTheme.typography.headlineLarge,
            )

            Surface(
              shape = RoundedCornerShape(50),
              color = statusColor.copy(alpha = 0.18f),
              border = BorderStroke(1.dp, statusColor),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(8.dp)
                      .clip(CircleShape)
                      .background(if (snapshot.isRunning) statusColor else Color(0xFF6B7BA6))
                )
                Text(
                  text =
                    when {
                      !snapshot.isRunning -> "PARADO"
                      snapshot.isPaused -> "PAUSADO"
                      snapshot.mode == OperationMode.OBSERVATION -> "OBS"
                      else -> "AUTO"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (snapshot.isRunning) statusColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Text(
            text = "Seu Assistente Inteligente para Fechar Anúncios!",
            style = MaterialTheme.typography.titleMedium,
            color = CyanElectric,
          )

          Text(
            text =
              if (snapshot.isRunning) {
                "${snapshot.mode.labelPt} • ${snapshot.phase.labelPt}"
              } else {
                "Rápido • Seguro • Prático para Samsung SM-X210"
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = SlateSurfaceElevated,
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
          ) {
            Text(
              text = snapshot.lastActionSummary,
              style = MaterialTheme.typography.bodyMedium,
              color = EmeraldSafe,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            )
          }
        }
      }

      // 8-Step State Machine Strip
      PipelineStepper(currentPhase = snapshot.phase, isRunning = snapshot.isRunning)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onToggleStartStop,
          modifier =
            Modifier.weight(1f)
              .height(56.dp)
              .minimumInteractiveComponentSize()
              .testTag("start_stop_button"),
          shape = RoundedCornerShape(16.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (snapshot.isRunning) CoralAlert else EmeraldSafe,
              contentColor = ObsidianBg,
            ),
        ) {
          Icon(
            imageVector = if (snapshot.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (snapshot.isRunning) "Parar serviço" else "Iniciar serviço",
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (snapshot.isRunning) "Parar PT Pilot" else "Iniciar PT Pilot",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        if (!permissionsReady) {
          OutlinedButton(
            onClick = onRequestMediaProjection,
            modifier =
              Modifier.height(56.dp)
                .minimumInteractiveComponentSize()
                .testTag("quick_capture_button"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, CyanElectric.copy(alpha = 0.75f)),
          ) {
            Icon(
              imageVector = Icons.Filled.ScreenshotMonitor,
              contentDescription = "Ativar Captura OCR",
              tint = CyanElectric,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Captura OCR", color = CyanElectric)
          }
        }
      }
    }
  }
}

@Composable
private fun PipelineStepper(currentPhase: AutomationPhase, isRunning: Boolean) {
  val stepLabels =
    listOf("1.Ad", "2.Play", "3.Skip", "4.Botão", "5.Seg.", "6.Ação", "7.Fim", "8.Jogo")

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      stepLabels.forEachIndexed { idx, label ->
        val stepNum = idx + 1
        val active = isRunning && currentPhase.stepIndex == stepNum
        val completed = isRunning && currentPhase.stepIndex > stepNum
        val barColor =
          when {
            active -> CyanElectric
            completed -> EmeraldSafe
            else -> SlateSurfaceElevated
          }
        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
          )
        }
      }
    }
  }
}

@Composable
fun SubsystemIndicatorsCard(
  permissionStatus: PermissionStatus,
  overlayVisible: Boolean,
  snapshot: EngineSnapshot,
  config: SafetyConfig,
  onOpenAccessibility: () -> Unit,
  onRequestCapture: () -> Unit,
  onOpenOverlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Indicadores do Sistema",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      IndicatorRow(
        title = "Acessibilidade",
        subtitle =
          if (permissionStatus.accessibilityEnabled) "Conectada — lendo janelas e gestos"
          else "Desativada — toque para habilitar",
        icon = Icons.Filled.AccessibilityNew,
        isActive = permissionStatus.accessibilityEnabled,
        badgeText = if (permissionStatus.accessibilityEnabled) "ATIVA" else "INATIVA",
        testTag = "indicator_accessibility",
        onClick = onOpenAccessibility,
      )

      IndicatorRow(
        title = "Captura de Tela (MediaProjection)",
        subtitle =
          if (permissionStatus.screenCaptureActive) "Ativa localmente — alimentando OCR"
          else "Aguardando autorização explícita",
        icon = Icons.Filled.ScreenshotMonitor,
        isActive = permissionStatus.screenCaptureActive,
        badgeText = if (permissionStatus.screenCaptureActive) "ATIVA" else "OPCIONAL/OCR",
        testTag = "indicator_screen_capture",
        onClick = onRequestCapture,
      )

      IndicatorRow(
        title = "Sobreposição (Bolha Flutuante)",
        subtitle =
          when {
            !permissionStatus.overlayEnabled -> "Permissão desativada — toque para liberar"
            overlayVisible -> "Bolha ativa sobre outros aplicativos"
            else -> "Liberada — será exibida ao iniciar"
          },
        icon = Icons.Filled.Layers,
        isActive = permissionStatus.overlayEnabled,
        badgeText =
          when {
            !permissionStatus.overlayEnabled -> "DESATIVADA"
            overlayVisible -> "VISÍVEL"
            else -> "PRONTA"
          },
        testTag = "indicator_overlay",
        onClick = onOpenOverlay,
      )

      IndicatorRow(
        title = "Reconhecimento Multi-Sinal",
        subtitle =
          if (config.ocrEnabled && permissionStatus.screenCaptureActive) {
            "Híbrido (Árvore + OCR ML Kit) • ${snapshot.ocrProcessingLatencyMs} ms"
          } else if (permissionStatus.accessibilityEnabled) {
            "Árvore de Acessibilidade ativa • 7 sinais"
          } else {
            "Aguardando serviço de acessibilidade"
          },
        icon = Icons.Filled.Psychology,
        isActive = permissionStatus.accessibilityEnabled || permissionStatus.screenCaptureActive,
        badgeText =
          if (snapshot.ocrActive) "HÍBRIDO" else if (permissionStatus.accessibilityEnabled) "ÁRVORE" else "OFF",
        testTag = "indicator_recognition",
        onClick = onRequestCapture,
      )
    }
  }
}

@Composable
private fun IndicatorRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  isActive: Boolean,
  badgeText: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val accent = if (isActive) EmeraldSafe else AmberObserve
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(SlateSurfaceElevated)
        .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(accent.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = accent,
        modifier = Modifier.size(22.dp),
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Surface(
      shape = RoundedCornerShape(8.dp),
      color = accent.copy(alpha = 0.16f),
    ) {
      Text(
        text = badgeText,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
  }
}

@Composable
fun ModeSelectorCard(
  config: SafetyConfig,
  onSelectMode: (OperationMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Modo de Operação",
          style = MaterialTheme.typography.titleLarge,
        )
        Surface(
          shape = RoundedCornerShape(50),
          color = CyanElectric.copy(alpha = 0.14f),
        ) {
          Text(
            text = "${config.observationValidatedCount} padrões validados",
            style = MaterialTheme.typography.labelSmall,
            color = CyanElectric,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ModeOptionTile(
          mode = OperationMode.OBSERVATION,
          selected = config.operationMode == OperationMode.OBSERVATION,
          icon = Icons.Filled.Visibility,
          accent = AmberObserve,
          testTag = "mode_observation_card",
          onClick = { onSelectMode(OperationMode.OBSERVATION) },
          modifier = Modifier.weight(1f),
        )
        ModeOptionTile(
          mode = OperationMode.AUTOMATIC,
          selected = config.operationMode == OperationMode.AUTOMATIC,
          icon = Icons.Filled.AutoAwesome,
          accent = EmeraldSafe,
          testTag = "mode_automatic_card",
          onClick = { onSelectMode(OperationMode.AUTOMATIC) },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ModeOptionTile(
  mode: OperationMode,
  selected: Boolean,
  icon: ImageVector,
  accent: Color,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    color = if (selected) accent.copy(alpha = 0.14f) else SlateSurfaceElevated,
    border = BorderStroke(1.5.dp, if (selected) accent else SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = mode.labelPt,
          tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = mode.labelPt,
          style = MaterialTheme.typography.titleMedium,
          color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
      }
      Text(
        text = mode.descriptionPt,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun PermissionSetupBanner(
  status: PermissionStatus,
  onOpenAccessibility: () -> Unit,
  onOpenOverlay: () -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestScreenCapture: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val allDone =
    status.accessibilityEnabled &&
      status.overlayEnabled &&
      status.notificationsGranted &&
      status.batteryOptimizationIgnored &&
      status.screenCaptureActive

  if (allDone) return

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1510)),
    border = BorderStroke(1.dp, AmberObserve.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = "Permissões pendentes",
          tint = AmberObserve,
        )
        Text(
          text = "Configuração de Autorizações",
          style = MaterialTheme.typography.titleMedium,
          color = AmberObserve,
        )
      }
      Text(
        text =
          "Conceda apenas os itens pendentes abaixo. Autorizações já válidas não serão solicitadas novamente.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (!status.accessibilityEnabled) {
        PermissionActionRow(
          label = "1. Habilitar Serviço de Acessibilidade",
          actionText = "Configurar",
          icon = Icons.Filled.AccessibilityNew,
          testTag = "grant_accessibility_button",
          onClick = onOpenAccessibility,
        )
      }
      if (!status.overlayEnabled) {
        PermissionActionRow(
          label = "2. Permitir Sobreposição (Bolha Flutuante)",
          actionText = "Liberar",
          icon = Icons.Filled.Layers,
          testTag = "grant_overlay_button",
          onClick = onOpenOverlay,
        )
      }
      if (!status.notificationsGranted) {
        PermissionActionRow(
          label = "3. Autorizar Notificações do Serviço",
          actionText = "Permitir",
          icon = Icons.Filled.NotificationsActive,
          testTag = "grant_notifications_button",
          onClick = onRequestNotifications,
        )
      }
      if (!status.screenCaptureActive) {
        PermissionActionRow(
          label = "4. Captura de Tela para OCR Local",
          actionText = "Autorizar",
          icon = Icons.Filled.ScreenshotMonitor,
          testTag = "grant_capture_button",
          onClick = onRequestScreenCapture,
        )
      }
      if (!status.batteryOptimizationIgnored) {
        PermissionActionRow(
          label = "5. Bateria Sem Restrição (Samsung SM-X210)",
          actionText = "Ajustar",
          icon = Icons.Filled.BatteryChargingFull,
          testTag = "grant_battery_button",
          onClick = onOpenBatterySettings,
        )
      }
    }
  }
}

@Composable
private fun PermissionActionRow(
  label: String,
  actionText: String,
  icon: ImageVector,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AmberObserve,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    FilledTonalButton(
      onClick = onClick,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    ) {
      Text(actionText)
    }
  }
}

@Composable
fun SessionMetricsRow(
  snapshot: EngineSnapshot,
  totalLogged: Int,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    MetricTile(
      label = "Cliques Seguros",
      value = snapshot.safeClicksSession.toString(),
      accent = EmeraldSafe,
      modifier = Modifier.weight(1f),
    )
    MetricTile(
      label = "Armadilhas Evitadas",
      value = snapshot.blockedTrapsSession.toString(),
      accent = CoralAlert,
      modifier = Modifier.weight(1f),
    )
    MetricTile(
      label = "Retornos Loja",
      value = snapshot.redirectRecoveriesSession.toString(),
      accent = CyanElectric,
      modifier = Modifier.weight(1f),
    )
    MetricTile(
      label = "Registros Locais",
      value = totalLogged.toString(),
      accent = AmberObserve,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun MetricTile(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = SlateSurface,
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
      )
    }
  }
}

@Composable
fun ConfidenceBar(score: Float, color: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LinearProgressIndicator(
      progress = { score.coerceIn(0f, 1f) },
      modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
      color = color,
      trackColor = SlateSurfaceElevated,
    )
    Text(
      text = "${(score * 100).toInt()}%",
      style = MaterialTheme.typography.labelMedium,
      color = color,
    )
  }
}

@Composable
fun PrivacyGuaranteePill(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = EmeraldSafe.copy(alpha = 0.08f),
    border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Security,
        contentDescription = "Privacidade local",
        tint = EmeraldSafe,
      )
      Text(
        text =
          "Processamento 100% local (sem envio de telas, sem alteração de DNS e sem bloqueio de anúncios).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
