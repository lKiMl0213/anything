package rpg.presentation.model

import rpg.application.actions.GameAction

data class ProgressBarViewModel(
    val label: String,
    val current: Double,
    val max: Double
)

data class PlayerSummaryViewModel(
    val name: String,
    val level: Int,
    val classLabel: String,
    val gold: Int,
    val hp: ProgressBarViewModel,
    val mp: ProgressBarViewModel
)

data class ScreenOptionViewModel(
    val key: String,
    val label: String,
    val action: GameAction
)

sealed interface ScreenViewModel {
    val title: String
    val messages: List<String>
}

data class MenuScreenViewModel(
    override val title: String,
    val subtitle: String? = null,
    val summary: PlayerSummaryViewModel? = null,
    val bodyLines: List<String> = emptyList(),
    val options: List<ScreenOptionViewModel> = emptyList(),
    override val messages: List<String> = emptyList()
) : ScreenViewModel

data class CombatScreenViewModel(
    override val title: String,
    val introLines: List<String>,
    val playerName: String,
    val enemyName: String,
    val playerHp: ProgressBarViewModel,
    val playerMp: ProgressBarViewModel,
    val enemyHp: ProgressBarViewModel,
    val logLines: List<String>,
    val options: List<ScreenOptionViewModel>,
    override val messages: List<String> = emptyList()
) : ScreenViewModel
