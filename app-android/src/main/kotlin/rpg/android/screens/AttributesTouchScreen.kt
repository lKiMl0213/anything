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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.android.state.AttributeAllocationUiState

@Composable
fun AttributesTouchScreen(
    state: AttributeAllocationUiState,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Atributos")
        Text("Pontos disponiveis: ${state.pointsRemaining}")

        state.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${row.code} ${row.previewFinal}", modifier = Modifier.weight(1f))
                Button(onClick = { onDecrease(row.code) }, enabled = row.pending > 0) { Text("-") }
                Button(onClick = { onIncrease(row.code) }, enabled = state.pointsRemaining > 0) { Text("+") }
            }
            Text(
                "Base ${row.currentBase} | Equip ${signed(row.equipmentBonus)} | Classe/Tal ${signed(row.classTalentBonus)} | Temp ${signed(row.temporaryBonus)}"
            )
        }

        if (state.messages.isNotEmpty()) {
            state.messages.forEach { Text(it) }
        }

        Button(
            onClick = onApply,
            enabled = state.canApply,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("APLICAR")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("VOLTAR")
        }
    }
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()
