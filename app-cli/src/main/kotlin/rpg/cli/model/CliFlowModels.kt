package rpg.cli.model

import rpg.engine.ComputedStats
import rpg.item.ItemRarity
import rpg.model.Attributes
import rpg.model.ItemType
import rpg.model.PlayerState

internal data class BattleOutcome(
    val playerAfter: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance> = emptyMap(),
    val victory: Boolean,
    val escaped: Boolean = false,
    val collectedItems: Map<String, Int> = emptyMap()
)

internal data class CombatSkillOption(
    val id: String,
    val name: String,
    val mpCost: Double,
    val cooldownSeconds: Double,
    val castTimeSeconds: Double,
    val damageMultiplier: Double,
    val preferMagic: Boolean?,
    val onHitStatuses: List<rpg.model.CombatStatusApplyDef>,
    val selfHealFlat: Double,
    val selfHealPctMaxHp: Double,
    val ammoCost: Int,
    val rank: Int,
    val maxRank: Int,
    val aoeUnlockRank: Int,
    val aoeBonusDamagePct: Double,
    val available: Boolean,
    val unavailableReason: String?,
    val cooldownRemainingSeconds: Double
)

internal sealed interface CombatMenuAction {
    data class BasicAttack(
        val preferMagic: Boolean?,
        val available: Boolean,
        val unavailableReason: String?
    ) : CombatMenuAction
    data class SkillAttack(val skill: CombatSkillOption) : CombatMenuAction
}

internal enum class DecisionView {
    MAIN,
    ATTACK,
    ITEM
}

internal data class AttrMeta(
    val code: String,
    val label: String
)

internal data class AttributeDistributionState(
    val allocated: Attributes,
    val remainingPoints: Int
)

internal data class InitialAttributeAllocation(
    val baseAttributes: Attributes,
    val unspentPoints: Int
)

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

internal data class AmmoStack(
    val templateId: String,
    val sampleItemId: String,
    val quantity: Int,
    val itemIds: List<String>,
    val item: rpg.item.ResolvedItem
)

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

internal data class DeathPenaltyResult(
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>
)

internal data class RunFinalizeResult(
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>
)

internal data class EventRoomOutcome(
    val player: PlayerState,
    val battleOutcome: BattleOutcome? = null,
    val itemInstances: Map<String, rpg.model.ItemInstance>? = null,
    val questBoard: rpg.quest.QuestBoardState
)

internal data class QuestUiSnapshot(
    val player: PlayerState,
    val itemInstances: Map<String, rpg.model.ItemInstance>,
    val board: rpg.quest.QuestBoardState
)
