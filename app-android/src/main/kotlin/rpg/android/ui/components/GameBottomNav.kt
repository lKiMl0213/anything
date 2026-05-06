package rpg.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class BottomNavItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val hasAlert: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun GameBottomNav(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GameUiTokens.bottomNavHeight)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(GameUiTokens.buttonCorner)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val background = when {
                item.selected && item.hasAlert -> MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                item.selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                item.hasAlert -> MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(GameUiTokens.buttonCorner))
                    .clickable(onClick = item.onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label,
                    color = if (item.selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
