package rpg.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class GameButtonTone {
    DEFAULT,
    ALERT,
    LOCKED
}

@Composable
fun GamePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: GameButtonTone = GameButtonTone.DEFAULT
) {
    val containerColor = when (tone) {
        GameButtonTone.DEFAULT -> GameUiTokens.defaultButtonColor()
        GameButtonTone.ALERT -> GameUiTokens.alertButtonColor()
        GameButtonTone.LOCKED -> GameUiTokens.lockedButtonColor()
    }
    val disabledContainerColor = GameUiTokens.disabledButtonColor()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = GameUiTokens.buttonMinHeight)
            .heightIn(min = GameUiTokens.buttonMinHeight),
        shape = RoundedCornerShape(GameUiTokens.buttonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = GameUiTokens.buttonTextColor(),
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = GameUiTokens.buttonTextColor().copy(alpha = 0.65f)
        )
    ) {
        Text(label)
    }
}

@Composable
fun GameFooterActions(
    leftLabel: String,
    rightLabel: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameUiTokens.footerSpacing)
    ) {
        GamePrimaryButton(
            label = leftLabel,
            onClick = onLeftClick,
            enabled = leftEnabled,
            modifier = Modifier.weight(1f)
        )
        GamePrimaryButton(
            label = rightLabel,
            onClick = onRightClick,
            enabled = rightEnabled,
            modifier = Modifier.weight(1f)
        )
    }
}
