package rpg.android.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import rpg.android.R
import rpg.android.state.CombatStatusEffectUi
import rpg.android.state.CombatUiState
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot

@Composable
fun CombatTouchScreen(
    state: CombatUiState,
    onAttack: () -> Unit,
    onEscape: () -> Unit,
    onUseItem: (String) -> Unit,
    autoContinueUnlocked: Boolean,
    autoContinueEnabled: Boolean,
    onAutoContinueChanged: (Boolean) -> Unit,
    autoPotionUnlocked: Boolean,
    autoPotionEnabled: Boolean,
    autoPotionThresholdPct: Int,
    onAutoPotionEnabledChanged: (Boolean) -> Unit,
    onAutoPotionThresholdChanged: (Int) -> Unit,
    onSelectedConsumableChanged: (String) -> Unit
) {
    var selectedItemIndex by remember(state.consumables) { mutableIntStateOf(0) }
    if (selectedItemIndex >= state.consumables.size) {
        selectedItemIndex = 0
    }
    val selectedConsumable = state.consumables.getOrNull(selectedItemIndex)
    LaunchedEffect(selectedConsumable?.itemId) {
        selectedConsumable?.itemId?.let(onSelectedConsumableChanged)
    }

    val enemyNameCompact = remember(state.enemyName) { stripStarSuffix(state.enemyName) }
    val visibleLogs = remember(state.logLines) {
        state.logLines
            .map(::sanitizeLogLine)
            .filter { it.isNotBlank() }
            .takeLast(16)
    }

    var previousEnemyHp by remember { mutableStateOf(state.enemyHp) }
    var previousPlayerHp by remember { mutableStateOf(state.playerHp) }
    var playerAdvanceTarget by remember { mutableStateOf(0.dp) }
    var enemyAdvanceTarget by remember { mutableStateOf(0.dp) }
    var enemyShakeTarget by remember { mutableStateOf(0.dp) }
    var playerShakeTarget by remember { mutableStateOf(0.dp) }
    var enemyFlashTarget by remember { mutableFloatStateOf(0f) }
    var playerFlashTarget by remember { mutableFloatStateOf(0f) }

    val playerAdvance by animateDpAsState(
        targetValue = playerAdvanceTarget,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "playerAdvance"
    )
    val enemyAdvance by animateDpAsState(
        targetValue = enemyAdvanceTarget,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "enemyAdvance"
    )
    val enemyShake by animateDpAsState(
        targetValue = enemyShakeTarget,
        animationSpec = tween(durationMillis = 70),
        label = "enemyShake"
    )
    val playerShake by animateDpAsState(
        targetValue = playerShakeTarget,
        animationSpec = tween(durationMillis = 70),
        label = "playerShake"
    )
    val enemyFlash by animateFloatAsState(
        targetValue = enemyFlashTarget.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
        label = "enemyFlash"
    )
    val playerFlash by animateFloatAsState(
        targetValue = playerFlashTarget.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
        label = "playerFlash"
    )
    val enemyFade by animateFloatAsState(
        targetValue = if (state.enemyHp <= 0.0) 0.15f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "enemyFade"
    )
    val playerFade by animateFloatAsState(
        targetValue = if (state.playerHp <= 0.0) 0.20f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "playerFade"
    )

    LaunchedEffect(state.enemyHp) {
        if (state.enemyHp + 0.001 < previousEnemyHp) {
            playerAdvanceTarget = 18.dp
            enemyFlashTarget = 0.32f
            enemyShakeTarget = 7.dp
            delay(70)
            enemyShakeTarget = (-7).dp
            delay(70)
            enemyShakeTarget = 5.dp
            delay(60)
            playerAdvanceTarget = 0.dp
            enemyShakeTarget = 0.dp
            enemyFlashTarget = 0f
        }
        previousEnemyHp = state.enemyHp
    }

    LaunchedEffect(state.playerHp) {
        if (state.playerHp + 0.001 < previousPlayerHp) {
            enemyAdvanceTarget = (-14).dp
            playerFlashTarget = 0.30f
            playerShakeTarget = 7.dp
            delay(70)
            playerShakeTarget = (-7).dp
            delay(70)
            playerShakeTarget = 5.dp
            delay(60)
            enemyAdvanceTarget = 0.dp
            playerShakeTarget = 0.dp
            playerFlashTarget = 0f
        }
        previousPlayerHp = state.playerHp
    }

    val darkTheme = isSystemInDarkTheme()
    val primaryTextColor = if (darkTheme) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (darkTheme) Color(0xFFE7EDF7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
    val subduedTextColor = if (darkTheme) Color(0xFFCCD6E4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)

    GameScreenRoot(backgroundRes = R.drawable.bg_combat) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val spriteEnemyWidth = (maxWidth * 0.33f).coerceIn(108.dp, 204.dp)
            val spritePlayerWidth = (maxWidth * 0.46f).coerceIn(142.dp, 286.dp)
            val playerInfoWidth = (maxWidth * 0.46f).coerceIn(152.dp, 236.dp)
            val playerInfoBottom = ((spritePlayerWidth * 0.84f) + 26.dp).coerceIn(138.dp, 248.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.66f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.30f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    if (autoContinueUnlocked) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Auto Continuar",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
                            )
                            Switch(
                                checked = autoContinueEnabled,
                                onCheckedChange = onAutoContinueChanged,
                                modifier = Modifier.size(width = 40.dp, height = 24.dp)
                            )
                        }
                    }

                    if (state.introLines.isNotEmpty()) {
                        Text(
                            text = sanitizeLogLine(state.introLines.first()),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp, start = 54.dp, end = 54.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor,
                            maxLines = 1
                        )
                    }

                    CompactEnemyStatusCard(
                        enemyName = enemyNameCompact,
                        enemyStars = state.enemyStars,
                        enemyHp = state.enemyHp,
                        enemyHpMax = state.enemyHpMax,
                        enemyAtbProgress = state.enemyAtbProgress,
                        statusEffects = state.enemyStatusEffects,
                        darkTheme = darkTheme,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        subduedTextColor = subduedTextColor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 34.dp, end = 14.dp)
                    )

                    BattleSprite(
                        spriteRes = R.drawable.generic_enemy_model,
                        contentDescription = "Inimigo",
                        width = spriteEnemyWidth,
                        alpha = enemyFade,
                        xOffset = enemyAdvance + enemyShake,
                        flashAlpha = enemyFlash,
                        statusEffects = state.enemyStatusEffects,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 136.dp, end = 24.dp)
                    )

                    BattleSprite(
                        spriteRes = R.drawable.generic_player_model,
                        contentDescription = "Jogador",
                        width = spritePlayerWidth,
                        alpha = playerFade,
                        xOffset = playerAdvance + playerShake,
                        flashAlpha = playerFlash,
                        statusEffects = state.playerStatusEffects,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 28.dp)
                    )

                    CompactPlayerStatusCard(
                        playerName = state.playerName,
                        playerHp = state.playerHp,
                        playerHpMax = state.playerHpMax,
                        playerMp = state.playerMp,
                        playerMpMax = state.playerMpMax,
                        playerAtbProgress = state.playerAtbProgress,
                        statusEffects = state.playerStatusEffects,
                        activeEffectName = state.activeEffectName,
                        activeEffectRemainingSeconds = state.activeEffectRemainingSeconds,
                        darkTheme = darkTheme,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        subduedTextColor = subduedTextColor,
                        width = playerInfoWidth,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 12.dp, bottom = playerInfoBottom)
                    )
                }

                BattleActionsPanel(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxWidth(),
                    playerReady = state.playerReady,
                    hasConsumables = state.consumables.isNotEmpty(),
                    selectedConsumableLabel = selectedConsumable?.label,
                    onSelectPrevConsumable = {
                        if (state.consumables.isNotEmpty()) {
                            selectedItemIndex = (selectedItemIndex - 1 + state.consumables.size) % state.consumables.size
                        }
                    },
                    onSelectNextConsumable = {
                        if (state.consumables.isNotEmpty()) {
                            selectedItemIndex = (selectedItemIndex + 1) % state.consumables.size
                        }
                    },
                    onAttack = onAttack,
                    onUseItem = { selectedConsumable?.let { onUseItem(it.itemId) } },
                    onEscape = onEscape,
                    autoPotionUnlocked = autoPotionUnlocked,
                    autoPotionEnabled = autoPotionEnabled,
                    autoPotionThresholdPct = autoPotionThresholdPct,
                    onAutoPotionEnabledChanged = onAutoPotionEnabledChanged,
                    onAutoPotionThresholdChanged = onAutoPotionThresholdChanged,
                    darkTheme = darkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    logs = visibleLogs
                )
            }
        }
    }
}

@Composable
private fun CompactEnemyStatusCard(
    enemyName: String,
    enemyStars: Int,
    enemyHp: Double,
    enemyHpMax: Double,
    enemyAtbProgress: Float,
    statusEffects: List<CombatStatusEffectUi>,
    darkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    subduedTextColor: Color,
    modifier: Modifier = Modifier
) {
    val ready = enemyAtbProgress >= 0.999f
    val shakeOffset = readyShakeOffset(ready, "enemyCard")
    val glowPulse = readyGlowPulse(ready, "enemyCard")
    val borderColor = if (ready) {
        Color(0xFFFFE082).copy(alpha = 0.45f + glowPulse * 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
    }
    Column(
        modifier = modifier
            .offset(x = shakeOffset)
            .width(186.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.94f else 0.88f))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = enemyName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor,
            maxLines = 1
        )
        if (enemyStars > 0) {
            Text(
                text = "\u2B50".repeat(enemyStars.coerceIn(1, 7)),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFE082)
            )
        }
        if (statusEffects.isNotEmpty()) {
            StatusIconsLine(
                statusEffects = statusEffects,
                textColor = secondaryTextColor
            )
        }
        CompactBar(
            label = "HP",
            current = enemyHp,
            max = enemyHpMax,
            fillColor = Color(0xFFE57373),
            labelColor = secondaryTextColor,
            valueColor = subduedTextColor
        )
        ReadyAtbBar(
            label = "ATB",
            progress = enemyAtbProgress,
            fillColor = Color(0xFFFFA726),
            labelColor = secondaryTextColor,
            darkTheme = darkTheme
        )
    }
}

@Composable
private fun CompactPlayerStatusCard(
    playerName: String,
    playerHp: Double,
    playerHpMax: Double,
    playerMp: Double,
    playerMpMax: Double,
    playerAtbProgress: Float,
    statusEffects: List<CombatStatusEffectUi>,
    activeEffectName: String?,
    activeEffectRemainingSeconds: Int,
    darkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    subduedTextColor: Color,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val ready = playerAtbProgress >= 0.999f
    val shakeOffset = readyShakeOffset(ready, "playerCard")
    val glowPulse = readyGlowPulse(ready, "playerCard")
    val borderColor = if (ready) {
        Color(0xFFFFE082).copy(alpha = 0.45f + glowPulse * 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.50f)
    }
    Column(
        modifier = modifier
            .offset(x = shakeOffset)
            .width(width)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.94f else 0.88f))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = playerName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor,
            maxLines = 1
        )
        if (statusEffects.isNotEmpty()) {
            StatusIconsLine(
                statusEffects = statusEffects,
                textColor = secondaryTextColor
            )
        }
        CompactBar(
            label = "HP",
            current = playerHp,
            max = playerHpMax,
            fillColor = Color(0xFF4CAF50),
            labelColor = secondaryTextColor,
            valueColor = subduedTextColor
        )
        CompactBar(
            label = "MP",
            current = playerMp,
            max = playerMpMax,
            fillColor = Color(0xFF42A5F5),
            labelColor = secondaryTextColor,
            valueColor = subduedTextColor
        )
        ReadyAtbBar(
            label = "ATB",
            progress = playerAtbProgress,
            fillColor = Color(0xFFFFC669),
            labelColor = secondaryTextColor,
            darkTheme = darkTheme
        )
        activeEffectName
            ?.takeIf { it.isNotBlank() && activeEffectRemainingSeconds > 0 }
            ?.let { effectName ->
                Text(
                    text = "$effectName (${activeEffectRemainingSeconds}s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1
                )
            }
    }
}

@Composable
private fun StatusIconsLine(
    statusEffects: List<CombatStatusEffectUi>,
    textColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        statusEffects
            .sortedByDescending { it.remainingSeconds }
            .take(4)
            .forEach { status ->
                Text(
                    text = "${status.icon} ${status.remainingSeconds}s",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
    }
}

@Composable
private fun BattleSprite(
    @DrawableRes spriteRes: Int,
    contentDescription: String,
    width: Dp,
    alpha: Float,
    xOffset: Dp,
    flashAlpha: Float,
    statusEffects: List<CombatStatusEffectUi>,
    modifier: Modifier = Modifier
) {
    val overlayStatus = remember(statusEffects) { resolveOverlayStatus(statusEffects) }
    val overlayJitter = paralyzeOverlayJitter(overlayStatus)
    Box(
        modifier = modifier
            .offset(x = xOffset + overlayJitter)
            .width(width)
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.60f)
                .height(14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
        )
        Image(
            painter = painterResource(id = spriteRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentScale = ContentScale.Fit
        )
        BattleEffectOverlay(
            spriteRes = spriteRes,
            status = overlayStatus,
            damageFlashAlpha = flashAlpha
        )
    }
}

@Composable
private fun BattleEffectOverlay(
    @DrawableRes spriteRes: Int,
    status: OverlayStatus,
    damageFlashAlpha: Float
) {
    if (status == OverlayStatus.NONE && damageFlashAlpha <= 0f) return
    val transition = rememberInfiniteTransition(label = "battleEffect")
    val pulse by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 960, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "effectPulse"
    )
    val flicker by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 240),
            repeatMode = RepeatMode.Reverse
        ),
        label = "effectFlicker"
    )

    val tintColor = when (status) {
        OverlayStatus.BURNING -> Color(0xFFFF7043)
        OverlayStatus.FROZEN -> Color(0xFF81D4FA)
        OverlayStatus.POISONED -> Color(0xFF66BB6A)
        OverlayStatus.PARALYZED -> Color(0xFFFFEE58)
        OverlayStatus.SLOW -> Color(0xFF9575CD)
        OverlayStatus.BLEEDING -> Color(0xFFE57373)
        OverlayStatus.WEAKNESS -> Color(0xFFB0BEC5)
        OverlayStatus.MARKED -> Color(0xFF4FC3F7)
        OverlayStatus.NONE -> Color.Transparent
    }
    val statusAlpha = when (status) {
        OverlayStatus.BURNING -> 0.18f + flicker * 0.20f
        OverlayStatus.FROZEN -> 0.20f + pulse * 0.12f
        OverlayStatus.POISONED -> 0.20f + pulse * 0.14f
        OverlayStatus.PARALYZED -> 0.16f + flicker * 0.18f
        OverlayStatus.SLOW -> 0.18f + pulse * 0.10f
        OverlayStatus.BLEEDING -> 0.16f + pulse * 0.10f
        OverlayStatus.WEAKNESS -> 0.14f + pulse * 0.08f
        OverlayStatus.MARKED -> 0.14f + pulse * 0.12f
        OverlayStatus.NONE -> 0f
    }.coerceIn(0f, 0.68f)

    if (status != OverlayStatus.NONE) {
        Image(
            painter = painterResource(id = spriteRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(statusAlpha),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tintColor, BlendMode.SrcAtop)
        )
        StatusParticleOverlay(status = status, pulse = pulse, flicker = flicker)
    }

    if (damageFlashAlpha > 0f) {
        Image(
            painter = painterResource(id = spriteRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(damageFlashAlpha.coerceIn(0f, 0.6f)),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color(0xFFFF3D3D), BlendMode.SrcAtop)
        )
    }
}

@Composable
private fun StatusParticleOverlay(
    status: OverlayStatus,
    pulse: Float,
    flicker: Float
) {
    val particleColor = when (status) {
        OverlayStatus.POISONED -> Color(0xFF81C784)
        OverlayStatus.BURNING -> Color(0xFFFFB74D)
        OverlayStatus.FROZEN -> Color(0xFFB3E5FC)
        OverlayStatus.PARALYZED -> Color(0xFFFFF59D)
        else -> Color.Transparent
    }
    if (particleColor == Color.Transparent) return
    repeat(3) { index ->
        val base = 0.15f + (index * 0.25f)
        val yFactor = (base + pulse * 0.35f).coerceIn(0.08f, 0.82f)
        val xFactor = (0.30f + index * 0.20f).coerceIn(0.18f, 0.82f)
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (xFactor * 100).dp, y = (yFactor * 90).dp)
                    .size((4 + index).dp)
                    .clip(CircleShape)
                    .background(particleColor.copy(alpha = 0.30f + flicker * 0.20f))
            )
        }
    }
}

@Composable
private fun paralyzeOverlayJitter(status: OverlayStatus): Dp {
    if (status != OverlayStatus.PARALYZED) return 0.dp
    val transition = rememberInfiniteTransition(label = "paralyzeJitter")
    val offset by transition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90),
            repeatMode = RepeatMode.Reverse
        ),
        label = "paralyzeJitterOffset"
    )
    return offset.dp
}

private enum class OverlayStatus {
    NONE,
    BURNING,
    FROZEN,
    POISONED,
    PARALYZED,
    BLEEDING,
    WEAKNESS,
    SLOW,
    MARKED
}

private fun resolveOverlayStatus(statusEffects: List<CombatStatusEffectUi>): OverlayStatus {
    if (statusEffects.isEmpty()) return OverlayStatus.NONE
    val active = statusEffects.map { it.typeKey.uppercase() }.toSet()
    return when {
        "PARALYZED" in active -> OverlayStatus.PARALYZED
        "FROZEN" in active -> OverlayStatus.FROZEN
        "BURNING" in active -> OverlayStatus.BURNING
        "POISONED" in active -> OverlayStatus.POISONED
        "SLOW" in active -> OverlayStatus.SLOW
        "BLEEDING" in active -> OverlayStatus.BLEEDING
        "WEAKNESS" in active -> OverlayStatus.WEAKNESS
        "MARKED" in active -> OverlayStatus.MARKED
        else -> OverlayStatus.NONE
    }
}

@Composable
private fun readyShakeOffset(isReady: Boolean, key: String): Dp {
    var target by remember(key) { mutableStateOf(0.dp) }
    val offset by animateDpAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 60),
        label = "${key}ReadyShake"
    )
    LaunchedEffect(isReady, key) {
        if (isReady) {
            target = 2.dp
            delay(40)
            target = (-2).dp
            delay(40)
            target = 1.dp
            delay(40)
            target = 0.dp
        } else {
            target = 0.dp
        }
    }
    return offset
}

@Composable
private fun readyGlowPulse(isReady: Boolean, key: String): Float {
    val transition = rememberInfiniteTransition(label = "${key}ReadyPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${key}PulseValue"
    )
    return if (isReady) pulse else 0f
}

@Composable
private fun BattleActionsPanel(
    modifier: Modifier,
    playerReady: Boolean,
    hasConsumables: Boolean,
    selectedConsumableLabel: String?,
    onSelectPrevConsumable: () -> Unit,
    onSelectNextConsumable: () -> Unit,
    onAttack: () -> Unit,
    onUseItem: () -> Unit,
    onEscape: () -> Unit,
    autoPotionUnlocked: Boolean,
    autoPotionEnabled: Boolean,
    autoPotionThresholdPct: Int,
    onAutoPotionEnabledChanged: (Boolean) -> Unit,
    onAutoPotionThresholdChanged: (Int) -> Unit,
    darkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    logs: List<String>
) {
    val historyScrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) 0.92f else 0.86f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val stackPanels = maxWidth < 340.dp
        if (stackPanels) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BattleHistoryPanel(
                    logs = logs,
                    scrollState = historyScrollState,
                    darkTheme = darkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f)
                )
                BattleActionsColumn(
                    playerReady = playerReady,
                    hasConsumables = hasConsumables,
                    selectedConsumableLabel = selectedConsumableLabel,
                    onSelectPrevConsumable = onSelectPrevConsumable,
                    onSelectNextConsumable = onSelectNextConsumable,
                    onAttack = onAttack,
                    onUseItem = onUseItem,
                    onEscape = onEscape,
                    autoPotionUnlocked = autoPotionUnlocked,
                    autoPotionEnabled = autoPotionEnabled,
                    autoPotionThresholdPct = autoPotionThresholdPct,
                    onAutoPotionEnabledChanged = onAutoPotionEnabledChanged,
                    onAutoPotionThresholdChanged = onAutoPotionThresholdChanged,
                    darkTheme = darkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BattleHistoryPanel(
                    logs = logs,
                    scrollState = historyScrollState,
                    darkTheme = darkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                )
                BattleActionsColumn(
                    playerReady = playerReady,
                    hasConsumables = hasConsumables,
                    selectedConsumableLabel = selectedConsumableLabel,
                    onSelectPrevConsumable = onSelectPrevConsumable,
                    onSelectNextConsumable = onSelectNextConsumable,
                    onAttack = onAttack,
                    onUseItem = onUseItem,
                    onEscape = onEscape,
                    autoPotionUnlocked = autoPotionUnlocked,
                    autoPotionEnabled = autoPotionEnabled,
                    autoPotionThresholdPct = autoPotionThresholdPct,
                    onAutoPotionEnabledChanged = onAutoPotionEnabledChanged,
                    onAutoPotionThresholdChanged = onAutoPotionThresholdChanged,
                    darkTheme = darkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun BattleHistoryPanel(
    logs: List<String>,
    scrollState: androidx.compose.foundation.ScrollState,
    darkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(logs.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.42f else 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Histórico",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = primaryTextColor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (logs.isEmpty()) {
                Text("-", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
            } else {
                logs.forEach { line ->
                    Text(
                        text = "- $line",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BattleActionsColumn(
    playerReady: Boolean,
    hasConsumables: Boolean,
    selectedConsumableLabel: String?,
    onSelectPrevConsumable: () -> Unit,
    onSelectNextConsumable: () -> Unit,
    onAttack: () -> Unit,
    onUseItem: () -> Unit,
    onEscape: () -> Unit,
    autoPotionUnlocked: Boolean,
    autoPotionEnabled: Boolean,
    autoPotionThresholdPct: Int,
    onAutoPotionEnabledChanged: (Boolean) -> Unit,
    onAutoPotionThresholdChanged: (Int) -> Unit,
    darkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.36f else 0.30f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "Ações",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = primaryTextColor
        )
        GamePrimaryButton(
            label = "Atacar",
            onClick = onAttack,
            enabled = playerReady,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamePrimaryButton(
                label = "Item",
                onClick = onUseItem,
                enabled = playerReady && hasConsumables,
                modifier = Modifier.weight(1f)
            )
            GamePrimaryButton(
                label = "Fugir",
                onClick = onEscape,
                enabled = playerReady,
                modifier = Modifier.weight(1f)
            )
        }

        if (autoPotionUnlocked) {
            GamePrimaryButton(
                label = if (autoPotionEnabled) "Auto Poção: ON" else "Auto Poção: OFF",
                onClick = { onAutoPotionEnabledChanged(!autoPotionEnabled) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (hasConsumables) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamePrimaryButton(
                    label = "<",
                    onClick = onSelectPrevConsumable,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = selectedConsumableLabel?.let { "Item: $it" } ?: "Item: -",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1
                )
                GamePrimaryButton(
                    label = ">",
                    onClick = onSelectNextConsumable,
                    modifier = Modifier.width(44.dp)
                )
            }
        }

        if (autoPotionUnlocked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GamePrimaryButton(
                    label = "-",
                    onClick = { onAutoPotionThresholdChanged((autoPotionThresholdPct - 5).coerceAtLeast(5)) },
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = "Auto Poção em ${autoPotionThresholdPct.coerceIn(5, 95)}%",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1
                )
                GamePrimaryButton(
                    label = "+",
                    onClick = { onAutoPotionThresholdChanged((autoPotionThresholdPct + 5).coerceAtMost(95)) },
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}
@Composable
private fun CompactBar(
    label: String,
    current: Double,
    max: Double,
    fillColor: Color,
    labelColor: Color,
    valueColor: Color
) {
    val value = ratio(current, max)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(22.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = fillColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${current.roundToInt()}/${max.coerceAtLeast(1.0).roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = valueColor
        )
    }
}

@Composable
private fun ReadyAtbBar(
    label: String,
    progress: Float,
    fillColor: Color,
    labelColor: Color,
    darkTheme: Boolean
) {
    val clamped = progress.coerceIn(0f, 1f)
    val ready = clamped >= 0.999f
    val glowPulse = readyGlowPulse(ready, "${label}Atb")
    val readyBorderColor = if (ready) {
        Color(0xFFFFF176).copy(alpha = 0.45f + glowPulse * 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$label ${(clamped * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.65f else 0.85f))
                .border(1.dp, readyBorderColor, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxSize()
                    .background(fillColor)
            )
        }
    }
}

private fun ratio(current: Double, max: Double): Float {
    val safeMax = max.coerceAtLeast(1.0)
    return (current / safeMax).toFloat().coerceIn(0f, 1f)
}

private fun stripStarSuffix(name: String): String {
    return name.replace(Regex("\\s*\\((\\u2B50+)\\)\\s*$"), "").trim()
}

private fun sanitizeLogLine(raw: String): String {
    return raw
        .replace(Regex("\\u001B\\[[;\\d]*m"), "")
        .replace(Regex("\\[(?:\\d{1,3};?)+m"), "")
        .trim()
}

