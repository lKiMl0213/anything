package rpg.android.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton

@Composable
fun TutorialOverlay(
    state: TutorialOverlayState,
    onContinue: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var showSkipConfirm by remember { mutableStateOf(false) }
    val anchoredRect = TutorialAnchorRegistry.rectFor(state.target)
    val highlightRect = tutorialHighlightRect(
        target = state.target,
        size = rootSize,
        anchoredRect = anchoredRect
    )

    val cardPlacement = decideCardPlacement(
        rootSize = rootSize,
        highlightRect = highlightRect,
        hasPrimaryButton = state.primaryButtonLabel != null
    )
    val density = LocalDensity.current
    val safeTopInset = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val safeBottomInset = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    val safeRightInset = with(density) {
        WindowInsets.safeDrawing.getRight(this, LayoutDirection.Ltr).toDp()
    }
    val safeLeftInset = with(density) {
        WindowInsets.safeDrawing.getLeft(this, LayoutDirection.Ltr).toDp()
    }
    val placeSkipOnLeft = state.target == TutorialTarget.SETTINGS_BUTTON

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val overlay = Color.Black.copy(alpha = 0.70f)
            val rect = highlightRect
            if (rect == null) {
                drawRect(color = overlay)
            } else {
                if (rect.top > 0f) {
                    drawRect(
                        color = overlay,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, rect.top)
                    )
                }
                if (rect.bottom < size.height) {
                    drawRect(
                        color = overlay,
                        topLeft = Offset(0f, rect.bottom),
                        size = Size(size.width, size.height - rect.bottom)
                    )
                }
                if (rect.left > 0f) {
                    drawRect(
                        color = overlay,
                        topLeft = Offset(0f, rect.top),
                        size = Size(rect.left, rect.height)
                    )
                }
                if (rect.right < size.width) {
                    drawRect(
                        color = overlay,
                        topLeft = Offset(rect.right, rect.top),
                        size = Size(size.width - rect.right, rect.height)
                    )
                }
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.96f),
                    topLeft = Offset(rect.left, rect.top),
                    size = rect.size,
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3f)
                )
            }
        }

        Text(
            text = "Pular tutorial",
            color = Color.White,
            modifier = Modifier
                .align(if (placeSkipOnLeft) Alignment.TopStart else Alignment.TopEnd)
                .padding(
                    top = safeTopInset + 8.dp,
                    start = if (placeSkipOnLeft) safeLeftInset + 8.dp else 0.dp,
                    end = if (placeSkipOnLeft) 0.dp else safeRightInset + 8.dp
                )
                .background(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { showSkipConfirm = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )

        highlightRect?.let { rect ->
            val arrowSymbol = if (cardPlacement == TutorialCardPlacement.TOP) "\u2b06" else "\u2b07"
            val rawY = if (cardPlacement == TutorialCardPlacement.TOP) rect.bottom + 8f else rect.top - 30f
            val arrowY = rawY.coerceIn(8f, max(8f, (rootSize.height - 34).toFloat()))
            val arrowX = rect.center.x.coerceIn(18f, max(18f, (rootSize.width - 18).toFloat()))
            Text(
                text = arrowSymbol,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = (arrowX - 9f).roundToInt(),
                            y = arrowY.roundToInt()
                        )
                    }
            )
        }

        val helpCardModifier = if (cardPlacement == TutorialCardPlacement.TOP) {
            Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = safeTopInset + 70.dp,
                    start = 12.dp,
                    end = 12.dp
                )
                .fillMaxWidth(0.96f)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = safeBottomInset + 12.dp,
                    start = 12.dp,
                    end = 12.dp
                )
                .fillMaxWidth(0.96f)
        }

        Column(
            modifier = helpCardModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamePanel(title = state.title) {
                Text(
                    text = state.message,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (state.requiresUserAction) {
                    Text(
                        text = "Toque no local destacado.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                state.primaryButtonLabel?.let { label ->
                    GamePrimaryButton(
                        label = label,
                        onClick = {
                            if (state.step == TutorialStep.FINAL) {
                                onComplete()
                            } else {
                                onContinue()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.64f)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (showSkipConfirm) {
        GamePopup(
            title = "Pular tutorial",
            onDismiss = { showSkipConfirm = false },
            showCloseButton = false,
            modifier = Modifier.fillMaxWidth(0.90f)
        ) {
            Text(
                text = "Tem certeza que deseja pular o tutorial?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GamePrimaryButton(
                    label = "Continuar tutorial",
                    onClick = { showSkipConfirm = false },
                    modifier = Modifier.fillMaxWidth(0.80f)
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GamePrimaryButton(
                    label = "Pular",
                    onClick = {
                        showSkipConfirm = false
                        onSkip()
                    },
                    modifier = Modifier.fillMaxWidth(0.56f)
                )
            }
        }
    }
}

private fun tutorialHighlightRect(
    target: TutorialTarget,
    size: IntSize,
    anchoredRect: Rect?
): Rect? {
    if (size.width <= 0 || size.height <= 0) return null

    if (anchoredRect != null) {
        val boostPx = highlightBoostPx(target, size)
        return clampRectToRoot(
            rect = Rect(
                left = anchoredRect.left - boostPx,
                top = anchoredRect.top - boostPx,
                right = anchoredRect.right + boostPx,
                bottom = anchoredRect.bottom + boostPx
            ),
            size = size
        )
    }

    val spec = when (target) {
        TutorialTarget.HUB_INFO_PANEL -> TargetRect(0.50f, 0.19f, 0.96f, 0.30f)
        TutorialTarget.HUB_EXPLORE_BUTTON -> TargetRect(0.50f, 0.80f, 0.82f, 0.11f)
        TutorialTarget.EXPLORATION_AREAS_PANEL -> TargetRect(0.50f, 0.52f, 0.96f, 0.62f)
        TutorialTarget.BACK_BUTTON -> TargetRect(0.09f, 0.12f, 0.18f, 0.12f)
        TutorialTarget.BOTTOM_NAV_CHARACTER -> TargetRect(0.10f, 0.93f, 0.22f, 0.10f)
        TutorialTarget.CHARACTER_ACTION_PANEL -> TargetRect(0.50f, 0.63f, 0.62f, 0.15f)
        TutorialTarget.CHARACTER_EQUIPMENT_PANEL -> TargetRect(0.50f, 0.39f, 0.96f, 0.64f)
        TutorialTarget.BOTTOM_NAV_PRODUCTION -> TargetRect(0.30f, 0.93f, 0.22f, 0.10f)
        TutorialTarget.PRODUCTION_PANEL -> TargetRect(0.50f, 0.47f, 0.96f, 0.64f)
        TutorialTarget.CITY_PANEL -> TargetRect(0.50f, 0.50f, 0.96f, 0.64f)
        TutorialTarget.BOTTOM_NAV_CITY -> TargetRect(0.70f, 0.93f, 0.22f, 0.10f)
        TutorialTarget.PROGRESSION_PANEL -> TargetRect(0.50f, 0.50f, 0.96f, 0.64f)
        TutorialTarget.BOTTOM_NAV_PROGRESSION -> TargetRect(0.90f, 0.93f, 0.22f, 0.10f)
        TutorialTarget.BOTTOM_NAV_EXPLORE -> TargetRect(0.50f, 0.93f, 0.22f, 0.10f)
        TutorialTarget.SETTINGS_BUTTON -> TargetRect(0.91f, 0.07f, 0.16f, 0.12f)
        TutorialTarget.NONE -> return null
    }

    val widthPx = size.width * spec.widthPct
    val heightPx = size.height * spec.heightPct
    val centerX = size.width * spec.centerX
    val centerY = size.height * spec.centerY
    val genericPadX = size.width * 0.016f
    val genericPadY = size.height * 0.014f

    return clampRectToRoot(
        rect = Rect(
            left = centerX - widthPx / 2f - genericPadX,
            top = centerY - heightPx / 2f - genericPadY,
            right = centerX + widthPx / 2f + genericPadX,
            bottom = centerY + heightPx / 2f + genericPadY
        ),
        size = size
    )
}

private fun decideCardPlacement(
    rootSize: IntSize,
    highlightRect: Rect?,
    hasPrimaryButton: Boolean
): TutorialCardPlacement {
    if (highlightRect == null || rootSize.height <= 0) return TutorialCardPlacement.BOTTOM

    val estimatedCardHeightPx = if (hasPrimaryButton) {
        rootSize.height * 0.28f
    } else {
        rootSize.height * 0.22f
    }
    val marginPx = rootSize.height * 0.03f
    val spaceAbove = highlightRect.top
    val spaceBelow = rootSize.height.toFloat() - highlightRect.bottom
    val canFitTop = spaceAbove >= estimatedCardHeightPx + marginPx
    val canFitBottom = spaceBelow >= estimatedCardHeightPx + marginPx

    return when {
        canFitBottom && (!canFitTop || spaceBelow >= spaceAbove) -> TutorialCardPlacement.BOTTOM
        canFitTop -> TutorialCardPlacement.TOP
        spaceBelow >= spaceAbove -> TutorialCardPlacement.BOTTOM
        else -> TutorialCardPlacement.TOP
    }
}

private fun highlightBoostPx(target: TutorialTarget, size: IntSize): Float {
    val base = min(size.width, size.height) * 0.010f
    val bônus = when (target) {
        TutorialTarget.HUB_INFO_PANEL,
        TutorialTarget.EXPLORATION_AREAS_PANEL,
        TutorialTarget.CHARACTER_EQUIPMENT_PANEL,
        TutorialTarget.PRODUCTION_PANEL,
        TutorialTarget.CITY_PANEL,
        TutorialTarget.PROGRESSION_PANEL -> min(size.width, size.height) * 0.010f

        TutorialTarget.HUB_EXPLORE_BUTTON,
        TutorialTarget.BACK_BUTTON,
        TutorialTarget.BOTTOM_NAV_CHARACTER,
        TutorialTarget.BOTTOM_NAV_PRODUCTION,
        TutorialTarget.BOTTOM_NAV_CITY,
        TutorialTarget.BOTTOM_NAV_PROGRESSION,
        TutorialTarget.BOTTOM_NAV_EXPLORE,
        TutorialTarget.CHARACTER_ACTION_PANEL,
        TutorialTarget.SETTINGS_BUTTON -> min(size.width, size.height) * 0.006f

        TutorialTarget.NONE -> 0f
    }
    return base + bônus
}

private fun clampRectToRoot(rect: Rect, size: IntSize): Rect {
    val left = rect.left.coerceIn(0f, size.width.toFloat())
    val top = rect.top.coerceIn(0f, size.height.toFloat())
    val right = rect.right.coerceIn(0f, size.width.toFloat())
    val bottom = rect.bottom.coerceIn(0f, size.height.toFloat())
    return Rect(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom)
    )
}

private enum class TutorialCardPlacement {
    TOP,
    BOTTOM
}

private data class TargetRect(
    val centerX: Float,
    val centerY: Float,
    val widthPct: Float,
    val heightPct: Float
)

