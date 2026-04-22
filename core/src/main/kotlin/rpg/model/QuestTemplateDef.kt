package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class QuestTier {
    DAILY,
    WEEKLY,
    MONTHLY,
    ACCEPTED
}

@Serializable
enum class QuestObjectiveType {
    KILL_MONSTER,
    KILL_TAG,
    COLLECT_ITEM,
    CRAFT_ITEM,
    GATHER_RESOURCE,
    REACH_FLOOR,
    COMPLETE_RUN
}

@Serializable
enum class QuestTargetType {
    MONSTER_ID,
    MONSTER_TAG,
    ITEM_ID,
    RESOURCE_ID,
    FLOOR,
    RUN
}

@Serializable
data class IntRangeDef(
    val min: Int = 1,
    val max: Int = 1
)

@Serializable
data class QuestRewardItemDef(
    val itemId: String,
    val chancePct: Double = 100.0,
    val minQty: Int = 1,
    val maxQty: Int = 1
)

@Serializable
data class QuestRewardProfileDef(
    val xpBase: Int = 10,
    val goldBase: Int = 5,
    val effortMultiplier: Double = 1.0,
    val itemRewards: List<QuestRewardItemDef> = emptyList(),
    val specialCurrencyBase: Int = 0
)

@Serializable
data class QuestConstraintDef(
    val minPlayerLevel: Int = 1,
    val maxPlayerLevel: Int = 999,
    val requiresCrafting: Boolean = false,
    val requiresGathering: Boolean = false,
    val requiresDungeon: Boolean = true,
    val requiresUnlockedTierId: String? = null,
    val requiresMonsterTag: String? = null,
    val requiresRecipeForTarget: Boolean = false,
    val consumeTargetOnComplete: Boolean = false
)

@Serializable
data class QuestTemplateDef(
    val id: String,
    val category: String = "",
    val supportedTiers: List<QuestTier> = listOf(QuestTier.ACCEPTED),
    val objectiveType: QuestObjectiveType,
    val targetType: QuestTargetType,
    val targetId: String? = null,
    val targetTag: String? = null,
    val amountRange: IntRangeDef = IntRangeDef(),
    val rewardProfile: QuestRewardProfileDef = QuestRewardProfileDef(),
    val constraints: QuestConstraintDef = QuestConstraintDef(),
    val titleTemplate: String = "",
    val descriptionTemplate: String = "",
    val hintTemplate: String = "",
    val enabled: Boolean = true,
    val weight: Int = 1
)
