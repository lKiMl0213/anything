package rpg.cli.model

import rpg.engine.ComputedStats
import rpg.item.ItemRarity
import rpg.model.ItemType
import rpg.model.PlayerState

internal typealias BattleOutcome = rpg.application.model.BattleOutcome
internal typealias CombatSkillOption = rpg.application.model.CombatSkillOption
internal typealias CombatMenuAction = rpg.application.model.CombatMenuAction

internal enum class DecisionView {
    MAIN,
    ATTACK,
    ITEM
}

internal typealias AttrMeta = rpg.application.model.AttrMeta
internal typealias AttributeDistributionState = rpg.application.model.AttributeDistributionState
internal typealias InitialAttributeAllocation = rpg.application.model.InitialAttributeAllocation

internal data class ShopPurchaseResult(
    val success: Boolean,
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>,
    val message: String
)

internal data class UseItemResult(
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>
)

internal data class InventoryStack(
    val sampleItemId: String,
    val quantity: Int,
    val itemIds: List<String>,
    val item: rpg.item.ResolvedItem
)

internal data class InventoryFilter(
    val type: ItemType? = null,
    val minimumRarity: ItemRarity? = null
)

internal typealias AmmoStack = rpg.application.model.AmmoStack

internal data class EquipPreviewTarget(
    val slotKey: String,
    val currentItemId: String?
)

internal data class EquipComparisonPreview(
    val slotKey: String,
    val replacedItem: rpg.item.ResolvedItem?,
    val before: ComputedStats,
    val after: ComputedStats
)

internal typealias DeathPenaltyResult = rpg.application.model.DeathPenaltyResult
internal typealias RunFinalizeResult = rpg.application.model.RunFinalizeResult
internal typealias EventRoomOutcome = rpg.application.model.EventRoomOutcome

internal data class QuestUiSnapshot(
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>,
    val board: rpg.quest.QuestBoardState
)
