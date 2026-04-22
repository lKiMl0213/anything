package rpg.achievement

import kotlin.math.max
import rpg.combat.CombatTelemetry
import rpg.model.PlayerState

data class AchievementUpdate(
    val player: PlayerState,
    val unlockedTiers: List<AchievementTierUnlockedNotification> = emptyList()
)

class AchievementTracker(private val service: AchievementService) {
    fun synchronize(player: PlayerState): PlayerState {
        return service.synchronize(player, emitNotifications = false).player
    }

    fun onGoldEarned(player: PlayerState, amount: Long): AchievementUpdate {
        if (amount <= 0L) return syncOnly(player)
        return update(player) { stats ->
            stats.copy(totalGoldEarned = stats.totalGoldEarned + amount)
        }
    }

    fun onGoldSpent(player: PlayerState, amount: Long): AchievementUpdate {
        if (amount <= 0L) return syncOnly(player)
        return update(player) { stats ->
            stats.copy(totalGoldSpent = stats.totalGoldSpent + amount)
        }
    }

    fun onDeath(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalDeaths = stats.totalDeaths + 1)
        }
    }

    fun onFullRestSleep(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalFullRestSleeps = stats.totalFullRestSleeps + 1)
        }
    }

    fun onQuestCompleted(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalQuestsCompleted = stats.totalQuestsCompleted + 1)
        }
    }

    fun onSubclassUnlocked(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalSubclassUnlocks = stats.totalSubclassUnlocks + 1)
        }
    }

    fun onSpecializationUnlocked(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalSpecializationUnlocks = stats.totalSpecializationUnlocks + 1)
        }
    }

    fun onClassResetTriggered(player: PlayerState): AchievementUpdate {
        return update(player) { stats ->
            stats.copy(totalClassResetTriggers = stats.totalClassResetTriggers + 1)
        }
    }

    fun onBattleResolved(
        player: PlayerState,
        telemetry: CombatTelemetry,
        victory: Boolean,
        escaped: Boolean,
        isBoss: Boolean,
        monsterBaseType: String,
        monsterStars: Int
    ): AchievementUpdate {
        return update(player) { stats ->
            val dealt = telemetry.playerDamageDealt.coerceAtLeast(0.0)
            val taken = telemetry.playerDamageTaken.coerceAtLeast(0.0)
            val crits = telemetry.playerCriticalHits.coerceAtLeast(0)

            var next = stats.copy(
                totalDamageDealt = stats.totalDamageDealt + dealt,
                totalDamageTaken = stats.totalDamageTaken + taken,
                totalCriticalHits = stats.totalCriticalHits + crits
            )

            if (victory) {
                val normalizedBaseType = normalizeBaseType(monsterBaseType)
                val starBucket = monsterStars.coerceIn(0, 7)
                val byBase = incrementStringCounter(next.killsByBaseType, normalizedBaseType, 1L)
                val byStar = incrementStarCounter(next.killsByStar, starBucket, 1L)
                val maxByBase = next.highestStarByBaseType.toMutableMap()
                val currentMax = maxByBase[normalizedBaseType] ?: 0
                maxByBase[normalizedBaseType] = max(currentMax, starBucket)
                next = next.copy(
                    totalBattlesWon = next.totalBattlesWon + 1,
                    totalMonstersKilled = next.totalMonstersKilled + 1,
                    totalBossesKilled = if (isBoss) next.totalBossesKilled + 1 else next.totalBossesKilled,
                    killsByBaseType = byBase,
                    killsByStar = byStar,
                    highestStarByBaseType = maxByBase.toMap()
                )
            } else if (!escaped) {
                next = next.copy(totalBattlesLost = next.totalBattlesLost + 1)
            }
            next
        }
    }

    fun onCustomCounterIncrement(
        player: PlayerState,
        namespace: String,
        key: String,
        amount: Long = 1L
    ): AchievementUpdate {
        if (amount <= 0L) return syncOnly(player)
        return update(player) { stats ->
            val counterKey = customCounterKey(namespace, key)
            val nextValue = (stats.customCounters[counterKey] ?: 0L) + amount
            stats.copy(customCounters = stats.customCounters + (counterKey to nextValue))
        }
    }

    private fun update(
        player: PlayerState,
        mutator: (PlayerLifetimeStats) -> PlayerLifetimeStats
    ): AchievementUpdate {
        val syncedPlayer = service.synchronize(player, emitNotifications = false).player
        val updatedPlayer = syncedPlayer.copy(
            lifetimeStats = mutator(syncedPlayer.lifetimeStats)
        )
        val sync = service.synchronize(updatedPlayer, emitNotifications = true)
        return AchievementUpdate(sync.player, sync.unlockedTiers)
    }

    private fun syncOnly(player: PlayerState): AchievementUpdate {
        val synced = service.synchronize(player, emitNotifications = false)
        return AchievementUpdate(synced.player)
    }

    private fun normalizeBaseType(baseType: String): String {
        return baseType.trim().lowercase().ifBlank { "unknown" }
    }

    private fun customCounterKey(namespace: String, key: String): String {
        val left = namespace.trim().lowercase().ifBlank { "global" }
        val right = key.trim().lowercase().ifBlank { "counter" }
        return "$left:$right"
    }

    private fun incrementStringCounter(
        source: Map<String, Long>,
        key: String,
        amount: Long
    ): Map<String, Long> {
        if (amount <= 0L) return source
        val normalized = key.trim().lowercase().ifBlank { "unknown" }
        val next = (source[normalized] ?: 0L) + amount
        return source + (normalized to next)
    }

    private fun incrementStarCounter(
        source: Map<Int, Long>,
        star: Int,
        amount: Long
    ): Map<Int, Long> {
        if (amount <= 0L) return source
        val bucket = star.coerceIn(0, 7)
        val next = (source[bucket] ?: 0L) + amount
        return source + (bucket to next)
    }
}

