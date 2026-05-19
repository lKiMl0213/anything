package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.state.MainSection
import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameOutlinedTextField
import rpg.application.actions.GameAction

@Composable
internal fun MenuActionPreviewPopup(
    preview: MenuActionPreviewUiModel,
    section: MainSection,
    onDismiss: () -> Unit,
    onAction: (GameAction) -> Unit
) {
    val picker = preview.quantityPicker
    val min = picker?.minValue ?: 1
    val max = (picker?.maxValue ?: 1).coerceAtLeast(min)
    val hasDetailPopup = preview.detailPopupLines.isNotEmpty()
    var quantityInput by remember(preview.optionKey, min, max) {
        mutableStateOf((picker?.currentValue?.coerceIn(min, max) ?: min).toString())
    }
    var showDetailPopup by remember(preview.optionKey) { mutableStateOf(false) }
    val selectedQuantity = quantityInput.toIntOrNull()?.coerceIn(min, max) ?: min
    val canDecrease = selectedQuantity > min
    val canIncrease = selectedQuantity < max

    if (showDetailPopup) {
        GamePopup(
            title = preview.detailPopupTitle ?: preview.title,
            onDismiss = onDismiss
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                preview.detailPopupLines.forEach { line ->
                    Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                GamePrimaryButton(
                    label = "Voltar",
                    onClick = { showDetailPopup = false },
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            }
        }
        return
    }

    GamePopup(
        title = preview.title,
        onDismiss = onDismiss,
        showCloseButton = section != MainSection.PRODUCTION
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            preview.lines.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            if (picker != null) {
                Text(
                    text = "Quantidade: $selectedQuantity / CAP: $max",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GamePrimaryButton(
                        label = "-",
                        onClick = { quantityInput = (selectedQuantity - 1).coerceAtLeast(min).toString() },
                        enabled = canDecrease,
                        modifier = Modifier.weight(1f)
                    )
                    GameOutlinedTextField(
                        value = quantityInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            quantityInput = if (digits.isBlank()) {
                                ""
                            } else {
                                digits.toIntOrNull()?.coerceIn(min, max)?.toString() ?: min.toString()
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                    GamePrimaryButton(
                        label = "+",
                        onClick = { quantityInput = (selectedQuantity + 1).coerceAtMost(max).toString() },
                        enabled = canIncrease,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            GamePrimaryButton(
                label = preview.primaryLabel,
                onClick = {
                    if (hasDetailPopup && preview.primaryAction is GameAction.InspectTalentNode) {
                        showDetailPopup = true
                        return@GamePrimaryButton
                    }
                    onDismiss()
                    val action = picker?.applyAction?.invoke(selectedQuantity) ?: preview.primaryAction
                    onAction(action)
                },
                modifier = Modifier.fillMaxWidth(0.88f)
            )

            if (preview.secondaryLabel != null && preview.secondaryAction != null) {
                GamePrimaryButton(
                    label = preview.secondaryLabel,
                    onClick = {
                        onDismiss()
                        onAction(preview.secondaryAction)
                    },
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            } else if (preview.secondaryLabel != null && hasDetailPopup) {
                GamePrimaryButton(
                    label = preview.secondaryLabel,
                    onClick = { showDetailPopup = true },
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            } else if (preview.secondaryLabel != null && section != MainSection.PRODUCTION) {
                GamePrimaryButton(
                    label = preview.secondaryLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            }

            if (section == MainSection.PRODUCTION) {
                GamePrimaryButton(
                    label = "Cancelar",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.88f)
                )
            }
        }
    }
}
