package rpg.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InventoryRowItem(
    val id: String,
    val label: String,
    val onClick: () -> Unit
)

data class BackpackTierIndicator(
    val tier: Int,
    val equipped: Boolean
)

@Composable
fun InventoryPanel(
    capacityLabel: String,
    sortModeLabel: String,
    onSortSelected: (String) -> Unit,
    backpackIndicators: List<BackpackTierIndicator>,
    items: List<InventoryRowItem>,
    modifier: Modifier = Modifier
) {
    GamePanel(
        modifier = modifier,
        title = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inventário $capacityLabel",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    backpackIndicators.sortedBy { it.tier }.forEach { indicator ->
                        Text(
                            text = "🎒${indicator.tier}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (indicator.equipped) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            },
                            fontWeight = if (indicator.equipped) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GameDropdownSelect(
                    label = "Ordenar: $sortModeLabel",
                    options = listOf(
                        GameSelectOption("TYPE", "Tipo"),
                        GameSelectOption("RARITY", "Raridade"),
                        GameSelectOption("VALUE", "Valor")
                    ),
                    onSelect = { option -> onSortSelected(option.key) },
                    width = 128.dp
                )
            }
        }
        if (items.isEmpty()) {
            Text("Inventário vazio.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { item ->
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
