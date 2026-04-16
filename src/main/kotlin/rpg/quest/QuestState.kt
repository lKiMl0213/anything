package rpg.quest

import kotlinx.serialization.Serializable
import rpg.model.QuestObjectiveType
import rpg.model.QuestTargetType
import rpg.model.QuestTier

@Serializable
enum class QuestStatus {
    ACTIVE,
    READY_TO_CLAIM,
    CLAIMED,
    CANCELLED
}

@Serializable
data class QuestRewardItem(
    val itemId: String,
    val quantity: Int = 1
)

@Serializable
data class QuestRewardBundle(
    val xp: Int = 0,
    val gold: Int = 0,
    val specialCurrency: Int = 0,
    val items: List<QuestRewardItem> = emptyList()
)

@Serializable
data class QuestInstance(
    val instanceId: String,
    val templateId: String,
    val tier: QuestTier,
    val objectiveType: QuestObjectiveType,
    val targetType: QuestTargetType,
    val generatedTargetId: String? = null,
    val generatedTargetTag: String? = null,
    val generatedTargetName: String = "",
    val title: String = "",
    val description: String = "",
    val hint: String = "",
    val requiredAmount: Int = 1,
    val currentProgress: Int = 0,
    val rewards: QuestRewardBundle = QuestRewardBundle(),
    val createdAt: Long = 0L,
    val expiresAt: Long? = null,
    val acceptedAt: Long? = null,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val canCancel: Boolean = false,
    val sourcePool: String = "",
    val consumeTargetOnComplete: Boolean = false
)

@Serializable
data class QuestBoardState(
    val dailyQuests: List<QuestInstance> = emptyList(),
    val weeklyQuests: List<QuestInstance> = emptyList(),
    val monthlyQuests: List<QuestInstance> = emptyList(),
    val acceptedQuests: List<QuestInstance> = emptyList(),
    val availableAcceptableQuestPool: List<QuestInstance> = emptyList(),
    val completedQuests: List<QuestInstance> = emptyList(),
    val dailyReplaceUsed: Int = 0,
    val weeklyReplaceUsed: Int = 0,
    val monthlyReplaceUsed: Int = 0,
    val lastDailyReset: Long = 0L,
    val lastWeeklyReset: Long = 0L,
    val lastMonthlyReset: Long = 0L,
    val lastAcceptableQuestRoll: Long = 0L
)
