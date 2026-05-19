package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.AttributeDistributionUiModel
import rpg.android.ui.components.AttributeInfoPopup
import rpg.android.ui.components.AttributeRow
import rpg.android.ui.components.GameBackIconButton
import rpg.android.ui.components.GameFooterActions
import rpg.android.ui.components.GameInfoPanel
import rpg.android.ui.components.GameScreenRoot

@Composable
fun AttributeDistributionScreen(
    state: AttributeDistributionUiModel,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var popupCode by remember { mutableStateOf<String?>(null) }
    val popupLines = popupCode?.let { code -> state.detailByCode[code] }.orEmpty()

    GameScreenRoot(
        backgroundRes = R.drawable.bg_attribute_distribution,
        footer = {
            GameFooterActions(
                rightLabel = "Confirmar",
                onRightClick = onConfirm,
                rightEnabled = state.canConfirm
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBackIconButton(onClick = onCancel)
            }

            GameInfoPanel(title = "Distribuicao de Atributos") {
                Text(
                    text = "Pontos de atributos disponíveis: ${state.pointsRemaining}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            GameInfoPanel(title = "Atributos") {
                state.rows.forEach { row ->
                    AttributeRow(
                        code = row.label,
                        valueLabel = row.previewValue.toString(),
                        allocatedLabel = "[alocado ${row.allocated}]",
                        onInfoClick = { popupCode = row.code },
                        onDecrease = { onDecrease(row.code) },
                        onIncrease = { onIncrease(row.code) },
                        canDecrease = row.allocated > 0,
                        canIncrease = state.pointsRemaining > 0
                    )
                }
            }
            if (state.messages.isNotEmpty()) {
                GameInfoPanel {
                    state.messages.forEach { message ->
                        Text(
                            text = message,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    popupCode?.let { selectedCode ->
        AttributeInfoPopup(
            title = state.rows.firstOrNull { it.code == selectedCode }?.label ?: selectedCode,
            lines = popupLines,
            onDismiss = { popupCode = null }
        )
    }
}
