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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.android.state.CharacterCreationUiState

@Composable
fun CharacterCreationTouchScreen(
    state: CharacterCreationUiState,
    onNameChanged: (String) -> Unit,
    onSelectRace: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onIncreaseAttribute: (String) -> Unit,
    onDecreaseAttribute: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Novo Jogo")
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome do personagem") },
            singleLine = true
        )

        Text("Raca")
        state.races.forEach { race ->
            val selectedPrefix = if (race.id == state.selectedRaceId) "[Selecionado] " else ""
            Button(
                onClick = { onSelectRace(race.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$selectedPrefix${race.label}")
            }
        }

        Text("Classe")
        state.classes.forEach { clazz ->
            val selectedPrefix = if (clazz.id == state.selectedClassId) "[Selecionada] " else ""
            Button(
                onClick = { onSelectClass(clazz.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$selectedPrefix${clazz.label}")
            }
        }

        Text("Pontos restantes: ${state.pointsRemaining}")
        state.attributes.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${row.code} ${row.finalValue}", modifier = Modifier.weight(1f))
                Button(
                    onClick = { onDecreaseAttribute(row.code) },
                    enabled = row.allocated > 0
                ) { Text("-") }
                Button(
                    onClick = { onIncreaseAttribute(row.code) },
                    enabled = state.pointsRemaining > 0
                ) { Text("+") }
            }
            Text(
                "Bonus racial ${signed(row.raceBonus)} | Bonus classe ${signed(row.classBonus)} | Distribuido ${row.allocated}"
            )
        }

        state.message?.takeIf { it.isNotBlank() }?.let { Text(it) }

        Button(
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CONFIRMAR CRIACAO")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("VOLTAR") }
    }
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
