package rpg.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import rpg.android.ui.components.GameDropdownSelect
import rpg.android.config.AppBuildInfo
import rpg.android.ui.components.GameIconActionButton
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopup
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameSelectOption
import rpg.android.ui.components.GameUiTokens
import rpg.android.ui.scale.GameUiScale

@Composable
fun GameSettingsOverlay(
    enabled: Boolean,
    isDarkTheme: Boolean,
    uiScale: GameUiScale,
    buildInfo: AppBuildInfo,
    onToggleTheme: () -> Unit,
    onUiScaleSelected: (GameUiScale) -> Unit,
    onOpenPatchNotes: () -> Unit,
    onExitApp: () -> Unit
) {
    if (!enabled) return
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
            onClick = { settingsOpen = true },
            size = 50.dp
        )
    }

    if (settingsOpen) {
        SettingsPopup(
            isDarkTheme = isDarkTheme,
            uiScale = uiScale,
            buildInfo = buildInfo,
            onDismiss = { settingsOpen = false },
            onToggleTheme = onToggleTheme,
            onUiScaleSelected = onUiScaleSelected,
            onOpenPatchNotes = {
                settingsOpen = false
                onOpenPatchNotes()
            },
            onExitApp = {
                settingsOpen = false
                onExitApp()
            }
        )
    }
}

@Composable
private fun SettingsPopup(
    isDarkTheme: Boolean,
    uiScale: GameUiScale,
    buildInfo: AppBuildInfo,
    onDismiss: () -> Unit,
    onToggleTheme: () -> Unit,
    onUiScaleSelected: (GameUiScale) -> Unit,
    onOpenPatchNotes: () -> Unit,
    onExitApp: () -> Unit
) {
    var feedbackOpen by remember { mutableStateOf(false) }

    GamePopup(
        title = "Configuracoes",
        onDismiss = onDismiss,
        showCloseButton = false,
        modifier = Modifier.fillMaxWidth(0.95f)
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
            GamePanel(title = "VERSAO BETA") {
                Text(
                    text = "Este jogo ainda esta em desenvolvimento. Interface, balanceamento, recompensas, textos, imagens e sistemas podem mudar.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "Tema atual: ${if (isDarkTheme) "Escuro" else "Claro"}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            GamePrimaryButton(
                label = "Alternar tema",
                onClick = onToggleTheme,
                modifier = Modifier.fillMaxWidth(0.78f)
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
                modifier = Modifier.fillMaxWidth(0.90f),
                width = GameUiTokens.buttonMaxWidth
            )
            GamePrimaryButton(
                label = "Feedback / Reportar bug",
                onClick = { feedbackOpen = true },
                modifier = Modifier.fillMaxWidth(0.90f)
            )
            GamePrimaryButton(
                label = "Patch Notes / Changelog",
                onClick = onOpenPatchNotes,
                modifier = Modifier.fillMaxWidth(0.90f)
            )
            GamePrimaryButton(
                label = "Sair",
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth(0.78f)
            )
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

internal fun uiScaleLabel(scale: GameUiScale): String {
    return when (scale) {
        GameUiScale.SMALL -> "Pequeno"
        GameUiScale.MEDIUM -> "Medio"
        GameUiScale.LARGE -> "Grande"
    }
}
