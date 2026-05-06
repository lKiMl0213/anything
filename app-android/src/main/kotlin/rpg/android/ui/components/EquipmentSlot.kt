package rpg.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EquipmentSlot(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    fillWidth: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    slotSize: Dp = 62.dp
) {
    val slotModifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    val isBlocked = value.contains("bloqueado", ignoreCase = true)
    val isEmpty = value == "-" || value.isBlank()
    val visualAlpha = when {
        isEmpty -> 0.52f
        isBlocked -> 0.74f
        else -> 1f
    }
    val borderColor = when {
        isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
        isEmpty -> MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
    }
    val backgroundColor = when {
        isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        isEmpty -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }

    Surface(
        modifier = slotModifier.clickable(onClick = onClick),
        color = backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(GameUiTokens.slotCorner)
    ) {
        if (iconOnly) {
            Box(
                modifier = Modifier
                    .size(slotSize)
                    .alpha(visualAlpha),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isBlocked -> "\uD83D\uDD12"
                        icon.isNullOrBlank() -> "\u2B1C"
                        else -> icon
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(if (isEmpty) 0.70f else 1f)
                )
                if (!isEmpty && !isBlocked) {
                    Text(
                        text = "\u2022",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 58.dp else 44.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart
            ) {
                if (compact) {
                    Text(
                        text = buildString {
                            if (!icon.isNullOrBlank()) {
                                append(icon)
                                append('\n')
                            }
                            append(title)
                            append('\n')
                            append(value)
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "$title: $value",
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
