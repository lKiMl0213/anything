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
fun AttributesScreen(
    currentAttributes: Map<String, Int>,
    availablePoints: Int,
    onApply: (finalAttributes: Map<String, Int>, spentPoints: Int) -> Unit,
    onBack: () -> Unit
) {
    var localAvailablePoints by remember(availablePoints) { mutableStateOf(availablePoints.coerceAtLeast(0)) }
    var pendingPoints by remember(currentAttributes) {
        mutableStateOf(attributeCodes.associateWith { 0 })
    }

    MenuLayout(title = "Atributos") {
        androidx.compose.material3.Text("Pontos disponiveis: $localAvailablePoints")

        attributeCodes.forEach { code ->
            val baseValue = currentAttributes[code] ?: 0
            val pending = pendingPoints[code] ?: 0
            val currentValue = baseValue + pending

            AttributeStepperRow(
                code = code,
                value = currentValue,
                canDecrement = pending > 0,
                canIncrement = localAvailablePoints > 0,
                onIncrement = {
                    if (localAvailablePoints <= 0) return@AttributeStepperRow
                    pendingPoints = pendingPoints.toMutableMap().apply {
                        this[code] = pending + 1
                    }
                    localAvailablePoints -= 1
                },
                onDecrement = {
                    if (pending <= 0) return@AttributeStepperRow
                    pendingPoints = pendingPoints.toMutableMap().apply {
                        this[code] = pending - 1
                    }
                    localAvailablePoints += 1
                }
            )
        }

        val pointsSpent = availablePoints - localAvailablePoints
        MenuButton("APLICAR") {
            val finalValues = attributeCodes.associateWith { code ->
                (currentAttributes[code] ?: 0) + (pendingPoints[code] ?: 0)
            }
            onApply(finalValues, pointsSpent.coerceAtLeast(0))
        }
        MenuButton("VOLTAR", onBack)
    }
}
