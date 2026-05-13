package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.ui.components.GameButtonDensity
import rpg.android.ui.components.GamePrimaryButton
import rpg.application.actions.GameAction
import rpg.presentation.model.ScreenOptionViewModel

@Composable
internal fun PremiumPlanGrid(
    options: List<ScreenOptionViewModel>,
    onAction: (GameAction) -> Unit
) {
    val goldOptions = options.filter(::isGoldPremiumOption)
    val cashOptions = options.filter(::isCashPremiumOption)
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        PremiumPlanColumn(
            title = "OURO",
            options = goldOptions,
            onAction = onAction,
            modifier = Modifier.weight(1f)
        )
        PremiumPlanColumn(
            title = "CASH",
            options = cashOptions,
            onAction = onAction,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PremiumPlanColumn(
    title: String,
    options: List<ScreenOptionViewModel>,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall
        )
        options.forEach { option ->
            GamePrimaryButton(
                label = compactPremiumOptionLabel(option.label),
                onClick = { onAction(option.action) },
                modifier = Modifier.fillMaxWidth(),
                enabled = option.enabled,
                density = GameButtonDensity.COMPACT
            )
        }
    }
}

internal fun isPremiumPlansContext(
    title: String,
    options: List<ScreenOptionViewModel>
): Boolean {
    if (title.contains("premium", ignoreCase = true)) {
        return options.any { it.action is GameAction.BuyPremiumPlan }
    }
    return false
}

private fun compactPremiumOptionLabel(label: String): String {
    val parts = label.split("|").map { it.trim() }.filter { it.isNotBlank() }
    val base = parts.firstOrNull().orEmpty()
        .replace("(Ouro)", "", ignoreCase = true)
        .replace("(Cash)", "", ignoreCase = true)
        .replace(" dias", "d", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()
    val cost = parts.getOrNull(1)
        ?.replace(" ouro", "g", ignoreCase = true)
        ?.replace(" CASH", "c", ignoreCase = true)
        ?.trim()
        .orEmpty()
    return listOfNotNull(base.ifBlank { null }, cost.ifBlank { null }).joinToString(" | ").ifBlank { label }
}

private fun isGoldPremiumOption(option: ScreenOptionViewModel): Boolean {
    val action = option.action as? GameAction.BuyPremiumPlan ?: return false
    return action.planId.contains("_gold_", ignoreCase = true)
}

private fun isCashPremiumOption(option: ScreenOptionViewModel): Boolean {
    val action = option.action as? GameAction.BuyPremiumPlan ?: return false
    return action.planId.contains("_cash_", ignoreCase = true)
}
