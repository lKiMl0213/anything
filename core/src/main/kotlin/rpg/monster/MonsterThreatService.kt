package rpg.monster

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.model.CombatStatusApplyDef
import rpg.model.DungeonRun
import rpg.model.MapTierDef
import rpg.status.StatusType

internal class MonsterThreatService(
    private val rng: Random
) {
    fun buildThreatProfile(
        tier: MapTierDef,
        run: DungeonRun,
        isBoss: Boolean
    ): MonsterThreatProfile {
        val winsFactor = (run.victoriesInRun / 45.0).coerceIn(0.0, 1.0)
        val depthFactor = (run.depth / 55.0).coerceIn(0.0, 1.0)
        val roomFactor = (run.roomsCleared / 60.0).coerceIn(0.0, 1.0)
        val difficultyFactor = ((run.difficultyLevel - 1) / 14.0).coerceIn(0.0, 1.0)
        val mutationFactor = (run.mutationTier / 7.0).coerceIn(0.0, 1.0)
        val tierFactor = if (tier.isInfinite) {
            1.0
        } else {
            (tier.recommendedLevel / 35.0).coerceIn(0.0, 1.0)
        }

        val progression = (
            0.08 +
                winsFactor * 0.26 +
                depthFactor * 0.14 +
                roomFactor * 0.05 +
                difficultyFactor * 0.14 +
                mutationFactor * 0.08 +
                tierFactor * 0.25
            ).coerceIn(0.0, 1.0)

        val varianceScale = (
            0.60 +
                progression * 0.70 +
                if (isBoss) 0.12 else 0.0
            ).coerceIn(0.55, 1.45)

        val statusScale = (
            0.72 +
                progression * 0.48 +
                if (isBoss) 0.08 else 0.0
            ).coerceIn(0.65, 1.30)

        val maxModifierCount = when {
            progression < 0.18 -> 0
            progression < 0.42 -> 1
            progression < 0.70 -> 2
            else -> 3
        } + if (isBoss && progression >= 0.80) 1 else 0

        val starCapBase = when {
            progression < 0.14 -> 2
            progression < 0.30 -> 3
            progression < 0.50 -> 4
            progression < 0.68 -> 5
            progression < 0.86 -> 6
            else -> 7
        }
        val starCap = (starCapBase + if (isBoss) 1 else 0).coerceIn(1, 7)

        return MonsterThreatProfile(
            progression = progression,
            varianceScale = varianceScale,
            statusScale = statusScale,
            rarityPressure = progression,
            maxModifierCount = maxModifierCount.coerceIn(0, 5),
            starCap = starCap
        )
    }

    fun monsterLevel(tier: MapTierDef, run: DungeonRun, playerLevel: Int): Int {
        val base = if (tier.isInfinite) {
            max(tier.baseMonsterLevel, (playerLevel * 1.1).roundToInt())
        } else {
            tier.baseMonsterLevel
        }
        val scaled = base + (run.difficultyLevel - 1)
        return max(1, (scaled * tier.difficultyMultiplier).roundToInt())
    }

    fun rollRarity(
        difficulty: Int,
        isBoss: Boolean,
        rarityPressure: Double
    ): MonsterRarity {
        val pressure = rarityPressure.coerceIn(0.0, 1.0)
        val difficultyBonus = (difficulty - 1).coerceAtLeast(0).toDouble()
        val weights = mutableMapOf(
            MonsterRarity.COMMON to (68.0 - pressure * 28.0),
            MonsterRarity.ELITE to (18.0 + pressure * 8.0 + difficultyBonus * 0.8),
            MonsterRarity.RARE to (9.0 + pressure * 8.0 + difficultyBonus * 0.7),
            MonsterRarity.EPIC to (4.0 + pressure * 6.0 + difficultyBonus * 0.45),
            MonsterRarity.LEGENDARY to (1.0 + pressure * 4.0 + difficultyBonus * 0.25)
        )

        if (!isBoss && pressure < 0.2) {
            weights[MonsterRarity.EPIC] = minOf(weights[MonsterRarity.EPIC] ?: 1.0, 2.0)
            weights[MonsterRarity.LEGENDARY] = minOf(weights[MonsterRarity.LEGENDARY] ?: 1.0, 0.8)
        }
        if (isBoss) {
            weights[MonsterRarity.COMMON] = (weights[MonsterRarity.COMMON] ?: 1.0) * 0.55
            weights[MonsterRarity.ELITE] = (weights[MonsterRarity.ELITE] ?: 1.0) + 6.0
            weights[MonsterRarity.RARE] = (weights[MonsterRarity.RARE] ?: 1.0) + 6.0
            weights[MonsterRarity.EPIC] = (weights[MonsterRarity.EPIC] ?: 1.0) + 4.0
            weights[MonsterRarity.LEGENDARY] = (weights[MonsterRarity.LEGENDARY] ?: 1.0) + 2.0
        }

        val total = weights.values.sumOf { it.coerceAtLeast(0.1) }
        var roll = rng.nextDouble(0.0, total)
        for (rarity in MonsterRarity.entries) {
            roll -= (weights[rarity] ?: 0.1).coerceAtLeast(0.1)
            if (roll <= 0.0) return rarity
        }
        return MonsterRarity.COMMON
    }

    fun modifierCount(
        rarity: MonsterRarity,
        mutationTier: Int,
        progression: Double,
        maxModifierCount: Int
    ): Int {
        val base = when (rarity) {
            MonsterRarity.COMMON -> 0
            MonsterRarity.ELITE -> 1
            MonsterRarity.RARE -> 1
            MonsterRarity.EPIC -> 2
            MonsterRarity.LEGENDARY -> 3
        }
        val mutationScale = (0.30 + progression * 0.80).coerceIn(0.3, 1.2)
        val mutationBonus = (mutationTier * mutationScale).roundToInt().coerceAtLeast(0)
        return (base + mutationBonus).coerceIn(0, maxModifierCount.coerceAtLeast(0))
    }

    fun scaleArchetypeStatuses(
        statuses: List<CombatStatusApplyDef>,
        profile: MonsterThreatProfile,
        isBoss: Boolean
    ): List<CombatStatusApplyDef> {
        if (statuses.isEmpty()) return emptyList()
        val chanceScale = profile.statusScale
        val effectScale = (0.78 + profile.progression * 0.42 + if (isBoss) 0.08 else 0.0).coerceIn(0.7, 1.35)
        val durationScale = (0.84 + profile.progression * 0.32 + if (isBoss) 0.05 else 0.0).coerceIn(0.75, 1.2)
        return statuses.map { status ->
            val reducedStacks = if (!isBoss && profile.progression < 0.25 && status.stackable) {
                (status.maxStacks - 1).coerceAtLeast(1)
            } else {
                status.maxStacks
            }
            status.copy(
                chancePct = (status.chancePct * chanceScale).coerceIn(1.0, 100.0),
                durationSeconds = (status.durationSeconds * durationScale).coerceAtLeast(0.1),
                effectValue = status.effectValue * effectScale,
                maxStacks = reducedStacks
            )
        }
    }

    fun rollStarStatus(
        stars: Int,
        isBoss: Boolean,
        profile: MonsterThreatProfile
    ): List<CombatStatusApplyDef> {
        if (stars <= 0) return emptyList()
        val starChance = (
            stars * (4.0 + profile.progression * 3.0) +
                if (isBoss) 8.0 else 0.0
            ) * profile.statusScale
        val chance = starChance.coerceAtMost(90.0)
        if (rng.nextDouble(0.0, 100.0) > chance) return emptyList()

        val type = StatusType.entries.random(rng)
        val baseValue = when (type) {
            StatusType.BURNING -> 3.0 + stars * 1.1
            StatusType.POISONED -> 2.5 + stars * 0.9
            StatusType.BLEEDING -> 0.008 + stars * 0.002
            StatusType.FROZEN -> 0.0
            StatusType.PARALYZED -> 8.0 + stars * 2.0
            StatusType.WEAKNESS -> 5.0 + stars * 1.5
            StatusType.SLOW -> 6.0 + stars * 1.4
            StatusType.MARKED -> 1.0
        }
        val potencyScale = (0.78 + profile.progression * 0.55 + if (isBoss) 0.1 else 0.0).coerceIn(0.7, 1.45)
        val duration = when (type) {
            StatusType.FROZEN -> 2.5 + stars * 0.2
            else -> 5.0 + stars * 0.5
        } * (0.84 + profile.progression * 0.35).coerceIn(0.8, 1.2)

        return listOf(
            CombatStatusApplyDef(
                type = type,
                chancePct = ((10.0 + stars * 3.5) * profile.statusScale).coerceIn(4.0, 75.0),
                durationSeconds = duration,
                tickIntervalSeconds = 1.5,
                effectValue = baseValue * potencyScale,
                stackable = type in setOf(StatusType.BURNING, StatusType.POISONED, StatusType.BLEEDING),
                maxStacks = if (type in setOf(StatusType.BURNING, StatusType.POISONED, StatusType.BLEEDING)) {
                    if (!isBoss && profile.progression < 0.25) 2 else 3
                } else {
                    1
                },
                source = "StarTrait$stars"
            )
        )
    }
}
