package rpg.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import rpg.android.components.AttributeStepperRow
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

private val attributeCodes = listOf("STR", "AGI", "DEX", "VIT", "INT", "SPR", "LUK")

@Composable
fun CharacterCreationScreen(
    initialAttributes: Map<String, Int>,
    initialPool: Int,
    onConfirm: (Map<String, Int>) -> Unit,
    onBack: () -> Unit
) {
    var availablePoints by remember(initialPool) { mutableStateOf(initialPool.coerceAtLeast(0)) }
    var pendingPoints by remember(initialAttributes) {
        mutableStateOf(attributeCodes.associateWith { 0 })
    }

    MenuLayout(title = "Criacao de Personagem") {
        androidx.compose.material3.Text("Pontos disponiveis: $availablePoints")

        attributeCodes.forEach { code ->
            val baseValue = initialAttributes[code] ?: 0
            val pending = pendingPoints[code] ?: 0
            val currentValue = baseValue + pending

            AttributeStepperRow(
                code = code,
                value = currentValue,
                canDecrement = pending > 0,
                canIncrement = availablePoints > 0,
                onIncrement = {
                    if (availablePoints <= 0) return@AttributeStepperRow
                    pendingPoints = pendingPoints.toMutableMap().apply {
                        this[code] = pending + 1
                    }
                    availablePoints -= 1
                },
                onDecrement = {
                    if (pending <= 0) return@AttributeStepperRow
                    pendingPoints = pendingPoints.toMutableMap().apply {
                        this[code] = pending - 1
                    }
                    availablePoints += 1
                }
            )
        }

        MenuButton("CONFIRMAR") {
            val finalValues = attributeCodes.associateWith { code ->
                (initialAttributes[code] ?: 0) + (pendingPoints[code] ?: 0)
            }
            onConfirm(finalValues)
        }
        MenuButton("VOLTAR", onBack)
    }
}
