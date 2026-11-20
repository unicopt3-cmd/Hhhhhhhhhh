package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AdDecisionEntity
import com.example.data.LearnedPatternEntity
import com.example.model.DecisionType
import com.example.model.RegionBucket
import com.example.ui.components.ConfidenceBar
import com.example.ui.components.PTPilotBrandHeader
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

private enum class HistoryFilter(val label: String) {
  ALL("Todos"),
  PENDING("Pendentes"),
  SAFE("Seguros / Cliques"),
  TRAPS("Armadilhas"),
  REDIRECTS("Redirecionamentos"),
}

@Composable
fun HistoryLearningScreen(
  decisions: List<AdDecisionEntity>,
  learnedPatterns: List<LearnedPatternEntity>,
  onValidateDecision: (Long, Boolean) -> Unit,
  onDeletePattern: (Long) -> Unit,
  onClearHistory: () -> Unit,
  onClearLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

  Column(modifier = modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = selectedTab, containerColor = SlateSurface) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Histórico (${decisions.size})") },
        modifier = Modifier.testTag("tab_history"),
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Biblioteca Local (${learnedPatterns.size})") },
        modifier = Modifier.testTag("tab_library"),
      )
    }

    if (selectedTab == 0) {
      val filteredDecisions =
        remember(decisions, selectedFilter) {
          when (selectedFilter) {
            HistoryFilter.ALL -> decisions
            HistoryFilter.PENDING -> decisions.filter { it.userConfirmed == null }
            HistoryFilter.SAFE ->
              decisions.filter {
                it.decisionType == DecisionType.CLICKED_CLOSE.name ||
                  it.decisionType == DecisionType.OBSERVED_CANDIDATE.name
              }
            HistoryFilter.TRAPS ->
              decisions.filter { it.decisionType == DecisionType.BLOCKED_AD_TRAP.name }
            HistoryFilter.REDIRECTS ->
              decisions.filter { it.decisionType == DecisionType.REDIRECT_RECOVERED.name }
          }
        }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          PTPilotBrandHeader(
            sectionTitle = "Aprendizagem",
            sectionSubtitle = "Valide botões reais de fechar/pular e fortaleça a biblioteca local.",
          )
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Validação & Aprendizagem",
              style = MaterialTheme.typography.titleLarge,
            )
            if (decisions.isNotEmpty()) {
              TextButton(
                onClick = onClearHistory,
                modifier = Modifier.testTag("clear_history_button"),
              ) {
                Text("Limpar", color = CoralAlert)
              }
            }
          }
        }

        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
              )
            }
          }
        }

        if (filteredDecisions.isEmpty()) {
          item {
            EmptyStateCard(
              icon = Icons.Filled.History,
              title = "Nenhum registro nesta categoria",
              tip =
                "Ative o Modo Observação no painel principal e abra um jogo com anúncios. Cada botão encontrado aparecerá aqui para você confirmar ou rejeitar.",
            )
          }
        } else {
          items(filteredDecisions, key = { it.id }) { item ->
            DecisionHistoryCard(
              item = item,
              onConfirmTrueClose = { onValidateDecision(item.id, true) },
              onMarkAsTrap = { onValidateDecision(item.id, false) },
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Padrões Aprendidos Localmente",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "Usados pelo motor multi-sinal para aumentar a precisão em anúncios futuros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (learnedPatterns.isNotEmpty()) {
              TextButton(
                onClick = onClearLibrary,
                modifier = Modifier.testTag("clear_library_button"),
              ) {
                Text("Limpar", color = CoralAlert)
              }
            }
          }
        }

        if (learnedPatterns.isEmpty()) {
          item {
            EmptyStateCard(
              icon = Icons.Filled.Psychology,
              title = "Biblioteca de padrões vazia",
              tip =
                "Confirme botões verdadeiros ou armadilhas na aba Histórico para construir sua biblioteca local de reconhecimento.",
            )
          }
        } else {
          items(learnedPatterns, key = { it.id }) { pattern ->
            LearnedPatternCard(
              pattern = pattern,
              onDelete = { onDeletePattern(pattern.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DecisionHistoryCard(
  item: AdDecisionEntity,
  onConfirmTrueClose: () -> Unit,
  onMarkAsTrap: () -> Unit,
) {
  val decisionType = runCatching { DecisionType.valueOf(item.decisionType) }.getOrNull()
  val accent =
    when (decisionType) {
      DecisionType.CLICKED_CLOSE -> EmeraldSafe
      DecisionType.BLOCKED_AD_TRAP, DecisionType.REDIRECT_RECOVERED -> CoralAlert
      DecisionType.OBSERVED_CANDIDATE -> AmberObserve
      else -> CyanElectric
    }
  val regionLabel =
    runCatching { RegionBucket.valueOf(item.regionBucket).labelPt }.getOrDefault(item.regionBucket)
  val formatter = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = accent.copy(alpha = 0.16f),
        ) {
          Text(
            text = decisionType?.labelPt ?: item.decisionType,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Text(
          text = formatter.format(Date(item.timestamp)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = "Elemento: \"${item.detectedText}\" (${item.source})",
        style = MaterialTheme.typography.titleMedium,
      )

      Text(
        text = "App: ${item.gamePackage.ifBlank { "Desconhecido" }} • $regionLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (item.elementId.isNotBlank()) {
        Text(
          text = "ID: ${item.elementId}",
          style = MaterialTheme.typography.labelSmall,
          color = CyanElectric,
        )
      }

      ConfidenceBar(score = item.confidenceScore, color = accent)

      Text(
        text = item.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (item.signalDetails.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = SlateSurfaceElevated,
        ) {
          Text(
            text = item.signalDetails,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
          )
        }
      }

      // Learning validation buttons
      if (decisionType != DecisionType.REDIRECT_RECOVERED) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onConfirmTrueClose,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("confirm_close_${item.id}"),
            border =
              BorderStroke(
                1.dp,
                if (item.userConfirmed == true) EmeraldSafe else SlateBorder,
              ),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = if (item.userConfirmed == true) EmeraldSafe.copy(alpha = 0.16f) else SlateSurfaceElevated,
                contentColor = EmeraldSafe,
              ),
          ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (item.userConfirmed == true) "Confirmado" else "Fechamento Real")
          }

          OutlinedButton(
            onClick = onMarkAsTrap,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("mark_trap_${item.id}"),
            border =
              BorderStroke(
                1.dp,
                if (item.userConfirmed == false) CoralAlert else SlateBorder,
              ),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = if (item.userConfirmed == false) CoralAlert.copy(alpha = 0.16f) else SlateSurfaceElevated,
                contentColor = CoralAlert,
              ),
          ) {
            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (item.userConfirmed == false) "Armadilha" else "É Link/Anúncio")
          }
        }
      }
    }
  }
}

@Composable
private fun LearnedPatternCard(
  pattern: LearnedPatternEntity,
  onDelete: () -> Unit,
) {
  val accent = if (pattern.isSafeClose) EmeraldSafe else CoralAlert
  val regionLabel =
    runCatching { RegionBucket.valueOf(pattern.regionBucket).labelPt }.getOrDefault(pattern.regionBucket)

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text =
            "${if (pattern.isSafeClose) "BOTÃO SEGURO" else "ARMADILHA BLOQUEADA"} • ${pattern.confirmationCount}x",
          style = MaterialTheme.typography.labelSmall,
          color = accent,
        )
        Text(
          text = "Texto: \"${pattern.normalizedText.ifBlank { "(sem texto)" }}\"",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Região: $regionLabel • ID: ${pattern.resourceIdPattern.ifBlank { "nenhum" }}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_pattern_${pattern.id}"),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = "Remover padrão aprendido",
          tint = CoralAlert,
        )
      }
    }
  }
}

@Composable
private fun EmptyStateCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  tip: String,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = SlateSurface),
    border = BorderStroke(1.dp, SlateBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = CyanElectric,
        modifier = Modifier.size(40.dp),
      )
      Text(text = title, style = MaterialTheme.typography.titleLarge)
      Text(
        text = tip,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
