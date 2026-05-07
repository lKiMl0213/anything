package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GameButtonTone {
    DEFAULT,
    SUCCESS,
    ALERT,
    LOCKED
}

enum class GameButtonDensity {
    COMPACT,
    INFO
}

@Composable
fun GamePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: GameButtonTone = GameButtonTone.DEFAULT,
    density: GameButtonDensity = GameButtonDensity.COMPACT
) {
    val containerColor = when (tone) {
        GameButtonTone.DEFAULT -> GameUiTokens.defaultButtonColor()
        GameButtonTone.SUCCESS -> GameUiTokens.successButtonColor()
        GameButtonTone.ALERT -> GameUiTokens.alertButtonColor()
        GameButtonTone.LOCKED -> GameUiTokens.lockedButtonColor()
    }
    val disabledContainerColor = GameUiTokens.disabledButtonColor()
    val minHeight = when (density) {
        GameButtonDensity.COMPACT -> GameUiTokens.buttonMinHeight
        GameButtonDensity.INFO -> GameUiTokens.infoButtonHeight
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .heightIn(
                min = minHeight,
                max = if (density == GameButtonDensity.INFO) minHeight else Dp.Unspecified
            )
            .widthIn(max = GameUiTokens.buttonMaxWidth),
        shape = RoundedCornerShape(GameUiTokens.buttonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = GameUiTokens.buttonTextColor(),
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = GameUiTokens.buttonTextColor().copy(alpha = 0.65f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = GameUiTokens.labelTextSize),
            maxLines = if (density == GameButtonDensity.INFO) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = (GameUiTokens.labelTextSize.value + 2f).sp
        )
    }
}

@Composable
fun GameFooterActions(
    leftLabel: String? = null,
    rightLabel: String? = null,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GameUiTokens.buttonMinHeight + 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GameUiTokens.footerSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (leftLabel != null && onLeftClick != null) {
                GamePrimaryButton(
                    label = leftLabel,
                    onClick = onLeftClick,
                    enabled = leftEnabled,
                    modifier = Modifier.widthIn(min = 94.dp, max = GameUiTokens.buttonMaxWidth)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (rightLabel != null && onRightClick != null) {
                GamePrimaryButton(
                    label = rightLabel,
                    onClick = onRightClick,
                    enabled = rightEnabled,
                    modifier = Modifier.widthIn(min = 94.dp, max = GameUiTokens.buttonMaxWidth)
                )
            }
        }
    }
}

@Composable
fun GameBackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FlatIconButton(
        icon = "\u2b05\ufe0f",
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        size = GameUiTokens.backIconTouchSize
    )
}

@Composable
fun GameIconActionButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp? = null
) {
    FlatIconButton(
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        size = size ?: GameUiTokens.iconButtonTouchSize
    )
}

@Composable
private fun FlatIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = GameUiTokens.iconButtonFontSize,
            fontWeight = FontWeight.Black
        )
    }
}
