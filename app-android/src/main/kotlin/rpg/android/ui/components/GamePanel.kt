package rpg.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = GameUiTokens.panelMaxWidth),
        color = GameUiTokens.panelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(GameUiTokens.panelCorner),
        border = BorderStroke(1.dp, GameUiTokens.panelBorderColor())
    ) {
        Column(
            modifier = Modifier.padding(GameUiTokens.panelPadding)
        ) {
            title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = GameUiTokens.titleTextSize),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun GameInfoPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GamePanel(
        modifier = modifier,
        title = title,
        content = content
    )
}
