package rpg.android.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

fun interface GameAudioController {
    fun play(effect: SoundEffect)
}

private val NoOpAudioController = GameAudioController { }

val LocalGameAudioController = staticCompositionLocalOf<GameAudioController> {
    NoOpAudioController
}

@Composable
fun ProvideGameAudioController(
    controller: GameAudioController,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGameAudioController provides controller,
        content = content
    )
}

