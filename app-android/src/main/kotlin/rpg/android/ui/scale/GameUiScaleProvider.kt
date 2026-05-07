package rpg.android.ui.scale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Stable
data class GameUiSettings(
    val scale: GameUiScale,
    val metrics: GameUiMetrics
)

val LocalGameUiSettings = staticCompositionLocalOf {
    GameUiSettings(
        scale = GameUiScale.default,
        metrics = GameUiMetrics.forScale(GameUiScale.default)
    )
}

@Composable
fun ProvideGameUiScale(
    scale: GameUiScale,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val clampedSystemFontScale = baseDensity.fontScale.coerceIn(0.92f, 1.10f)
    val appliedFontScale = (clampedSystemFontScale * scale.densityFontFactor).coerceIn(0.86f, 1.22f)
    val overriddenDensity = remember(baseDensity.density, appliedFontScale) {
        Density(
            density = baseDensity.density,
            fontScale = appliedFontScale
        )
    }
    val settings = remember(scale) {
        GameUiSettings(
            scale = scale,
            metrics = GameUiMetrics.forScale(scale)
        )
    }

    CompositionLocalProvider(
        LocalDensity provides overriddenDensity,
        LocalGameUiSettings provides settings
    ) {
        content()
    }
}

