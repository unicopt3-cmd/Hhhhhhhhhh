package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.SafetyConfig
import com.example.ui.components.PTPilotBrandHeader
import com.example.ui.components.PrivacyGuaranteePill
import com.example.ui.theme.AmberObserve
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import kotlin.math.roundToInt

@Composable
fun SafetySettingsScreen(
  config: SafetyConfig,
  onConfidenceThresholdChange: (Float) -> Unit,
  onMinAdWatchChange: (Int) -> Unit,
  onClickCooldownChange: (Long) -> Unit,
  onRedirectProtectionChange: (Boolean) -> Unit,
  onMaxRedirectRetriesChange: (Int) -> Unit,
  onOcrEnabledChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      PTPilotBrandHeader(
        sectionTitle = "Segurança",
        sectionSubtitle = "Controle pontuações, tempo mínimo e proteção contra redirecionamentos.",
      )
    }

    item {
      Text(
        text = "Configurações de Segurança",
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = "Ajuste a exigência do motor multi-sinal, tempos de espera e proteção contra redirecionamentos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateBorder),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Pontuação Mínima de Confiança",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${(config.confidenceThreshold * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = CyanElectric,
              )
            }
            Text(
              text = "Quando a pontuação estiver abaixo desse limite, nenhum clique automático é executado.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
              value = config.confidenceThreshold,
              onValueChange = onConfidenceThresholdChange,
              valueRange = 0.50f..0.95f,
              steps = 8,
              colors = SliderDefaults.colors(thumbColor = CyanElectric, activeTrackColor = CyanElectric),
              modifier = Modifier.testTag("slider_confidence_threshold"),
            )
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Tempo Mínimo de Exibição do Anúncio",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${config.minAdWatchSeconds}s",
                style = MaterialTheme.typography.labelMedium,
                color = AmberObserve,
              )
            }
            Text(
              text = "Aguarda esse tempo após identificar um anúncio antes de procurar ou acionar o fechamento.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
              value = config.minAdWatchSeconds.toFloat(),
              onValueChange = { onMinAdWatchChange(it.roundToInt()) },
              valueRange = 0f..15f,
              steps = 14,
              colors = SliderDefaults.colors(thumbColor = AmberObserve, activeTrackColor = AmberObserve),
              modifier = Modifier.testTag("slider_min_watch"),
            )
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Intervalo Entre Cliques (Cooldown)",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = String.format("%.1fs", config.clickCooldownMs / 1000f),
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldSafe,
              )
            }
            Text(
              text = "Evita cliques repetidos e ações precipitadas durante a transição do anúncio.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
              value = config.clickCooldownMs.toFloat(),
              onValueChange = { onClickCooldownChange(it.roundToInt().toLong()) },
              valueRange = 1000f..5000f,
              steps = 7,
              colors = SliderDefaults.colors(thumbColor = EmeraldSafe, activeTrackColor = EmeraldSafe),
              modifier = Modifier.testTag("slider_click_cooldown"),
            )
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateBorder),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Proteção contra Redirecionamentos",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Detecta abertura da Google Play Store, Galaxy Store ou navegador e tenta retornar ao jogo anterior.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = config.redirectProtectionEnabled,
              onCheckedChange = onRedirectProtectionChange,
              modifier = Modifier.testTag("switch_redirect_protection"),
            )
          }

          if (config.redirectProtectionEnabled) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Limite Máximo de Tentativas Voltar",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "${config.maxRedirectRetries}x",
                  style = MaterialTheme.typography.labelMedium,
                  color = CyanElectric,
                )
              }
              Text(
                text = "Garante que o aplicativo nunca entre em ciclos infinitos de navegação.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Slider(
                value = config.maxRedirectRetries.toFloat(),
                onValueChange = { onMaxRedirectRetriesChange(it.roundToInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.testTag("slider_max_retries"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "OCR Local via MediaProjection (ML Kit)",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Reconhece textos como Skip, Pular, Close e Fechar mesmo quando desenhados fora da árvore de acessibilidade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = config.ocrEnabled,
              onCheckedChange = onOcrEnabledChange,
              modifier = Modifier.testTag("switch_ocr_enabled"),
            )
          }
        }
      }
    }

    item {
      PrivacyGuaranteePill()
    }
  }
}
