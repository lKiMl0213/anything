package rpg.android.state

import rpg.application.actions.GameAction
import rpg.application.inventory.EquippedSlotView
import rpg.application.inventory.InventoryStackView

data class StartPageUiModel(
    val canLoad: Boolean,
    val message: String? = null,
    val saves: List<SaveSlotUi> = emptyList()
)

data class SaveSlotUi(
    val fileName: String,
    val characterName: String
)

data class NewGameAttributeUi(
    val code: String,
    val label: String,
    val finalValue: Int
)

data class NewGameUiModel(
    val name: String,
    val selectedRaceName: String,
    val selectedClassName: String,
    val pointsRemaining: Int,
    val attributes: List<NewGameAttributeUi>,
    val canConfirm: Boolean,
    val message: String? = null
)

data class RaceClassUiModel(
    val selectedRaceId: String?,
    val selectedClassId: String?,
    val raceOptions: List<SelectOption>,
    val classOptions: List<SelectOption>,
    val raceSummaryLines: List<String>,
    val classSummaryLines: List<String>
)

data class AttributeDistributionRowUi(
    val code: String,
    val label: String,
    val previewValue: Int,
    val allocated: Int
)

data class AttributeDistributionUiModel(
    val title: String,
    val pointsRemaining: Int,
    val rows: List<AttributeDistributionRowUi>,
    val detailByCode: Map<String, List<String>>,
    val canConfirm: Boolean,
    val messages: List<String>
)

data class HubSkillUi(
    val symbol: String,
    val label: String,
    val level: Int,
    val currentXp: Double,
    val requiredXp: Double
)

data class MainHubUiModel(
    val name: String,
    val premiumStatusLabel: String,
    val raceClassLabel: String,
    val levelXpLabel: String,
    val currencyLabel: String,
    val inventoryCapacityLabel: String,
    val hpCurrent: Double,
    val hpMax: Double,
    val mpCurrent: Double,
    val mpMax: Double,
    val activeEffectName: String?,
    val activeEffectRemainingSeconds: Int,
    val deathDebuffStacks: Int,
    val deathDebuffMinutes: Double,
    val hpRegenPerMinute: Double,
    val mpRegenPerMinute: Double,
    val hpEtaSeconds: Int,
    val mpEtaSeconds: Int,
    val skills: List<HubSkillUi>,
    val infoLines: List<String>
)

data class CharacterUiModel(
    val equippedSlots: List<EquippedSlotView>,
    val accessorySlots: List<EquippedSlotView>,
    val inventoryStacks: List<InventoryStackView>,
    val inventoryCapacityLabel: String,
    val canOpenAttributes: Boolean,
    val canOpenTalents: Boolean
)

data class TimedActionUiState(
    val title: String,
    val detail: String,
    val remainingSeconds: Int,
    val progress: Float
)

data class PopupDetailUiModel(
    val title: String,
    val lines: List<String>,
    val primaryLabel: String? = null,
    val onPrimary: (() -> Unit)? = null,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
    val quantity: PopupQuantityUiModel? = null,
    val showCloseButton: Boolean = true
)

data class PopupQuantityUiModel(
    val value: Int,
    val minValue: Int,
    val maxValue: Int,
    val unitValue: Int,
    val totalValue: Int,
    val onDecrease: (() -> Unit)? = null,
    val onIncrease: (() -> Unit)? = null
)

data class MenuActionPreviewUiModel(
    val optionKey: String,
    val title: String,
    val lines: List<String>,
    val primaryLabel: String,
    val primaryAction: GameAction,
    val secondaryLabel: String? = null,
    val secondaryAction: GameAction? = null,
    val quantityPicker: MenuQuantityPickerUiModel? = null,
    val detailPopupTitle: String? = null,
    val detailPopupLines: List<String> = emptyList()
)

data class MenuQuantityPickerUiModel(
    val minValue: Int,
    val maxValue: Int,
    val currentValue: Int,
    val applyAction: (Int) -> GameAction
)

data class PatchNotesUiModel(
    val title: String,
    val versionLabel: String,
    val dateLabel: String?,
    val novidades: List<String>,
    val melhorias: List<String>,
    val correcoes: List<String>,
    val sistemas: List<String>,
    val markSeenOnDismiss: Boolean = false
)

data class TalentTreeGraphUiModel(
    val title: String,
    val stageLabel: String,
    val pointsAvailable: Int,
    val nodes: List<TalentTreeNodeUiModel>
)

data class TalentTreeNodeUiModel(
    val nodeId: String,
    val name: String,
    val currentRank: Int,
    val maxRank: Int,
    val canRankUp: Boolean,
    val blockedReason: String?,
    val prerequisites: List<TalentTreePrerequisiteUiModel>
)

data class TalentTreePrerequisiteUiModel(
    val nodeId: String,
    val nodeName: String,
    val minRank: Int
)
