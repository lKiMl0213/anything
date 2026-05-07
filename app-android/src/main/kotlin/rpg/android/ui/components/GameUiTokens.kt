package rpg.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import rpg.android.ui.scale.LocalGameUiSettings

object GameUiTokens {
    val screenPadding: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.screenPadding

    val panelPadding: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.panelPadding

    val panelSpacing: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.panelSpacing

    val footerSpacing: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.footerSpacing

    val buttonMinHeight: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.buttonMinHeight

    val infoButtonHeight: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.infoButtonHeight

    val bottomNavHeight: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.bottomNavHeight

    val panelCorner: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.panelCorner

    val buttonCorner: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.buttonCorner

    val slotCorner: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.slotCorner

    val panelMaxWidth: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.panelMaxWidth

    val buttonMaxWidth: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.buttonMaxWidth

    val compactSelectWidth: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.compactSelectWidth

    val iconButtonTouchSize: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.iconButtonTouchSize

    val backIconTouchSize: Dp
        @Composable get() = LocalGameUiSettings.current.metrics.backIconTouchSize

    val iconButtonFontSize: TextUnit
        @Composable get() = LocalGameUiSettings.current.metrics.iconButtonFontSize

    val bottomNavIconSize: TextUnit
        @Composable get() = LocalGameUiSettings.current.metrics.bottomNavIconSize

    val titleTextSize: TextUnit
        @Composable get() = LocalGameUiSettings.current.metrics.titleTextSize

    val bodyTextSize: TextUnit
        @Composable get() = LocalGameUiSettings.current.metrics.bodyTextSize

    val labelTextSize: TextUnit
        @Composable get() = LocalGameUiSettings.current.metrics.labelTextSize

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
