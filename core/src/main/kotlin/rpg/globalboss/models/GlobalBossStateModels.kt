package rpg.globalboss.models

import kotlinx.serialization.Serializable

@Serializable
data class GlobalBossEventProgress(
    val eventId: String = "",
    val cycleAnchorEpochMs: Long = 0L,
    val dailyAnchorEpochDay: Long = Long.MIN_VALUE,
    val totalDamage: Double = 0.0,
    val totalPoints: Long = 0,
    val bestRun: Long = 0,
    val runsUsed: Int = 0,
    val cycleRunsUsed: Int = 0,
    val dailyFreeRunsUsed: Int = 0,
    val dailyPaidRunsBought: Int = 0,
    val dailyPaidRunsUsed: Int = 0,
    val milestones: Set<String> = emptySet(),
    val claimedMilestones: Set<String> = emptySet(),
    val milestoneClaimedAtEpochMs: Map<String, Long> = emptyMap(),
    val milestoneClaimMigrationDone: Boolean = false,
    val quests: Set<String> = emptySet()
)

@Serializable
data class GlobalBossState(
    val events: Map<String, GlobalBossEventProgress> = emptyMap()
)
