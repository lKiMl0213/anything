package rpg.hunting

import kotlinx.serialization.Serializable
import kotlin.math.floor

@Serializable
data class HuntingConfig(
    val minSelectedDurationSeconds: Int = 30,
    val maxSelectedDurationSeconds: Int = 3600,
    val durationOptionsSeconds: List<Int> = listOf(30, 60, 120, 180, 300, 600, 900, 1200, 1800),
    val minCycleDurationSeconds: Int = 5,
    val skillCycleReductionPctPerLevel: Double = 0.55,
    val maxSkillCycleReductionPct: Double = 35.0,
    val taskCycleReductionMultiplier: Double = 0.45,
    val quantityBonusPctPerSkillLevel: Double = 1.1,
    val rareChanceBonusPctPerSkillLevel: Double = 0.30,
    val maxRareChanceBonusPct: Double = 14.0,
    val underLevelPenaltyPctPerLevel: Double = 10.0,
    val maxUnderLevelPenaltyPct: Double = 70.0,
    val overLevelBonusPctPerLevel: Double = 2.4,
    val maxOverLevelBonusPct: Double = 20.0,
    val antiFarmLevelGap: Int = 10,
    val antiFarmYieldMultiplier: Double = 0.45,
    val rngMinMultiplier: Double = 0.85,
    val rngMaxMultiplier: Double = 1.20,
    val goldBaseCost: Int = 18,
    val goldPerMinute: Int = 6,
    val goldPerRecommendedLevel: Double = 0.75,
    val goldPerRarityStep: Double = 6.0
) {
    fun normalizeSelectedDurationSeconds(durationSeconds: Int): Int {
        val min = minSelectedDurationSeconds.coerceAtLeast(1)
        val max = maxSelectedDurationSeconds.coerceAtLeast(min)
        return durationSeconds.coerceIn(min, max)
    }

    fun normalizedDurationOptionsSeconds(): List<Int> {
        val min = minSelectedDurationSeconds.coerceAtLeast(1)
        val max = maxSelectedDurationSeconds.coerceAtLeast(min)
        val normalized = durationOptionsSeconds
            .asSequence()
            .map { it.coerceIn(min, max) }
            .filter { it >= min }
            .distinct()
            .sorted()
            .toList()
        return if (normalized.isEmpty()) listOf(min) else normalized
    }

    fun actionDurationSeconds(selectedDurationSeconds: Int): Double {
        return normalizeSelectedDurationSeconds(selectedDurationSeconds).toDouble()
    }

    fun cycleDurationSeconds(baseCycleSeconds: Int, skillLevel: Int, taskEfficiencyPct: Double = 0.0): Double {
        val base = baseCycleSeconds.coerceAtLeast(1).toDouble()
        val skillReduction = ((skillLevel.coerceAtLeast(1) - 1) * skillCycleReductionPctPerLevel)
            .coerceAtMost(maxSkillCycleReductionPct)
        val taskReduction = taskEfficiencyPct.coerceIn(0.0, 80.0) * taskCycleReductionMultiplier
        val totalReductionPct = (skillReduction + taskReduction).coerceIn(0.0, 85.0)
        return (base * (1.0 - totalReductionPct / 100.0))
            .coerceAtLeast(minCycleDurationSeconds.coerceAtLeast(1).toDouble())
    }

    fun resolveCycles(selectedDurationSeconds: Int, cycleDurationSeconds: Double): Int {
        val selected = normalizeSelectedDurationSeconds(selectedDurationSeconds)
        val safeCycle = cycleDurationSeconds.coerceAtLeast(1.0)
        return floor(selected / safeCycle).toInt().coerceAtLeast(0)
    }
}
