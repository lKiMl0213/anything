package rpg.android.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun AttributeStepperRow(
    code: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(code)
        Button(onClick = onDecrement, enabled = canDecrement) { Text("-") }
        Text(value.toString())
        Button(onClick = onIncrement, enabled = canIncrement) { Text("+") }
    }
}
