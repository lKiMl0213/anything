package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rpg.android.audio.AudioSettings
import rpg.android.config.AppBuildInfo
import rpg.android.tutorial.TutorialTarget
import rpg.android.tutorial.tutorialAnchor
import rpg.android.audio.LocalGameAudioController
import rpg.android.audio.SoundEffect
import rpg.android.ui.components.GameDropdownSelect
import rpg.android.ui.components.GameIconActionButton
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameSelectOption
import rpg.android.ui.components.GameUiTokens
import rpg.android.ui.scale.GameUiScale

@Composable
fun GameSettingsOverlay(
    enabled: Boolean,
    tutorialAvailable: Boolean,
    patchNotesAvailable: Boolean,
    isDarkTheme: Boolean,
    uiScale: GameUiScale,
    audioSettings: AudioSettings,
    buildInfo: AppBuildInfo,
    onToggleTheme: () -> Unit,
    onUiScaleSelected: (GameUiScale) -> Unit,
    onMusicEnabledChange: (Boolean) -> Unit,
    onEffectsEnabledChange: (Boolean) -> Unit,
    onSettingsOpened: () -> Boolean,
    onOpenPatchNotes: () -> Unit,
    onRestartTutorial: () -> Unit,
    onExitApp: () -> Unit
) {
    if (!enabled) return
    val audioController = LocalGameAudioController.current
    var settingsOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(4.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        GameIconActionButton(
            icon = "\u2699\ufe0f",
            onClick = {
                if (onSettingsOpened()) {
                    audioController.play(SoundEffect.POPUP_OPEN)
                    settingsOpen = true
                }
            },
            modifier = Modifier.tutorialAnchor(TutorialTarget.SETTINGS_BUTTON, extraPadding = 8.dp),
            size = 50.dp
        )
    }

    if (settingsOpen) {
        SettingsPopup(
            tutorialAvailable = tutorialAvailable,
            patchNotesAvailable = patchNotesAvailable,
            isDarkTheme = isDarkTheme,
            uiScale = uiScale,
            audioSettings = audioSettings,
            buildInfo = buildInfo,
            onDismiss = {
                audioController.play(SoundEffect.POPUP_CLOSE)
                settingsOpen = false
            },
            onToggleTheme = onToggleTheme,
            onUiScaleSelected = onUiScaleSelected,
            onMusicEnabledChange = onMusicEnabledChange,
            onEffectsEnabledChange = onEffectsEnabledChange,
            onOpenPatchNotes = {
                audioController.play(SoundEffect.POPUP_CLOSE)
                settingsOpen = false
                onOpenPatchNotes()
            },
            onRestartTutorial = {
                audioController.play(SoundEffect.POPUP_CLOSE)
                settingsOpen = false
                onRestartTutorial()
            },
            onExitApp = {
                audioController.play(SoundEffect.POPUP_CLOSE)
                settingsOpen = false
                onExitApp()
            }
        )
    }
}

@Composable
private fun SettingsPopup(
    tutorialAvailable: Boolean,
    patchNotesAvailable: Boolean,
    isDarkTheme: Boolean,
    uiScale: GameUiScale,
    audioSettings: AudioSettings,
    buildInfo: AppBuildInfo,
    onDismiss: () -> Unit,
    onToggleTheme: () -> Unit,
    onUiScaleSelected: (GameUiScale) -> Unit,
    onMusicEnabledChange: (Boolean) -> Unit,
    onEffectsEnabledChange: (Boolean) -> Unit,
    onOpenPatchNotes: () -> Unit,
    onRestartTutorial: () -> Unit,
    onExitApp: () -> Unit
) {
    var feedbackOpen by remember { mutableStateOf(false) }
    val compactWidth = GameUiTokens.compactSelectWidth

    GamePopup(
        title = "Configuracoes",
        onDismiss = onDismiss,
        showCloseButton = false,
        modifier = Modifier.fillMaxWidth(0.90f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = buildInfo.betaLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Som",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            TwoColumnRow {
                SettingsActionButton(
                    label = "BGM ${if (audioSettings.musicEnabled) "\uD83C\uDFB5" else "\uD83D\uDD07"}",
                    onClick = { onMusicEnabledChange(!audioSettings.musicEnabled) },
                    width = compactWidth
                )
                SettingsActionButton(
                    label = "Effects ${if (audioSettings.effectsEnabled) "\uD83D\uDD14" else "\uD83D\uDD15"}",
                    onClick = { onEffectsEnabledChange(!audioSettings.effectsEnabled) },
                    width = compactWidth
                )
            }

            TwoColumnRow {
                SettingsActionButton(
                    label = if (isDarkTheme) "\uD83C\uDF19" else "\u2600\uFE0F",
                    onClick = onToggleTheme,
                    width = compactWidth
                )
                GameDropdownSelect(
                    label = "Interface: ${uiScaleLabel(uiScale)}",
                    options = GameUiScale.entries.map { scale ->
                        GameSelectOption(scale.storageKey, uiScaleLabel(scale))
                    },
                    onSelect = { selected ->
                        val matched = GameUiScale.entries.firstOrNull { it.storageKey == selected.key }
                        if (matched != null) {
                            onUiScaleSelected(matched)
                        }
                    },
                    width = compactWidth
                )
            }

            TwoColumnRow {
                SettingsActionButton(
                    label = if (patchNotesAvailable) "Patchnotes" else "Patchnotes \uD83D\uDD12",
                    onClick = onOpenPatchNotes,
                    width = compactWidth
                )
                SettingsActionButton(
                    label = "Feedback",
                    onClick = { feedbackOpen = true },
                    width = compactWidth
                )
            }

            TwoColumnRow {
                if (tutorialAvailable) {
                    SettingsActionButton(
                        label = "Tutorial",
                        onClick = onRestartTutorial,
                        width = compactWidth
                    )
                } else {
                    Spacer(modifier = Modifier.width(compactWidth))
                }
                SettingsActionButton(
                    label = "Sair",
                    onClick = onExitApp,
                    width = compactWidth
                )
            }
        }
    }

    if (feedbackOpen) {
        FeedbackPopup(
            buildInfo = buildInfo,
            uiScale = uiScale,
            onDismiss = { feedbackOpen = false }
        )
    }
}

@Composable
private fun TwoColumnRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp
) {
    GamePrimaryButton(
        label = label,
        onClick = onClick,
        modifier = Modifier.width(width)
    )
}

internal fun uiScaleLabel(scale: GameUiScale): String {
    return when (scale) {
        GameUiScale.SMALL -> "Pequeno"
        GameUiScale.MEDIUM -> "Medio"
        GameUiScale.LARGE -> "Grande"
    }
}
