package rpg.achievement

import kotlin.math.max
import rpg.model.PlayerState

internal class AchievementProgressSupport(
    private val definitions: List<AchievementDefinition>
) {
    fun toView(definition: AchievementDefinition, progress: AchievementProgress): AchievementView {
        val tierIndex = progress.currentTierIndex
        val nextTarget = if (tierIndex in definition.tierTargets.indices) {
            definition.tierTargets[tierIndex]
        } else {
            null
        }
        val nextReward = if (tierIndex in definition.tierTargets.indices) {
            definition.rewardScaling.rewardForTier(tierIndex).coerceAtLeast(0)
        } else {
            null
        }
        val revealed = !definition.isHidden || progress.timesCompleted > 0 || progress.rewardAvailable
        val displayName = if (revealed) definition.name else "[???]"
        val displayDescription = if (revealed) definition.description else "[???]"
        val status = when {
            progress.maxTierReached || progress.currentTierIndex >= definition.tierTargets.size -> AchievementStatus.MAX
            progress.rewardAvailable -> AchievementStatus.TIER_COMPLETED
            else -> AchievementStatus.IN_PROGRESS
        }
        return AchievementView(
            id = definition.id,
            category = definition.category,
            displayName = displayName,
            displayDescription = displayDescription,
            currentValue = progress.currentValue,
            currentTierTarget = nextTarget,
            timesCompleted = progress.timesCompleted,
            nextRewardGold = nextReward,
            rewardAvailable = progress.rewardAvailable,
            status = status,
            currentTierIndex = progress.currentTierIndex,
            isHidden = definition.isHidden,
            maxTierReached = progress.maxTierReached,
            capped = progress.capped
        )
    }

    fun normalizeProgress(
        progress: AchievementProgress,
        id: String,
        tierCount: Int
    ): AchievementProgress {
        val clampedTier = progress.currentTierIndex.coerceIn(0, max(0, tierCount))
        val maxReached = progress.maxTierReached || clampedTier >= tierCount
        return progress.copy(
            id = id,
            currentTierIndex = clampedTier,
            currentValue = progress.currentValue.coerceAtLeast(0L),
            timesCompleted = progress.timesCompleted.coerceAtLeast(0),
            rewardAvailable = if (clampedTier >= tierCount) false else progress.rewardAvailable,
            maxTierReached = maxReached,
            capped = if (clampedTier >= tierCount) true else progress.capped
        )
    }

    fun ensureKnownAchievements(player: PlayerState): PlayerState {
        val progress = player.achievementProgressById.toMutableMap()
        var changed = false
        for (definition in definitions) {
            if (!progress.containsKey(definition.id)) {
                progress[definition.id] = AchievementProgress(id = definition.id)
                changed = true
            }
        }
        return if (changed) {
            player.copy(achievementProgressById = progress.toMap())
        } else {
            player
        }
    }

    fun trackedValue(
        stats: PlayerLifetimeStats,
        trackedStat: AchievementTrackedStat
    ): Long {
        return when (trackedStat) {
            is AchievementTrackedStat.LifetimeKey -> when (trackedStat.key) {
                AchievementStatKeys.TOTAL_GOLD_EARNED -> stats.totalGoldEarned
                AchievementStatKeys.TOTAL_GOLD_SPENT -> stats.totalGoldSpent
                AchievementStatKeys.TOTAL_DEATHS -> stats.totalDeaths
                AchievementStatKeys.TOTAL_FULL_REST_SLEEPS -> stats.totalFullRestSleeps
                AchievementStatKeys.TOTAL_BATTLES_WON -> stats.totalBattlesWon
                AchievementStatKeys.TOTAL_BATTLES_LOST -> stats.totalBattlesLost
                AchievementStatKeys.TOTAL_BOSSES_KILLED -> stats.totalBossesKilled
                AchievementStatKeys.TOTAL_CRITICAL_HITS -> stats.totalCriticalHits
                AchievementStatKeys.TOTAL_QUESTS_COMPLETED -> stats.totalQuestsCompleted
                AchievementStatKeys.TOTAL_SUBCLASS_UNLOCKS -> stats.totalSubclassUnlocks
                AchievementStatKeys.TOTAL_SPECIALIZATION_UNLOCKS -> stats.totalSpecializationUnlocks
                AchievementStatKeys.TOTAL_CLASS_RESET_TRIGGERS -> stats.totalClassResetTriggers
                AchievementStatKeys.TOTAL_MONSTERS_KILLED -> stats.totalMonstersKilled
                else -> 0L
            }
            is AchievementTrackedStat.KillsByBaseType -> {
                stats.killsByBaseType[normalizeBaseType(trackedStat.baseType)] ?: 0L
            }
            is AchievementTrackedStat.KillsByStar -> {
                stats.killsByStar[trackedStat.star.coerceIn(0, 7)] ?: 0L
            }
            is AchievementTrackedStat.CustomCounter -> {
                val key = customCounterKey(trackedStat.namespace, trackedStat.key)
                stats.customCounters[key] ?: 0L
            }
        }
    }

    fun buildTierUnlockedNotification(
        definition: AchievementDefinition,
        tierIndex: Int
    ): AchievementTierUnlockedNotification {
        val reward = definition.rewardScaling.rewardForTier(tierIndex).coerceAtLeast(0)
        return AchievementTierUnlockedNotification(
            achievementId = definition.id,
            displayName = definition.name,
            displayDescription = definition.description,
            rewardGold = reward
        )
    }

    private fun normalizeBaseType(baseType: String): String {
        return baseType.trim().lowercase().ifBlank { "unknown" }
    }

    private fun customCounterKey(namespace: String, key: String): String {
        val left = namespace.trim().lowercase().ifBlank { "global" }
        val right = key.trim().lowercase().ifBlank { "counter" }
        return "$left:$right"
    }
}
