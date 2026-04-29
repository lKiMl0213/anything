package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.android.state.CombatUiState

@Composable
fun CombatTouchScreen(
    state: CombatUiState,
    onAttack: () -> Unit,
    onEscape: () -> Unit,
    onUseItem: (String) -> Unit
) {
    var selectedItemIndex by remember(state.consumables) { mutableIntStateOf(0) }
    if (selectedItemIndex >= state.consumables.size) {
        selectedItemIndex = 0
    }
    val selectedConsumable = state.consumables.getOrNull(selectedItemIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(state.title)
        state.introLines.forEach { Text(it) }

        Text("Combate | ${state.enemyName}")
        Text("${state.playerName} HP ${format(state.playerHp)} / ${format(state.playerHpMax)} | MP ${format(state.playerMp)} / ${format(state.playerMpMax)}")
        Text("Inimigo HP ${format(state.enemyHp)} / ${format(state.enemyHpMax)}")

        Text("Voce: ${state.playerAtbLabel}")
        LinearProgressIndicator(progress = { state.playerAtbProgress }, modifier = Modifier.fillMaxWidth())
        Text("Inimigo: ${state.enemyAtbLabel}")
        LinearProgressIndicator(progress = { state.enemyAtbProgress }, modifier = Modifier.fillMaxWidth())

        state.statusLines.forEach { Text(it) }

        if (state.playerReady) {
            Text("Voce esta pronto para agir.")
        } else {
            Text("Aguardando carregamento do turno...")
        }

        Text("Historico:")
        if (state.logLines.isEmpty()) {
            Text("-")
        } else {
            state.logLines.forEach { Text("- $it") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAttack,
                enabled = state.playerReady,
                modifier = Modifier.weight(1f)
            ) {
                Text("Atacar")
            }
            Button(
                onClick = {
                    selectedConsumable?.let { onUseItem(it.itemId) }
                },
                enabled = state.playerReady && selectedConsumable != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Usar item")
            }
            Button(
                onClick = onEscape,
                enabled = state.playerReady,
                modifier = Modifier.weight(1f)
            ) {
                Text("Fugir")
            }
        }

        if (state.consumables.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        selectedItemIndex = (selectedItemIndex - 1 + state.consumables.size) % state.consumables.size
                    }
                ) { Text("<") }
                Text("Item: ${selectedConsumable?.label ?: "-"}")
                Button(
                    onClick = {
                        selectedItemIndex = (selectedItemIndex + 1) % state.consumables.size
                    }
                ) { Text(">") }
            }
        } else {
            Text("Sem consumiveis disponiveis.")
        }
    }
}

private fun format(value: Double): String = "%.1f".format(value)
