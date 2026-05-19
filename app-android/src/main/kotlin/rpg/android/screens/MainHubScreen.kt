package rpg.android.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import rpg.android.R
import rpg.android.state.HubSkillUi
import rpg.android.state.MainHubUiModel
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.BonfireAnimated
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.GameTopHud
import rpg.android.ui.components.GameUiTokens
import rpg.android.tutorial.TutorialTarget
import rpg.android.tutorial.tutorialAnchor
@Composable
fun MainHubScreen(
    state: MainHubUiModel,
    hasProgressAlert: Boolean,
    versionLabel: String,
    onExplore: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenSkill: (HubSkillUi) -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit,
    onOpenGlobalBoss: () -> Unit
) {
    var selectedSkill by remember { mutableStateOf<HubSkillUi?>(null) }
    var showDailyPopup by remember { mutableStateOf(false) }
    var showNoAdsPopup by remember { mutableStateOf(false) }
    var dailyTimer by remember { mutableStateOf(formatDailyCountdown()) }

    LaunchedEffect(Unit) {
        while (true) {
            dailyTimer = formatDailyCountdown()
            delay(1_000L)
        }
    }

    GameScreenRoot(
        backgroundRes = R.drawable.bg_main_hub,
        bottomNav = {
            GameBottomNav(
                items = listOf(
                    BottomNavItem(
                        key = "character",
                        label = "Personagem",
                        icon = "\uD83E\uDDD9",
                        selected = false,
                        onClick = onOpenCharacter
                    ),
                    BottomNavItem(
                        key = "production",
                        label = "Produção",
                        icon = "\u2692",
                        selected = false,
                        onClick = onOpenProduction
                    ),
                    BottomNavItem(
                        key = "explore",
                        label = "Explorar",
                        icon = "\uD83E\uDDED",
                        selected = true,
                        onClick = {}
                    ),
                    BottomNavItem(
                        key = "city",
                        label = "Cidade",
                        icon = "\uD83C\uDFD9",
                        selected = false,
                        onClick = onOpenCity
                    ),
                    BottomNavItem(
                        key = "progress",
                        label = "Progresso",
                        icon = "\uD83D\uDCC8",
                        selected = false,
                        hasAlert = hasProgressAlert,
                        onClick = onOpenProgression
                    )
                )
            )
        },
        overlay = {
            MainHubFloatingActions(
                dailyTimer = dailyTimer,
                onDailyClick = { showDailyPopup = true },
                onNoAdsClick = { showNoAdsPopup = true },
                onOpenGlobalBoss = onOpenGlobalBoss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTarget.HUB_INFO_PANEL, extraPadding = 8.dp)
            ) {
                GameTopHud(
                    name = state.name,
                    premiumStatusLabel = state.premiumStatusLabel,
                    raceClassLabel = state.raceClassLabel,
                    levelXpLabel = state.levelXpLabel,
                    playerLevel = state.playerLevel,
                    playerXp = state.playerXp,
                    playerXpMax = state.playerXpMax,
                    currencyLabel = state.currencyLabel,
                    inventoryLabel = state.inventoryCapacityLabel,
                    debuffLabel = if (state.deathDebuffStacks > 0) {
                        "☠ x${state.deathDebuffStacks} (${formatMinutes(state.deathDebuffMinutes)}m)"
                    } else {
                        null
                    },
                    hpCurrent = state.hpCurrent,
                    hpMax = state.hpMax,
                    mpCurrent = state.mpCurrent,
                    mpMax = state.mpMax,
                    activeEffectName = state.activeEffectName,
                    activeEffectRemainingSeconds = state.activeEffectRemainingSeconds,
                    hpRegenPerMinute = state.hpRegenPerMinute,
                    mpRegenPerMinute = state.mpRegenPerMinute,
                    hpEtaSeconds = state.hpEtaSeconds,
                    mpEtaSeconds = state.mpEtaSeconds,
                    onRaceClassInfoClick = null
                )
            }
            GamePanel(title = "Habilidades de produção") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.skills.chunked(3).forEach { rowSkills ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSkills.forEach { skill ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                        .clickable { selectedSkill = skill }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${skill.symbol} nv${skill.level}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            repeat((3 - rowSkills.size).coerceAtLeast(0)) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .height(190.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BonfireAnimated(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            fireWidthFraction = 0.58f
                        )
                    }
                }
            }
            GamePrimaryButton(
                label = "EXPLORAR",
                onClick = onExplore,
                modifier = Modifier
                    .tutorialAnchor(TutorialTarget.HUB_EXPLORE_BUTTON, extraPadding = 8.dp)
                    .fillMaxWidth(0.78f)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = versionLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GameUiTokens.screenPadding),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = GameUiTokens.labelTextSize)
            )
        }
    }
    if (showDailyPopup) {
        GamePopup(
            title = "Evento diário",
            onDismiss = { showDailyPopup = false }
        ) {
            Text(
                text = "Evento diário em breve.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
    if (showNoAdsPopup) {
        GamePopup(
            title = "Sem anúncios",
            onDismiss = { showNoAdsPopup = false }
        ) {
            Text(
                text = "Recurso em breve.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
    selectedSkill?.let { skill ->
        GamePopup(
            title = skill.label,
            onDismiss = { selectedSkill = null }
        ) {
            Text(
                text = "Nível atual: ${skill.level}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "XP: ${skill.currentXp.toInt()} / ${skill.requiredXp.toInt()}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            GamePrimaryButton(
                label = "Ir para ${skill.label}",
                onClick = {
                    selectedSkill = null
                    onOpenSkill(skill)
                },
                modifier = Modifier.fillMaxWidth(0.82f)
            )
        }
    }
}

@Composable
private fun MainHubFloatingActions(
    dailyTimer: String,
    onDailyClick: () -> Unit,
    onNoAdsClick: () -> Unit,
    onOpenGlobalBoss: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val compact = maxWidth < 380.dp || maxHeight < 720.dp
        val bonfireBandTop = if (compact) maxHeight * 0.60f else maxHeight * 0.58f
        val horizontalPadding = if (compact) 8.dp else 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    top = bonfireBandTop,
                    end = horizontalPadding
                )
        ) {
            DailyFloatingButton(
                timer = dailyTimer,
                compact = compact,
                onClick = onDailyClick,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingPillButton(
                    icon = "\uD83D\uDEAB",
                    label = "NO ADS",
                    background = Color(0xFF0F766E),
                    compact = compact,
                    onClick = onNoAdsClick
                )
                FloatingPillButton(
                    icon = "\uD83D\uDC79",
                    label = "BOSS",
                    background = Color(0xFFB45309),
                    compact = compact,
                    onClick = onOpenGlobalBoss
                )
            }
        }
    }
}

@Composable
private fun DailyFloatingButton(
    timer: String,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = if (compact) 66.dp else 76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF92400E),
        contentColor = Color.White,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 7.dp else 9.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "\uD83D\uDCC5",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = timer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FloatingPillButton(
    icon: String,
    label: String,
    background: Color,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = if (compact) 142.dp else 164.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = background,
        contentColor = Color.White,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = icon,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun formatDailyCountdown(nowMillis: Long = System.currentTimeMillis()): String {
    val nextReset = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val remainingSeconds = ((nextReset.timeInMillis - nowMillis).coerceAtLeast(0L) / 1_000L)
    val hours = remainingSeconds / 3_600L
    val minutes = (remainingSeconds % 3_600L) / 60L
    val seconds = remainingSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatMinutes(value: Double): String = "%.1f".format(value.coerceAtLeast(0.0))



