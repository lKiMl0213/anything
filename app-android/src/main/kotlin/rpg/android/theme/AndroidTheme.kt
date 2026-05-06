package rpg.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9EBBFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0F1B34),
    surface = androidx.compose.ui.graphics.Color(0xFF1A1D24),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF3F6FF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF252A33),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD5DCEC),
    background = androidx.compose.ui.graphics.Color(0xFF111319),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF3F6FF),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    outline = androidx.compose.ui.graphics.Color(0xFF7E8799)
)

private val LightScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF2A4C86),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surface = androidx.compose.ui.graphics.Color(0xFFF5F7FC),
    onSurface = androidx.compose.ui.graphics.Color(0xFF10141B),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE0E6F3),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF2F3545),
    background = androidx.compose.ui.graphics.Color(0xFFF0F3FA),
    onBackground = androidx.compose.ui.graphics.Color(0xFF0B1018),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    outline = androidx.compose.ui.graphics.Color(0xFF6F7787)
)

@Composable
fun AnythingRpgTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        content = content
    )
}
