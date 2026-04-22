package rpg.achievement

class AchievementService {
    private val definitions: List<AchievementDefinition> = AchievementDefinitionCatalog.buildDefinitions()
    private val definitionsById: Map<String, AchievementDefinition> = definitions.associateBy { it.id }
    private val progressSupport = AchievementProgressSupport(definitions)

    fun allDefinitions(): List<AchievementDefinition> = definitions

    fun synchronize(
        player: rpg.model.PlayerState,
        emitNotifications: Boolean = false
    ): AchievementSyncResult {
        val basePlayer = progressSupport.ensureKnownAchievements(player)
        val progressMap = basePlayer.achievementProgressById.toMutableMap()
        val unlocked = mutableListOf<AchievementTierUnlockedNotification>()

        for (definition in definitions) {
            val current = progressMap[definition.id] ?: AchievementProgress(id = definition.id)
            val normalizedCurrent = progressSupport.normalizeProgress(current, definition.id, definition.tierTargets.size)
            val value = progressSupport.trackedValue(basePlayer.lifetimeStats, definition.trackedStat).coerceAtLeast(0L)

            var next = normalizedCurrent.copy(currentValue = value)
            if (next.currentTierIndex >= definition.tierTargets.size) {
                next = next.copy(
                    rewardAvailable = false,
                    maxTierReached = true,
                    capped = definition.capped
                )
            } else {
                val target = definition.tierTargets[next.currentTierIndex]
                var rewardReady = next.rewardAvailable
                if (!rewardReady && value >= target) {
                    rewardReady = true
                    if (emitNotifications) {
                        unlocked += progressSupport.buildTierUnlockedNotification(definition, next.currentTierIndex)
                    }
                }
                if (rewardReady && value < target) {
                    rewardReady = false
                }
                next = next.copy(
                    rewardAvailable = rewardReady,
                    maxTierReached = false,
                    capped = false
                )
            }
            progressMap[definition.id] = next
        }

        return AchievementSyncResult(
            player = basePlayer.copy(achievementProgressById = progressMap.toMap()),
            unlockedTiers = unlocked
        )
    }

    fun hasClaimableRewards(player: rpg.model.PlayerState): Boolean {
        val synced = synchronize(player, emitNotifications = false).player
        return definitions.any { definition ->
            synced.achievementProgressById[definition.id]?.rewardAvailable == true
        }
    }

    fun buildAchievementList(player: rpg.model.PlayerState): AchievementListResult {
        val sync = synchronize(player, emitNotifications = false)
        val views = definitions.mapNotNull { definition ->
            val progress = sync.player.achievementProgressById[definition.id] ?: return@mapNotNull null
            progressSupport.toView(definition, progress)
        }
        return AchievementListResult(sync.player, views)
    }

    fun claimReward(player: rpg.model.PlayerState, achievementId: String): AchievementClaimResult {
        val syncBefore = synchronize(player, emitNotifications = false)
        val syncedPlayer = syncBefore.player
        val definition = definitionsById[achievementId]
            ?: return AchievementClaimResult(
                success = false,
                message = "Conquista nao encontrada.",
                player = syncedPlayer
            )

        val currentProgress = syncedPlayer.achievementProgressById[achievementId]
            ?: AchievementProgress(id = achievementId)
        if (currentProgress.maxTierReached || currentProgress.currentTierIndex >= definition.tierTargets.size) {
            return AchievementClaimResult(
                success = false,
                message = "Conquista ja atingiu o cap MAX.",
                player = syncedPlayer
            )
        }
        if (!currentProgress.rewardAvailable) {
            return AchievementClaimResult(
                success = false,
                message = "Nenhuma recompensa disponivel para resgate.",
                player = syncedPlayer
            )
        }

        val rewardGold = definition.rewardScaling.rewardForTier(currentProgress.currentTierIndex).coerceAtLeast(0)
        val updatedStats = syncedPlayer.lifetimeStats.copy(
            totalGoldEarned = syncedPlayer.lifetimeStats.totalGoldEarned + rewardGold
        )

        val nextTierIndex = currentProgress.currentTierIndex + 1
        val reachedMax = nextTierIndex >= definition.tierTargets.size
        val advancedProgress = currentProgress.copy(
            currentTierIndex = nextTierIndex,
            timesCompleted = currentProgress.timesCompleted + 1,
            rewardAvailable = false,
            maxTierReached = reachedMax,
            capped = reachedMax && definition.capped
        )

        val playerAfterClaim = syncedPlayer.copy(
            gold = syncedPlayer.gold + rewardGold,
            lifetimeStats = updatedStats,
            achievementProgressById = syncedPlayer.achievementProgressById + (achievementId to advancedProgress)
        )
        val syncAfter = synchronize(playerAfterClaim, emitNotifications = true)
        return AchievementClaimResult(
            success = true,
            message = "Recompensa resgatada: +$rewardGold ouro.",
            player = syncAfter.player,
            rewardGold = rewardGold,
            unlockedTiers = syncAfter.unlockedTiers
        )
    }

    object StatKeys {
        const val TOTAL_GOLD_EARNED = AchievementStatKeys.TOTAL_GOLD_EARNED
        const val TOTAL_GOLD_SPENT = AchievementStatKeys.TOTAL_GOLD_SPENT
        const val TOTAL_DEATHS = AchievementStatKeys.TOTAL_DEATHS
        const val TOTAL_FULL_REST_SLEEPS = AchievementStatKeys.TOTAL_FULL_REST_SLEEPS
        const val TOTAL_BATTLES_WON = AchievementStatKeys.TOTAL_BATTLES_WON
        const val TOTAL_BATTLES_LOST = AchievementStatKeys.TOTAL_BATTLES_LOST
        const val TOTAL_BOSSES_KILLED = AchievementStatKeys.TOTAL_BOSSES_KILLED
        const val TOTAL_CRITICAL_HITS = AchievementStatKeys.TOTAL_CRITICAL_HITS
        const val TOTAL_QUESTS_COMPLETED = AchievementStatKeys.TOTAL_QUESTS_COMPLETED
        const val TOTAL_SUBCLASS_UNLOCKS = AchievementStatKeys.TOTAL_SUBCLASS_UNLOCKS
        const val TOTAL_SPECIALIZATION_UNLOCKS = AchievementStatKeys.TOTAL_SPECIALIZATION_UNLOCKS
        const val TOTAL_CLASS_RESET_TRIGGERS = AchievementStatKeys.TOTAL_CLASS_RESET_TRIGGERS
        const val TOTAL_MONSTERS_KILLED = AchievementStatKeys.TOTAL_MONSTERS_KILLED
    }
}
