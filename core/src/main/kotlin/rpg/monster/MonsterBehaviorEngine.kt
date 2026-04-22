package rpg.monster

import kotlin.math.min
import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.DerivedStats

data class BehaviorProfile(
    val summonChancePct: Double = 0.0,
    val enrageChancePct: Double = 0.0,
    val evolveChancePct: Double = 0.0,
    val maxSummons: Int = 0,
    val enrageTurns: Int = 0,
    val enrageHpThresholdPct: Double = 60.0,
    val evolveHpThresholdPct: Double = 35.0,
    val enrageMultiplier: DerivedStats = DerivedStats(),
    val evolveMultiplier: DerivedStats = DerivedStats()
)

data class BehaviorTrigger(
    val multiplier: DerivedStats,
    val turns: Int
)

class MonsterBehaviorEngine(
    private val repo: DataRepository,
    private val rng: Random
) {
    fun profile(tags: Set<String>): BehaviorProfile {
        var summonChance = 0.0
        var enrageChance = 0.0
        var evolveChance = 0.0
        var maxSummons = 0
        var enrageTurns = 0
        var enrageHp = 60.0
        var evolveHp = 35.0
        var enrageMultiplier = DerivedStats()
        var evolveMultiplier = DerivedStats()

        for (tag in tags) {
            val def = repo.monsterBehaviors[tag] ?: continue
            summonChance += def.summonChancePct
            enrageChance += def.enrageChancePct
            evolveChance += def.evolveChancePct
            maxSummons = maxOf(maxSummons, def.maxSummons)
            enrageTurns = maxOf(enrageTurns, def.enrageTurns)
            enrageHp = maxOf(enrageHp, def.enrageHpThresholdPct)
            evolveHp = maxOf(evolveHp, def.evolveHpThresholdPct)
            enrageMultiplier += def.enrageMultiplier
            evolveMultiplier += def.evolveMultiplier
        }

        return BehaviorProfile(
            summonChancePct = min(100.0, summonChance),
            enrageChancePct = min(100.0, enrageChance),
            evolveChancePct = min(100.0, evolveChance),
            maxSummons = maxSummons,
            enrageTurns = enrageTurns,
            enrageHpThresholdPct = enrageHp,
            evolveHpThresholdPct = evolveHp,
            enrageMultiplier = enrageMultiplier,
            evolveMultiplier = evolveMultiplier
        )
    }

    fun rollSummon(
        tags: Set<String>,
        summonsUsed: Int,
        bonusPct: Double = 0.0
    ): Boolean {
        val profile = profile(tags)
        if (summonsUsed >= profile.maxSummons) return false
        val chance = min(100.0, profile.summonChancePct + bonusPct)
        return rng.nextDouble(0.0, 100.0) <= chance
    }

    fun rollEnrage(tags: Set<String>, hpPct: Double, alreadyEnraged: Boolean): BehaviorTrigger? {
        if (alreadyEnraged) return null
        val profile = profile(tags)
        if (profile.enrageChancePct <= 0.0 || profile.enrageTurns <= 0) return null
        if (hpPct > profile.enrageHpThresholdPct) return null
        val roll = rng.nextDouble(0.0, 100.0)
        if (roll > profile.enrageChancePct) return null
        return BehaviorTrigger(profile.enrageMultiplier, profile.enrageTurns)
    }

    fun rollEvolve(tags: Set<String>, hpPct: Double, evolved: Boolean): BehaviorTrigger? {
        if (evolved) return null
        val profile = profile(tags)
        if (profile.evolveChancePct <= 0.0) return null
        if (hpPct > profile.evolveHpThresholdPct) return null
        val roll = rng.nextDouble(0.0, 100.0)
        if (roll > profile.evolveChancePct) return null
        return BehaviorTrigger(profile.evolveMultiplier, 0)
    }
}
