package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.CombatUiState
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.GameStatBar
import rpg.android.ui.components.GameUiTokens
import rpg.android.ui.scale.LocalGameUiSettings
import rpg.android.ui.scale.GameUiScale

@Composable
fun CombatTouchScreen(
    state: CombatUiState,
    onAttack: () -> Unit,
    onEscape: () -> Unit,
    onUseItem: (String) -> Unit
) {
    val uiScale = LocalGameUiSettings.current.scale
    val rowSpacing = GameUiTokens.panelSpacing
    val effectHeight = when (uiScale) {
        GameUiScale.SMALL -> 58.dp
        GameUiScale.MEDIUM -> 72.dp
        GameUiScale.LARGE -> 90.dp
    }
    val historyMinHeight = when (uiScale) {
        GameUiScale.SMALL -> 84.dp
        GameUiScale.MEDIUM -> 96.dp
        GameUiScale.LARGE -> 112.dp
    }
    val historyMaxHeight = when (uiScale) {
        GameUiScale.SMALL -> 84.dp
        GameUiScale.MEDIUM -> 96.dp
        GameUiScale.LARGE -> 112.dp
    }
    var selectedItemIndex by remember(state.consumables) { mutableIntStateOf(0) }
    if (selectedItemIndex >= state.consumables.size) {
        selectedItemIndex = 0
    }
    val selectedConsumable = state.consumables.getOrNull(selectedItemIndex)
    val visibleLogs = state.logLines
        .map(::sanitizeLogLine)
        .filter { it.isNotBlank() }
        .takeLast(40)

    val historyScrollState = rememberScrollState()

    GameScreenRoot(backgroundRes = R.drawable.bg_combat) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            GamePanel(title = state.title) {
                state.introLines.take(2).forEach { Text(it) }
                Text("Inimigo: ${state.enemyName}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                GamePanel(modifier = Modifier.weight(1f), title = state.playerName) {
                    GameStatBar(label = "HP", current = state.playerHp, max = state.playerHpMax)
                    GameStatBar(label = "MP", current = state.playerMp, max = state.playerMpMax)
                }
                GamePanel(modifier = Modifier.weight(1f), title = state.enemyName) {
                    GameStatBar(label = "HP", current = state.enemyHp, max = state.enemyHpMax)
                    Text(
                        text = "HP ${(state.enemyHp / state.enemyHpMax.coerceAtLeast(1.0) * 100.0).toInt()}%",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            GamePanel(title = "ATB") {
                Text("Voce: ${state.playerAtbLabel}")
                LinearProgressIndicator(progress = { state.playerAtbProgress }, modifier = Modifier.fillMaxWidth())
                Text("Inimigo: ${state.enemyAtbLabel}")
                LinearProgressIndicator(progress = { state.enemyAtbProgress }, modifier = Modifier.fillMaxWidth())
            }

            GamePanel(title = "Efeitos") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = effectHeight, max = effectHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (state.statusLines.isEmpty()) {
                            Text("Sem efeitos ativos.")
                        } else {
                            state.statusLines.forEach { line ->
                                Text(line)
                            }
                        }
                    }
                }
            }

            GamePanel(title = "Historico") {
                LaunchedEffect(visibleLogs.size) {
                    historyScrollState.scrollTo(historyScrollState.maxValue)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = historyMinHeight, max = historyMaxHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(historyScrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (visibleLogs.isEmpty()) {
                            Text("-")
                        } else {
                            visibleLogs.forEach { Text("- $it") }
                        }
                    }
                }
            }

            GamePanel(title = "Estado da acao") {
                Text(
                    text = if (state.playerReady) "Acao pronta!" else "Aguardando carregamento da barra de acao.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            GamePanel(title = "Acoes") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    GamePrimaryButton(
                        label = "Atacar",
                        onClick = onAttack,
                        enabled = state.playerReady,
                        modifier = Modifier.weight(1f)
                    )
                    GamePrimaryButton(
                        label = "Usar item",
                        onClick = { selectedConsumable?.let { onUseItem(it.itemId) } },
                        enabled = state.playerReady && selectedConsumable != null,
                        modifier = Modifier.weight(1f)
                    )
                    GamePrimaryButton(
                        label = "Fugir",
                        onClick = onEscape,
                        enabled = state.playerReady,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.consumables.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GamePrimaryButton(
                            label = "<",
                            onClick = {
                                selectedItemIndex = (selectedItemIndex - 1 + state.consumables.size) % state.consumables.size
                            },
                            modifier = Modifier.widthIn(min = 56.dp, max = 64.dp)
                        )
                        Text(
                            text = "Item: ${selectedConsumable?.label ?: "-"}",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        GamePrimaryButton(
                            label = ">",
                            onClick = {
                                selectedItemIndex = (selectedItemIndex + 1) % state.consumables.size
                            },
                            modifier = Modifier.widthIn(min = 56.dp, max = 64.dp)
                        )
                    }
                } else {
                    Text("Sem consumiveis disponiveis.")
                }
            }
        }
    }
}

private fun sanitizeLogLine(raw: String): String {
    return raw
        .replace(Regex("\\u001B\\[[;\\d]*m"), "")
        .replace(Regex("\\[(?:\\d{1,3};?)+m"), "")
        .trim()
}
