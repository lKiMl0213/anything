package rpg.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

object GameUiTokens {
    val screenPadding = 8.dp
    val panelPadding = 8.dp
    val panelSpacing = 6.dp
    val footerSpacing = 8.dp
    val buttonMinHeight = 40.dp
    val bottomNavHeight = 52.dp
    val panelCorner = 14.dp
    val buttonCorner = 12.dp
    val slotCorner = 12.dp

    @Composable
    fun overlayColor(): Color {
        val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return if (darkSurface) {
            Color.Black.copy(alpha = 0.45f)
        } else {
            Color.Black.copy(alpha = 0.20f)
        }
    }

    @Composable
    fun panelColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)

    @Composable
    fun panelBorderColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)

    @Composable
    fun defaultButtonColor(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun alertButtonColor(): Color = MaterialTheme.colorScheme.error.copy(alpha = 0.78f)

    @Composable
    fun lockedButtonColor(): Color = MaterialTheme.colorScheme.error.copy(alpha = 0.48f)

    @Composable
    fun buttonTextColor(): Color = MaterialTheme.colorScheme.onPrimary

    @Composable
    fun disabledButtonColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
}
