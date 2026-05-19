package rpg.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import rpg.android.R

private const val BONFIRE_SOURCE_WIDTH_PX = 401f
private const val BONFIRE_SOURCE_HEIGHT_PX = 282f
private const val FIRE_HALF_WIDTH_PX = 20f
private const val FIRE_HEIGHT_UP_PX = 15f
private const val TAU = 6.2831855f

@Composable
fun BonfireAnimated(
    modifier: Modifier = Modifier,
    fireWidthFraction: Float = 0.56f
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(fireWidthFraction)
                .aspectRatio(BONFIRE_SOURCE_WIDTH_PX / BONFIRE_SOURCE_HEIGHT_PX)
        ) {
            // Centro calibrado via leitura da imagem (região brilhante próxima de 320x190).
            val flameCenterX = maxWidth * (320f / BONFIRE_SOURCE_WIDTH_PX)
            val flameCenterY = maxHeight * (190f / BONFIRE_SOURCE_HEIGHT_PX)
            Image(
                painter = painterResource(id = R.drawable.bonfire_idle),
                contentDescription = "Fogueira",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            BonfireOverlay(
                centerX = flameCenterX,
                centerY = flameCenterY,
                canvasWidth = maxWidth,
                canvasHeight = maxHeight
            )
        }
    }
}

@Composable
private fun BonfireOverlay(
    centerX: Dp,
    centerY: Dp,
    canvasWidth: Dp,
    canvasHeight: Dp
) {
    val transition = rememberInfiniteTransition(label = "bonfireOverlay")
    val glowPulse by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val lightOscillation by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1020, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lightOscillation"
    )

    val glowSize = (canvasWidth * 0.28f).coerceIn(74.dp, 124.dp)
    val fireHalfWidth = canvasWidth * (FIRE_HALF_WIDTH_PX / BONFIRE_SOURCE_WIDTH_PX)
    val fireCoreHeight = canvasHeight * (FIRE_HEIGHT_UP_PX / BONFIRE_SOURCE_HEIGHT_PX)

    // Mantido conforme solicitado: área luminosa permanece como estava.
    Box(
        modifier = Modifier
            .offset(
                x = centerX - (glowSize / 2) + (2.dp * lightOscillation),
                y = centerY - (glowSize * 0.58f)
            )
            .size(glowSize)
            .alpha((0.08f + glowPulse).coerceIn(0f, 0.40f))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE0A0),
                        Color(0xBBFF9800),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )

    OrganicSmokeLayer(
        centerX = centerX,
        centerY = centerY,
        fireHalfWidth = fireHalfWidth,
        fireCoreHeight = fireCoreHeight
    )
}

@Composable
private fun OrganicSmokeLayer(
    centerX: Dp,
    centerY: Dp,
    fireHalfWidth: Dp,
    fireCoreHeight: Dp
) {
    val transition = rememberInfiniteTransition(label = "organicSmoke")
    val riseA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "smokeA"
    )
    val riseB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3700, delayMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "smokeB"
    )
    val riseC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4100, delayMillis = 980, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "smokeC"
    )

    // ~50% mais volume visual e densidade da fumaça.
    val smokeWidth = fireHalfWidth * 4.2f
    val smokeHeight = fireCoreHeight * 13.2f

    Canvas(
        modifier = Modifier
            .offset(
                x = centerX - (smokeWidth / 2),
                y = centerY - fireCoreHeight - smokeHeight
            )
            .width(smokeWidth)
            .height(smokeHeight)
    ) {
        drawSmokePlume(progress = riseA, driftFactor = -0.18f, baseAlpha = 0.23f)
        drawSmokePlume(progress = riseB, driftFactor = 0.10f, baseAlpha = 0.21f)
        drawSmokePlume(progress = riseC, driftFactor = 0.20f, baseAlpha = 0.20f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmokePlume(
    progress: Float,
    driftFactor: Float,
    baseAlpha: Float
) {
    val p = progress.coerceIn(0f, 1f)
    val eased = p * p * (3f - 2f * p)
    val drift = size.width * driftFactor * eased
    val centerX = size.width * 0.5f + drift
    val centerY = size.height * 0.92f - (size.height * eased)
    val radius = size.minDimension * (0.10f + eased * 0.18f)
    val alpha = (baseAlpha * (1f - eased)).coerceIn(0f, 0.18f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE7EBF1).copy(alpha = alpha),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = radius
        ),
        radius = radius,
        center = Offset(centerX, centerY)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFDCE2EA).copy(alpha = alpha * 0.76f),
                Color.Transparent
            ),
            center = Offset(centerX + radius * 0.30f, centerY - radius * 0.42f),
            radius = radius * 0.78f
        ),
        radius = radius * 0.78f,
        center = Offset(centerX + radius * 0.30f, centerY - radius * 0.42f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFF2F5FA).copy(alpha = alpha * 0.52f),
                Color.Transparent
            ),
            center = Offset(centerX - radius * 0.35f, centerY - radius * 0.70f),
            radius = radius * 0.62f
        ),
        radius = radius * 0.62f,
        center = Offset(centerX - radius * 0.35f, centerY - radius * 0.70f)
    )
}
