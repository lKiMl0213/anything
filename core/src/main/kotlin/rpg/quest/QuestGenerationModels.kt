package rpg.quest

import rpg.model.CraftRecipeDef
import rpg.model.GatherNodeDef
import rpg.model.PlayerState

data class QuestGenerationContext(
    val player: PlayerState,
    val unlockedTierIds: Set<String>,
    val accessibleMonsterIds: Set<String>,
    val accessibleMonsterTags: Set<String>,
    val availableItemIds: Set<String>,
    val craftableRecipes: List<CraftRecipeDef>,
    val gatherableNodes: List<GatherNodeDef>,
    val craftingEnabled: Boolean,
    val gatheringEnabled: Boolean,
    val dungeonEnabled: Boolean
)

internal data class TargetResolution(
    val targetId: String? = null,
    val targetTag: String? = null,
    val targetName: String = "",
    val difficultyFactor: Double = 1.0
)
