package rpg.android

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rpg.android.audio.AndroidAudioManager
import rpg.android.audio.AudioEvent
import rpg.android.audio.GameAudioController
import rpg.android.audio.MusicTrack
import rpg.android.audio.ProvideGameAudioController
import rpg.android.audio.SoundEffect
import rpg.android.config.AppBuildInfoProvider
import rpg.android.screens.AttributeDistributionScreen
import rpg.android.screens.CharacterManagementScreen
import rpg.android.screens.CombatTouchScreen
import rpg.android.screens.GameSettingsOverlay
import rpg.android.screens.GenericMenuScreen
import rpg.android.screens.MainHubScreen
import rpg.android.screens.NewGameScreen
import rpg.android.screens.PatchNotesPopupContent
import rpg.android.screens.RaceClassScreen
import rpg.android.screens.StartPageScreen
import rpg.android.screens.TimedActionOverlay
import rpg.android.screens.isGlobalBossContext
import rpg.android.state.AndroidUiState
import rpg.android.state.MainSection
import rpg.android.tutorial.TutorialOverlay
import rpg.android.theme.AnythingRpgTheme
import rpg.android.ui.components.GamePopupMenu
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GamePopup
import rpg.android.ui.scale.ProvideGameUiScale

@Composable
fun AndroidGameApp() {
    val app = LocalContext.current.applicationContext as Application
    val activity = LocalContext.current as? Activity
    val viewModel: AndroidGameViewModel = viewModel(
        factory = AndroidGameViewModel.factory(app)
    )
    val uiState by viewModel.uiState.collectAsState()
    val timedAction by viewModel.timedActionState.collectAsState()
    val popupDetail by viewModel.popupDetail.collectAsState()
    val patchNotesPopup by viewModel.patchNotesPopup.collectAsState()
    val tutorialOverlay by viewModel.tutorialOverlay.collectAsState()
    val hasProgressAlert by viewModel.progressAlert.collectAsState()
    val hasActiveGame by viewModel.hasActiveGame.collectAsState()
    val tutorialCompletedForCurrentGame by viewModel.tutorialCompletedForCurrentGame.collectAsState()
    val darkThemeEnabled by viewModel.darkTheme.collectAsState()
    val uiScale by viewModel.uiScale.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val buildInfo = AppBuildInfoProvider.current()
    val audioManager = remember(app) { AndroidAudioManager(app) }
    val audioController = remember(viewModel) {
        GameAudioController { effect ->
            viewModel.playUiSound(effect)
        }
    }
    val baseMusicTrack = remember(uiState) {
        resolveMusicTrack(uiState)
    }
    var musicOverride by remember { mutableStateOf<MusicTrack?>(null) }
    var wasPopupVisible by remember { mutableStateOf(false) }
    var combatSnapshot by remember { mutableStateOf<CombatAudioSnapshot?>(null) }
    val popupVisible = popupDetail != null || patchNotesPopup != null

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
                audioManager.stopMusic()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioManager.release()
        }
    }

    LaunchedEffect(audioSettings) {
        audioManager.updateSettings(audioSettings)
    }

    LaunchedEffect(baseMusicTrack, musicOverride, audioSettings.musicEnabled) {
        val selectedTrack = if (audioSettings.musicEnabled) musicOverride ?: baseMusicTrack else null
        audioManager.playMusic(selectedTrack, loop = musicOverride == null)
    }

    LaunchedEffect(viewModel) {
        var stingerJob: Job? = null
        viewModel.audioEvents.collect { event ->
            when (event) {
                is AudioEvent.PlaySfx -> audioManager.playSfx(event.effect)
                is AudioEvent.PlayMusicStinger -> {
                    stingerJob?.cancel()
                    stingerJob = launch {
                        musicOverride = event.track
                        delay(event.durationMs.coerceAtLeast(600L))
                        musicOverride = null
                    }
                }
            }
        }
    }

    LaunchedEffect(popupVisible) {
        if (popupVisible != wasPopupVisible) {
            val effect = if (popupVisible) SoundEffect.POPUP_OPEN else SoundEffect.POPUP_CLOSE
            viewModel.playUiSound(effect)
            wasPopupVisible = popupVisible
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state !is AndroidUiState.Combat) {
            combatSnapshot = null
            return@LaunchedEffect
        }
        val current = CombatAudioSnapshot(
            playerHp = state.state.playerHp,
            enemyHp = state.state.enemyHp
        )
        val previous = combatSnapshot
        if (previous != null) {
            if (current.enemyHp < previous.enemyHp - 0.001) {
                viewModel.playUiSound(SoundEffect.HIT)
            }
            if (current.playerHp < previous.playerHp - 0.001) {
                viewModel.playUiSound(SoundEffect.ATTACK_ENEMY)
                viewModel.playUiSound(SoundEffect.HIT)
            } else if (current.playerHp > previous.playerHp + 0.001) {
                viewModel.playUiSound(SoundEffect.HEAL)
            }
        }
        combatSnapshot = current
    }

    AnythingRpgTheme(darkTheme = darkThemeEnabled) {
        ProvideGameUiScale(scale = uiScale) {
            ProvideGameAudioController(controller = audioController) {
                Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
            AndroidUiState.Loading -> StartPageScreen(
                state = rpg.android.state.StartPageUiModel(
                    canLoad = false,
                    message = "Carregando dados do jogo..."
                ),
                onNewGame = {},
                onLoad = {}
            )

            is AndroidUiState.Error -> StartPageScreen(
                state = rpg.android.state.StartPageUiModel(
                    canLoad = true,
                    message = state.message
                ),
                onNewGame = viewModel::startNewGame,
                onLoad = viewModel::loadGame
            )

            is AndroidUiState.StartPage -> StartPageScreen(
                state = state.state,
                onNewGame = viewModel::startNewGame,
                onLoad = viewModel::openLoadList,
                onLoadSelected = viewModel::loadSelectedSave
            )

            is AndroidUiState.NewGame -> NewGameScreen(
                state = state.state,
                onNameChange = viewModel::onCreationNameChanged,
                onOpenRaceClass = viewModel::openRaceClassSelection,
                onOpenAttributes = viewModel::openCreationAttributeDistribution,
                onConfirm = viewModel::confirmCreation,
                onCancel = viewModel::cancelCreation
            )

            is AndroidUiState.RaceClass -> RaceClassScreen(
                state = state.state,
                onSelectRace = viewModel::onRaceSelected,
                onSelectClass = viewModel::onClassSelected,
                onConfirm = viewModel::confirmRaceClassSelection,
                onCancel = viewModel::cancelRaceClassSelection
            )

            is AndroidUiState.AttributeDistribution -> AttributeDistributionScreen(
                state = state.state,
                onIncrease = viewModel::onAttributeIncrease,
                onDecrease = viewModel::onAttributeDecrease,
                onConfirm = viewModel::confirmAttributeDistribution,
                onCancel = viewModel::cancelAttributeDistribution
            )

            is AndroidUiState.MainHub -> MainHubScreen(
                state = state.state,
                hasProgressAlert = hasProgressAlert,
                versionLabel = buildInfo.shortLabel,
                onExplore = viewModel::openExplore,
                onOpenCharacter = viewModel::openCharacter,
                onOpenProduction = viewModel::openProduction,
                onOpenCity = viewModel::openCity,
                onOpenProgression = viewModel::openProgression,
                onOpenGlobalBoss = viewModel::openGlobalBoss
            )

            is AndroidUiState.Character -> CharacterManagementScreen(
                state = state.state,
                hasProgressAlert = hasProgressAlert,
                onSlotClick = viewModel::onCharacterSlotTapped,
                onInventoryItemClick = viewModel::onCharacterInventoryItemTapped,
                onOpenAttributes = viewModel::openCharacterAttributes,
                onOpenTalents = viewModel::openTalents,
                onOpenProduction = viewModel::openProduction,
                onOpenHub = viewModel::openHub,
                onOpenCity = viewModel::openCity,
                onOpenProgression = viewModel::openProgression
            )

            is AndroidUiState.GenericMenu -> GenericMenuScreen(
                viewModel = state.viewModel,
                section = state.section,
                actionPreviews = state.actionPreviews,
                talentTreeGraph = state.talentTreeGraph,
                hasProgressAlert = hasProgressAlert,
                onAction = viewModel::onMenuAction,
                onOpenCharacter = viewModel::openCharacter,
                onOpenProduction = viewModel::openProduction,
                onOpenHub = viewModel::openHub,
                onOpenCity = viewModel::openCity,
                onOpenProgression = viewModel::openProgression
            )

            is AndroidUiState.Combat -> CombatTouchScreen(
                state = state.state,
                onAttack = viewModel::onCombatAttack,
                onEscape = viewModel::onCombatEscape,
                onUseItem = viewModel::onCombatUseItem
            )
        }

                GameSettingsOverlay(
                    enabled = uiState !is AndroidUiState.Loading,
                    tutorialAvailable = hasActiveGame,
                    patchNotesAvailable = tutorialCompletedForCurrentGame,
                    isDarkTheme = darkThemeEnabled,
                    uiScale = uiScale,
                    audioSettings = audioSettings,
                    buildInfo = buildInfo,
                    onToggleTheme = viewModel::toggleTheme,
                    onUiScaleSelected = viewModel::setUiScale,
                    onMusicEnabledChange = viewModel::setMusicEnabled,
                    onEffectsEnabledChange = viewModel::setEffectsEnabled,
                    onSettingsOpened = viewModel::onSettingsOpened,
                    onOpenPatchNotes = viewModel::openPatchNotesFromSettings,
                    onRestartTutorial = viewModel::restartTutorialFromSettings,
                    onExitApp = { activity?.finishAffinity() }
                )

        timedAction?.let { timed ->
            TimedActionOverlay(
                state = timed,
                onCancel = viewModel::cancelTimedAction
            )
        }

        popupDetail?.let { popup ->
            GamePopupMenu(
                title = popup.title,
                onDismiss = viewModel::clearPopup,
                showCloseButton = popup.showCloseButton
            ) {
                popup.lines.forEach { line ->
                    androidx.compose.material3.Text(
                        text = line,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                popup.quantity?.let { quantity ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GamePrimaryButton(
                            label = "-",
                            onClick = { quantity.onDecrease?.invoke() },
                            enabled = quantity.value > quantity.minValue,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Text(
                            text = "${quantity.value} un. | ${quantity.unitValue} cada | total ${quantity.totalValue}",
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        GamePrimaryButton(
                            label = "+",
                            onClick = { quantity.onIncrease?.invoke() },
                            enabled = quantity.value < quantity.maxValue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                popup.primaryLabel?.let { label ->
                    GamePrimaryButton(
                        label = label,
                        onClick = { popup.onPrimary?.invoke() }
                    )
                }
                popup.secondaryLabel?.let { label ->
                    GamePrimaryButton(
                        label = label,
                        onClick = { popup.onSecondary?.invoke() }
                    )
                }
            }
        }

        if (patchNotesPopup == null) {
            tutorialOverlay?.let { overlay ->
                TutorialOverlay(
                    state = overlay,
                    onContinue = viewModel::onTutorialContinue,
                    onComplete = viewModel::onTutorialComplete,
                    onSkip = viewModel::onTutorialSkip
                )
            }
        }

        patchNotesPopup?.let { notes ->
            GamePopup(
                title = notes.title,
                onDismiss = viewModel::dismissPatchNotesPopup,
                showCloseButton = false,
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                PatchNotesPopupContent(notes = notes)
            }
        }
            }
        }
    }
}
}

private fun resolveMusicTrack(state: AndroidUiState): MusicTrack {
    return when (state) {
        AndroidUiState.Loading -> MusicTrack.MENU
        is AndroidUiState.Error,
        is AndroidUiState.StartPage,
        is AndroidUiState.NewGame,
        is AndroidUiState.RaceClass,
        is AndroidUiState.AttributeDistribution -> MusicTrack.MENU

        is AndroidUiState.MainHub -> MusicTrack.HOME
        is AndroidUiState.Character -> MusicTrack.CHARACTER
        is AndroidUiState.Combat -> {
            if (state.state.title.contains("boss", ignoreCase = true)) {
                MusicTrack.BOSS
            } else {
                MusicTrack.BATTLE
            }
        }

        is AndroidUiState.GenericMenu -> {
            if (isGlobalBossContext(state.viewModel.title, state.viewModel.options)) {
                MusicTrack.BOSS
            } else {
                when (state.section) {
                    MainSection.CHARACTER -> MusicTrack.CHARACTER
                    MainSection.PRODUCTION -> MusicTrack.PRODUCTION
                    MainSection.CITY -> MusicTrack.CITY
                    MainSection.PROGRESSION -> MusicTrack.PROGRESS
                    MainSection.EXPLORATION -> MusicTrack.HOME
                }
            }
        }
    }
}

private data class CombatAudioSnapshot(
    val playerHp: Double,
    val enemyHp: Double
)
