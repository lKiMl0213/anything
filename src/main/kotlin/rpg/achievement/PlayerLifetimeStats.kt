package rpg.achievement

import kotlinx.serialization.Serializable

@Serializable
data class PlayerLifetimeStats(
    val totalGoldEarned: Long = 0L,
    val totalGoldSpent: Long = 0L,
    val totalDeaths: Long = 0L,
    val totalFullRestSleeps: Long = 0L,
    val totalBattlesWon: Long = 0L,
    val totalBattlesLost: Long = 0L,
    val totalBossesKilled: Long = 0L,
    val totalDamageDealt: Double = 0.0,
    val totalDamageTaken: Double = 0.0,
    val totalCriticalHits: Long = 0L,
    val totalQuestsCompleted: Long = 0L,
    val totalSubclassUnlocks: Long = 0L,
    val totalSpecializationUnlocks: Long = 0L,
    val totalClassResetTriggers: Long = 0L,
    val totalMonstersKilled: Long = 0L,
    val killsByBaseType: Map<String, Long> = emptyMap(),
    val killsByStar: Map<Int, Long> = emptyMap(),
    val highestStarByBaseType: Map<String, Int> = emptyMap(),
    val customCounters: Map<String, Long> = emptyMap()
)
