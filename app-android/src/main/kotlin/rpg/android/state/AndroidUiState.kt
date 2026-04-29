package rpg.android.state

import rpg.application.actions.GameAction
import rpg.presentation.model.MenuScreenViewModel

sealed interface AndroidUiState {
    data object Loading : AndroidUiState
    data class Error(val message: String) : AndroidUiState
    data class Menu(val viewModel: MenuScreenViewModel) : AndroidUiState
    data class CharacterCreation(val state: CharacterCreationUiState) : AndroidUiState
    data class Attributes(val state: AttributeAllocationUiState) : AndroidUiState
    data class Combat(val state: CombatUiState) : AndroidUiState
}

data class SelectOption(
    val id: String,
    val label: String
)

data class CharacterCreationAttributeRowUi(
    val code: String,
    val label: String,
    val raceBonus: Int,
    val classBonus: Int,
    val allocated: Int
) {
    val finalValue: Int = raceBonus + classBonus + allocated
}

data class CharacterCreationUiState(
    val name: String,
    val races: List<SelectOption>,
    val classes: List<SelectOption>,
    val selectedRaceId: String?,
    val selectedClassId: String?,
    val pointsRemaining: Int,
    val attributes: List<CharacterCreationAttributeRowUi>,
    val canConfirm: Boolean,
    val message: String? = null
)

data class AttributeAllocationRowUi(
    val code: String,
    val label: String,
    val currentFinal: Int,
    val currentBase: Int,
    val equipmentBonus: Int,
    val classTalentBonus: Int,
    val temporaryBonus: Int,
    val pending: Int
) {
    val previewFinal: Int = currentFinal + pending
}

data class AttributeAllocationUiState(
    val pointsRemaining: Int,
    val rows: List<AttributeAllocationRowUi>,
    val canApply: Boolean,
    val messages: List<String>
)

data class CombatConsumableUi(
    val itemId: String,
    val label: String
)

data class CombatActionButtonUi(
    val label: String,
    val action: GameAction,
    val enabled: Boolean
)

data class CombatUiState(
    val title: String,
    val enemyName: String,
    val introLines: List<String>,
    val playerName: String,
    val playerHp: Double,
    val playerHpMax: Double,
    val playerMp: Double,
    val playerMpMax: Double,
    val enemyHp: Double,
    val enemyHpMax: Double,
    val playerAtbProgress: Float,
    val enemyAtbProgress: Float,
    val playerAtbLabel: String,
    val enemyAtbLabel: String,
    val playerReady: Boolean,
    val statusLines: List<String>,
    val logLines: List<String>,
    val actions: List<CombatActionButtonUi>,
    val consumables: List<CombatConsumableUi>
)
