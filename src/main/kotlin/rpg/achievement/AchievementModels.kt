package rpg.achievement

import kotlinx.serialization.Serializable

@Serializable
data class AchievementProgress(
    val id: String = "",
    val currentTierIndex: Int = 0,
    val currentValue: Long = 0L,
    val timesCompleted: Int = 0,
    val rewardAvailable: Boolean = false,
    val capped: Boolean = false,
    val maxTierReached: Boolean = false
)

enum class AchievementCategory(val label: String) {
    MORTE("MORTE"),
    DESCANSO("DESCANSO"),
    OURO("OURO"),
    MOBS("MOBS"),
    ESTRELAS("ESTRELAS"),
    COMBATE("COMBATE"),
    PROGRESSAO("PROGRESSAO"),
    OCULTA("OCULTA")
}

sealed interface AchievementTrackedStat {
    data class LifetimeKey(val key: String) : AchievementTrackedStat
    data class KillsByBaseType(val baseType: String) : AchievementTrackedStat
    data class KillsByStar(val star: Int) : AchievementTrackedStat
    data class CustomCounter(val namespace: String, val key: String) : AchievementTrackedStat
}

data class AchievementRewardScaling(
    val goldByTier: List<Int> = listOf(100, 250, 500, 1000, 2500),
    val fallbackGrowthFactor: Double = 2.0
) {
    fun rewardForTier(tierIndex: Int): Int {
        if (tierIndex < 0) return 0
        if (goldByTier.isEmpty()) return 0
        if (tierIndex < goldByTier.size) {
            return goldByTier[tierIndex].coerceAtLeast(0)
        }
        var reward = goldByTier.last().toDouble().coerceAtLeast(0.0)
        repeat(tierIndex - goldByTier.lastIndex) {
            reward *= fallbackGrowthFactor.coerceAtLeast(1.0)
        }
        return reward.toInt().coerceAtLeast(0)
    }
}

data class AchievementTier(
    val index: Int,
    val target: Long,
    val rewardGold: Int
)

data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val trackedStat: AchievementTrackedStat,
    val tierTargets: List<Long>,
    val rewardScaling: AchievementRewardScaling = AchievementRewardScaling(),
    val isHidden: Boolean = false,
    val capped: Boolean = true
)

enum class AchievementStatus(val label: String) {
    IN_PROGRESS("Em progresso"),
    TIER_COMPLETED("Concluido no tier atual"),
    MAX("MAX")
}

data class AchievementView(
    val id: String,
    val category: AchievementCategory,
    val displayName: String,
    val displayDescription: String,
    val currentValue: Long,
    val currentTierTarget: Long?,
    val timesCompleted: Int,
    val nextRewardGold: Int?,
    val rewardAvailable: Boolean,
    val status: AchievementStatus,
    val currentTierIndex: Int,
    val isHidden: Boolean,
    val maxTierReached: Boolean,
    val capped: Boolean
)

data class AchievementTierUnlockedNotification(
    val achievementId: String,
    val displayName: String,
    val displayDescription: String,
    val rewardGold: Int
)

data class AchievementSyncResult(
    val player: rpg.model.PlayerState,
    val unlockedTiers: List<AchievementTierUnlockedNotification> = emptyList()
)

data class AchievementClaimResult(
    val success: Boolean,
    val message: String,
    val player: rpg.model.PlayerState,
    val rewardGold: Int = 0,
    val unlockedTiers: List<AchievementTierUnlockedNotification> = emptyList()
)

data class AchievementListResult(
    val player: rpg.model.PlayerState,
    val views: List<AchievementView>
)

data class AchievementStatisticsView(
    val player: rpg.model.PlayerState,
    val generalLines: List<String>,
    val killsByStarLines: List<String>,
    val bestiaryLines: List<String>
)

