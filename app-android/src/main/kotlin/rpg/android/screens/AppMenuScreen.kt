package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rpg.application.actions.GameAction
import rpg.presentation.model.MenuScreenViewModel

@Composable
fun AppMenuScreen(
    model: MenuScreenViewModel,
    onSelectAction: (GameAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = model.title)
        model.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it) }
        model.summary?.let { summary ->
            Text("${summary.name} | Nivel ${summary.level}")
            Text(summary.classLabel)
            Text("HP ${format(summary.hp.current)}/${format(summary.hp.max)}")
            Text("MP ${format(summary.mp.current)}/${format(summary.mp.max)}")
            Text("Ouro ${summary.gold}")
        }

        if (model.messages.isNotEmpty()) {
            Divider()
            model.messages.forEach { Text(it) }
        }

        if (model.bodyLines.isNotEmpty()) {
            Divider()
            model.bodyLines.forEach { Text(it) }
        }

        Divider()
        model.options.forEach { option ->
            val isRankUp = option.action is GameAction.ConfirmTalentRankUp
            Button(onClick = { onSelectAction(option.action) }) {
                if (isRankUp) {
                    Text("+")
                } else {
                    Text(option.label)
                }
            }
        }
    }
}

private fun format(value: Double): String = "%.1f".format(value)
