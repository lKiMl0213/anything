package rpg.globalboss.config

import kotlinx.serialization.Serializable

@Serializable
enum class GlobalBossCadence {
    WEEKLY,
    MONTHLY
}

@Serializable
enum class GlobalBossQuestObjectiveType {
    TOTAL_POINTS,
    SINGLE_RUN_POINTS,
    RUNS_COMPLETED
}

@Serializable
data class GlobalBossRewardDef(
    val xp: Int = 0,
    val gold: Int = 0,
    val questCurrency: Int = 0,
    val premiumCash: Int = 0
)

@Serializable
data class GlobalBossEventBalanceDef(
    val baseDamageMultiplierPct: Double = 100.0,
    val baseDefenseMultiplierPct: Double = 100.0,
    val baseHpMultiplierPct: Double = 100.0,
    val baseDamageReductionPct: Double = 0.0,
    val turnScaleMultiplierPct: Double = 100.0,
    val damageScaleMultiplierPct: Double = 100.0,
    val rewardMultiplierPct: Double = 100.0
)

@Serializable
data class GlobalBossMilestoneDef(
    val id: String,
    val pointsRequired: Long,
    val reward: GlobalBossRewardDef = GlobalBossRewardDef()
)

@Serializable
data class GlobalBossQuestDef(
    val id: String,
    val title: String,
    val description: String = "",
    val objective: GlobalBossQuestObjectiveType = GlobalBossQuestObjectiveType.TOTAL_POINTS,
    val targetValue: Long = 0,
    val reward: GlobalBossRewardDef = GlobalBossRewardDef()
)

@Serializable
data class GlobalBossEventDef(
    val id: String,
    val cadence: GlobalBossCadence,
    val title: String,
    val description: String = "",
    val bossArchetypeId: String,
    val tierId: String = "tier1",
    val levelOffset: Int = 0,
    val minBossLevel: Int = 1,
    val maxBossLevel: Int = 1000,
    val balance: GlobalBossEventBalanceDef = GlobalBossEventBalanceDef(),
    val milestones: List<GlobalBossMilestoneDef> = emptyList(),
    val quests: List<GlobalBossQuestDef> = emptyList()
)

@Serializable
data class GlobalBossRunLimitConfig(
    val freeRunsPerDay: Int = 2,
    val purchasableRunsPerDay: Int = 3,
    val maxRunsPerDay: Int = 5,
    val purchasedRunCashCost: Int = 2,
    val weeklyPurchasedRunCashCost: Int? = null,
    val monthlyPurchasedRunCashCost: Int? = null
) {
    fun runCashCost(cadence: GlobalBossCadence): Int {
        val fallback = purchasedRunCashCost.coerceAtLeast(1)
        return when (cadence) {
            GlobalBossCadence.WEEKLY -> (weeklyPurchasedRunCashCost ?: fallback).coerceAtLeast(1)
            GlobalBossCadence.MONTHLY -> (monthlyPurchasedRunCashCost ?: fallback).coerceAtLeast(1)
        }
    }
}

@Serializable
data class GlobalBossScalingConfig(
    val turnStepActions: Int = 4,
    val turnScalePctPerStep: Double = 2.0,
    val damageStep: Double = 2500.0,
    val damageScalePctPerStep: Double = 1.5,
    val maxScalePct: Double = 400.0
)

@Serializable
data class GlobalBossSystemConfig(
    val pointsDamageDivisor: Double = 10.0,
    val runLimits: GlobalBossRunLimitConfig = GlobalBossRunLimitConfig(),
    val scaling: GlobalBossScalingConfig = GlobalBossScalingConfig()
)
