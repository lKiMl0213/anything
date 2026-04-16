package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class PowerWeightDef(
    val hp: Double = 0.25,
    val damage: Double = 1.0,
    val defense: Double = 0.5,
    val attackSpeed: Double = 4.0,
    val crit: Double = 0.1
)

@Serializable
data class StarTagDef(
    val minStars: Int,
    val label: String,
    val format: String = "{name} {label} ({stars}*)"
)

@Serializable
data class SpeedSoftCapDef(
    val threshold1: Double = 100.0,
    val threshold2: Double = 200.0,
    val multiplier1: Double = 0.5,
    val multiplier2: Double = 0.25
)

@Serializable
data class CombatBalanceDef(
    val tickSeconds: Double = 0.1,
    val actionThreshold: Double = 100.0,
    val speedNormalization: Double = 4.0,
    val speedScale: Double = 100.0,
    val globalCooldownSeconds: Double = 0.4,
    val softCap: SpeedSoftCapDef = SpeedSoftCapDef()
)

@Serializable
data class StarRewardBalanceDef(
    val xpPerStarPct: Double = 5.0,
    val goldPerStarPct: Double = 4.0,
    val dropPerStarPct: Double = 3.5,
    val maxXpBonusPct: Double = 55.0,
    val maxGoldBonusPct: Double = 45.0,
    val maxDropBonusPct: Double = 40.0
) {
    fun xpMultiplier(stars: Int): Double {
        val bonusPct = (stars.coerceAtLeast(0) * xpPerStarPct).coerceAtMost(maxXpBonusPct)
        return 1.0 + (bonusPct / 100.0)
    }

    fun goldMultiplier(stars: Int): Double {
        val bonusPct = (stars.coerceAtLeast(0) * goldPerStarPct).coerceAtMost(maxGoldBonusPct)
        return 1.0 + (bonusPct / 100.0)
    }

    fun dropMultiplier(stars: Int): Double {
        val bonusPct = (stars.coerceAtLeast(0) * dropPerStarPct).coerceAtMost(maxDropBonusPct)
        return 1.0 + (bonusPct / 100.0)
    }
}

@Serializable
data class GameBalanceDef(
    val powerWeights: PowerWeightDef = PowerWeightDef(),
    val xpPowerScale: Double = 0.40,
    val goldPowerScale: Double = 0.30,
    val difficultyMin: Double = 0.40,
    val difficultyMax: Double = 1.60,
    val antiFarmLevelGap: Int = 5,
    val antiFarmXpMultiplier: Double = 0.40,
    val antiFarmDropMultiplier: Double = 0.40,
    val antiFarmGoldMultiplier: Double = 0.50,
    val talentPoints: TalentPointPolicy = TalentPointPolicy(),
    val combat: CombatBalanceDef = CombatBalanceDef(),
    val starRewards: StarRewardBalanceDef = StarRewardBalanceDef(),
    val starTags: List<StarTagDef> = emptyList()
)
