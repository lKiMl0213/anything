package rpg.application.model

import rpg.model.Attributes
import rpg.model.ItemInstance
import rpg.model.PlayerState

data class BattleOutcome(
    val playerAfter: PlayerState,
    val itemInstances: Map<String, ItemInstance> = emptyMap(),
    val victory: Boolean,
    val escaped: Boolean = false,
    val collectedItems: Map<String, Int> = emptyMap()
)

data class CombatSkillOption(
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

sealed interface CombatMenuAction {
    data class BasicAttack(
        val preferMagic: Boolean?,
        val available: Boolean,
        val unavailableReason: String?
    ) : CombatMenuAction

    data class SkillAttack(val skill: CombatSkillOption) : CombatMenuAction
}

data class AttrMeta(
    val code: String,
    val label: String
)

data class AmmoStack(
    val templateId: String,
    val sampleItemId: String,
    val quantity: Int,
    val itemIds: List<String>,
    val item: rpg.item.ResolvedItem
)

data class DeathPenaltyResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>
)

data class RunFinalizeResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>
)

data class EventRoomOutcome(
    val player: PlayerState,
    val battleOutcome: BattleOutcome? = null,
    val itemInstances: Map<String, ItemInstance>? = null,
    val questBoard: rpg.quest.QuestBoardState
)

data class AttributeDistributionState(
    val allocated: Attributes,
    val remainingPoints: Int
)

data class InitialAttributeAllocation(
    val baseAttributes: Attributes,
    val unspentPoints: Int
)
