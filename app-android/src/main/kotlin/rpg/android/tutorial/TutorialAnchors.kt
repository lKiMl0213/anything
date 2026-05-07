package rpg.android.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object TutorialAnchorRegistry {
    private val anchors = mutableStateMapOf<TutorialTarget, Rect>()

    fun update(target: TutorialTarget, rect: Rect) {
        anchors[target] = rect
    }

    fun remove(target: TutorialTarget) {
        anchors.remove(target)
    }

    @Composable
    fun rectFor(target: TutorialTarget): Rect? = anchors[target]
}

fun Modifier.tutorialAnchor(
    target: TutorialTarget,
    extraPadding: Dp = 6.dp
): Modifier = composed {
    val density = LocalDensity.current

    DisposableEffect(target) {
        onDispose { TutorialAnchorRegistry.remove(target) }
    }

    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        val padPx = with(density) { extraPadding.toPx() }
        TutorialAnchorRegistry.update(
            target = target,
            rect = Rect(
                left = bounds.left - padPx,
                top = bounds.top - padPx,
                right = bounds.right + padPx,
                bottom = bounds.bottom + padPx
            )
        )
    }
}
