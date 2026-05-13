package rpg.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class InventoryRowItem(
    val id: String,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun InventoryPanel(
    title: String,
    items: List<InventoryRowItem>,
    modifier: Modifier = Modifier
) {
    GamePanel(
        modifier = modifier,
        title = title
    ) {
        if (items.isEmpty()) {
            Text("Inventário vazio.")
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
            ) {
                items.forEach { item ->
                    Text(
                        text = item.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClick)
                    )
                }
            }
        }
    }
}

