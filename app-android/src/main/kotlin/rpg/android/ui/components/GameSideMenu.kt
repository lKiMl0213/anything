package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

data class SideMenuItem(
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun GameSideMenu(
    items: List<SideMenuItem>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GameUiTokens.panelSpacing)
    ) {
        items.forEach { item ->
            GamePrimaryButton(label = item.label, onClick = item.onClick)
        }
    }
}
