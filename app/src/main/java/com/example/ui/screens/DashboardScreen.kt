package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AdDecisionEntity
import com.example.model.CandidateClassification
import com.example.model.DecisionType
import com.example.model.EngineSnapshot
import com.example.model.OperationMode
import com.example.model.PermissionStatus
import com.example.model.SafetyConfig
import com.example.ui.components.ConfidenceBar
import com.example.ui.components.HeroControlCard
import com.example.ui.components.ModeSelectorCard
import com.example.ui.components.PermissionSetupBanner
import com.example.ui.components.PrivacyGuaranteePill
import com.example.ui.components.SessionMetricsRow
import com.example.ui.components.SubsystemIndicatorsCard
import com.example.ui.theme.AmberObserve
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
  snapshot: EngineSnapshot,
  config: SafetyConfig,
  permissionStatus: PermissionStatus,
  overlayVisible: Boolean,
  decisions: List<AdDecisionEntity>,
  onToggleStartStop: () -> Unit,
  onSelectMode: (OperationMode) -> Unit,
  onOpenAccessibility: () -> Unit,
  onOpenOverlay: () -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestScreenCapture: () -> Unit,
  onNavigateToHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isTabletWide = maxWidth >= 720.dp

    if (isTabletWide) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        LazyColumn(
          modifier = Modifier.weight(1.05f),
          contentPadding = PaddingValues(vertical = 20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            HeroControlCard(
              snapshot = snapshot,
              permissionsReady = permissionStatus.screenCaptureActive,
              onToggleStartStop = onToggleStartStop,
              onRequestMediaProjection = onRequestScreenCapture,
            )
          }
          item {
            ModeSelectorCard(
              config = config,
              onSelectMode = onSelectMode,
            )
          }
          item {
            LiveCandidatesCard(snapshot = snapshot)
          }
          item {
            PrivacyGuaranteePill()
          }
        }

        LazyColumn(
          modifier = Modifier.weight(0.95f),
          contentPadding = PaddingValues(vertical = 20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item {
            PermissionSetupBanner(
              status = permissionStatus,
              onOpenAccessibility = onOpenAccessibility,
              onOpenOverlay = onOpenOverlay,
              onRequestNotifications = onRequestNotifications,
              onOpenBatterySettings = onOpenBatterySettings,
              onRequestScreenCapture = onRequestScreenCapture,
            )
          }
          item {
            SubsystemIndicatorsCard(
              permissionStatus = permissionStatus,
              overlayVisible = overlayVisible,
              snapshot = snapshot,
              config = config,
              onOpenAccessibility = onOpenAccessibility,
              onRequestCapture = onRequestScreenCapture,
              onOpenOverlay = onOpenOverlay,
            )
          }
          item {
            SessionMetricsRow(
              snapshot = snapshot,
              totalLogged = decisions.size,
            )
          }
          item {
            RecentDecisionsPreviewCard(
              decisions = decisions.take(4),
              onSeeAll = onNavigateToHistory,
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          HeroControlCard(
            snapshot = snapshot,
            permissionsReady = permissionStatus.screenCaptureActive,
            onToggleStartStop = onToggleStartStop,
            onRequestMediaProjection = onRequestScreenCapture,
          )
        }
        item {
          PermissionSetupBanner(
            status = permissionStatus,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlay = onOpenOverlay,
            onRequestNotifications = onRequestNotifications,
            onOpenBatterySettings = onOpenBatterySettings,
            onRequestScreenCapture = onRequestScreenCapture,
          )
        }
        item {
          SubsystemIndicatorsCard(
            permissionStatus = permissionStatus,
            overlayVisible = overlayVisible,
            snapshot = snapshot,
            config = config,
            onOpenAccessibility = onOpenAccessibility,
            onRequestCapture = onRequestScreenCapture,
            onOpenOverlay = onOpenOverlay,
          )
        }
        item {
          ModeSelectorCard(
            config = config,
            onSelectMode = onSelectMode,
          )
        }
        item {
          SessionMetricsRow(
            snapshot = snapshot,
            totalLogged = decisions.size,
          )
        }
        item {
          LiveCandidatesCard(snapshot = snapshot)
        }
        item {
          RecentDecisionsPreviewCard(
            decisions = decisions.take(4),
            onSeeAll = onNavigateToHistory,
          )
        }
        item {
          PrivacyGuaranteePill()
        }
      }
    }
  }
}

@Composable
private fun LiveCandidatesCard(snapshot: EngineSnapshot) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Candidatos em Tempo Real",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text =
            if (snapshot.lastGamePackage.isNotBlank()) {
              snapshot.lastGamePackage.substringAfterLast('.')
            } else {
              "Nenhum jogo detectado"
            },
          style = MaterialTheme.typography.labelSmall,
          color = CyanElectric,
        )
      }

      if (snapshot.latestCandidates.isEmpty()) {
        Text(
          text =
            if (snapshot.isRunning) {
              "Nenhum botão de anúncio ativo na tela no momento. Abra um jogo para acompanhar a análise multi-sinal."
            } else {
              "Inicie o PT Pilot e abra um jogo. Os botões encontrados aparecerão aqui com pontuação detalhada."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        snapshot.latestCandidates.take(4).forEach { scored ->
          val accent =
            when (scored.classification) {
              CandidateClassification.SAFE_CLOSE -> EmeraldSafe
              CandidateClassification.TRAP_AD_LINK -> CoralAlert
              CandidateClassification.COUNTDOWN_ACTIVE -> AmberObserve
              CandidateClassification.LOW_CONFIDENCE -> CyanElectric
            }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = SlateSurfaceElevated,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = scored.candidate.text.ifBlank { scored.regionBucket.labelPt },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "${scored.classification.labelPt} • ${scored.candidate.source.labelPt}",
                  style = MaterialTheme.typography.labelSmall,
                  color = accent,
                )
              }
              ConfidenceBar(score = scored.confidenceScore, color = accent)
              Text(
                text = scored.primaryReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RecentDecisionsPreviewCard(
  decisions: List<AdDecisionEntity>,
  onSeeAll: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Histórico Recente",
          style = MaterialTheme.typography.titleLarge,
        )
        TextButton(
          onClick = onSeeAll,
          modifier = Modifier.testTag("see_all_history_button"),
        ) {
          Text("Ver tudo")
        }
      }

      if (decisions.isEmpty()) {
        Text(
          text = "Ainda não há anúncios registrados. Use o Modo Observação para validar os primeiros botões.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))
        decisions.forEach { item ->
          val type = runCatching { DecisionType.valueOf(item.decisionType) }.getOrNull()
          val color =
            when (type) {
              DecisionType.CLICKED_CLOSE -> EmeraldSafe
              DecisionType.BLOCKED_AD_TRAP, DecisionType.REDIRECT_RECOVERED -> CoralAlert
              DecisionType.OBSERVED_CANDIDATE -> AmberObserve
              else -> CyanElectric
            }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = SlateSurfaceElevated,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${item.detectedText} • ${(item.confidenceScore * 100).toInt()}%",
                  style = MaterialTheme.typography.titleMedium,
                  color = color,
                )
                Text(
                  text = item.reason,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = formatter.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
