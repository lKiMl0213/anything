package rpg.android

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import rpg.android.state.AndroidUiState
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val buildInfo = AppBuildInfoProvider.current()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnythingRpgTheme(darkTheme = darkThemeEnabled) {
        ProvideGameUiScale(scale = uiScale) {
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
                    buildInfo = buildInfo,
                    onToggleTheme = viewModel::toggleTheme,
                    onUiScaleSelected = viewModel::setUiScale,
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
