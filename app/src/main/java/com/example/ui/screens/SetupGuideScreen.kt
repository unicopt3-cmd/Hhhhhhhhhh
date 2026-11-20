package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PermissionStatus
import com.example.ui.components.PTPilotBrandHeader
import com.example.ui.theme.AmberObserve
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated

@Composable
fun SetupGuideScreen(
  permissionStatus: PermissionStatus,
  onOpenAccessibility: () -> Unit,
  onOpenOverlay: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestScreenCapture: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PTPilotBrandHeader(
        sectionTitle = "Guia SM-X210",
        sectionSubtitle = "Configuração recomendada para Samsung Galaxy Tab A9+ (One UI).",
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.45f)),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.TabletAndroid,
              contentDescription = "Dispositivo",
              tint = CyanElectric,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Compatibilidade Samsung SM-X210",
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Text(
            text = "Dispositivo atual: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.labelMedium,
            color = CyanElectric,
          )
          Text(
            text =
              "O Samsung Galaxy Tab A9+ (SM-X210) usa One UI com restrições agressivas de economia de bateria em segundo plano. Siga os passos abaixo para evitar que o sistema encerre a acessibilidade ou a captura.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      GuideStepCard(
        stepNumber = "1",
        title = "Acessibilidade (Apps Instalados)",
        body =
          "Em Configurações > Acessibilidade > Aplicativos instalados, ative \"PT Pilot — Leitura e Automação\". Se a One UI exibir aviso de configuração restrita, abra Informações do App > menu ⋮ > Permitir configurações restritas.",
        done = permissionStatus.accessibilityEnabled,
        actionLabel = "Abrir Acessibilidade",
        testTag = "guide_accessibility_btn",
        onAction = onOpenAccessibility,
      )
    }

    item {
      GuideStepCard(
        stepNumber = "2",
        title = "Bateria Sem Restrições (One UI)",
        body =
          "Defina a bateria do PT Pilot como \"Não restrito\" e remova o app de \"Aplicativos em suspensão profunda\" em Assistência do Aparelho > Bateria.",
        done = permissionStatus.batteryOptimizationIgnored,
        actionLabel = "Configurar Bateria",
        testTag = "guide_battery_btn",
        onAction = onOpenBatterySettings,
      )
    }

    item {
      GuideStepCard(
        stepNumber = "3",
        title = "Sobreposição (Bolha Flutuante)",
        body =
          "Permite exibir a bolha discreta de controle e os contornos visuais de detecção no Modo Observação sobre os jogos em tela cheia.",
        done = permissionStatus.overlayEnabled,
        actionLabel = "Liberar Sobreposição",
        testTag = "guide_overlay_btn",
        onAction = onOpenOverlay,
      )
    }

    item {
      GuideStepCard(
        stepNumber = "4",
        title = "Captura MediaProjection + OCR Local",
        body =
          "No Android 14+, ao autorizar a captura de tela, escolha \"Tela inteira\" (quando disponível) para que o ML Kit reconheça botões desenhados em WebViews e jogos Unity.",
        done = permissionStatus.screenCaptureActive,
        actionLabel = "Autorizar Captura",
        testTag = "guide_capture_btn",
        onAction = onRequestScreenCapture,
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
        border = BorderStroke(1.dp, SlateBorder),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Roteiro de Teste Seguro",
            style = MaterialTheme.typography.titleMedium,
            color = EmeraldSafe,
          )
          Text(
            text =
              "1. Inicie no Modo Observação.\n" +
                "2. Abra um jogo e aguarde um anúncio premiado completo.\n" +
                "3. Observe as caixas verdes/vermelhas desenhadas pela sobreposição.\n" +
                "4. Volte ao PT Pilot > Histórico e confirme os botões verdadeiros de fechar.\n" +
                "5. Só então mude para Modo Automático.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun GuideStepCard(
  stepNumber: String,
  title: String,
  body: String,
  done: Boolean,
  actionLabel: String,
  testTag: String,
  onAction: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, if (done) EmeraldSafe.copy(alpha = 0.45f) else SlateBorder),
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
          text = "Passo $stepNumber • $title",
          style = MaterialTheme.typography.titleMedium,
          color = if (done) EmeraldSafe else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        if (done) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Concluído",
            tint = EmeraldSafe,
          )
        }
      }

      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FilledTonalButton(
        onClick = onAction,
        modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
      ) {
        Text(
          text = if (done) "Reabrir ($actionLabel)" else actionLabel,
          color = if (done) EmeraldSafe else AmberObserve,
        )
      }
    }
  }
}
