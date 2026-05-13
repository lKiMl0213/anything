package rpg.hunting

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.item.ItemRarity
import rpg.registry.ItemRegistry

data class HuntingDropRoll(
    val collectedByItemId: Map<String, Int>,
    val rareDropCount: Int,
    val totalUnits: Int
)

class HuntingDropResolver(
    private val itemRegistry: ItemRegistry,
    private val config: HuntingConfig,
    private val rng: Random
) {
    fun resolve(
        spot: HuntingSpot,
        playerLevel: Int,
        huntingSkillLevel: Int,
        selectedDurationSeconds: Int,
        cycleDurationSeconds: Double,
        taskEfficiencyPct: Double = 0.0
    ): HuntingDropRoll {
        val eligible = spot.drops.filter { entry ->
            entry.weight > 0 && playerLevel >= entry.minPlayerLevel
        }
        if (eligible.isEmpty()) return HuntingDropRoll(emptyMap(), 0, 0)

        val cycles = config.resolveCycles(selectedDurationSeconds, cycleDurationSeconds)
        val quantityMultiplier = quantityMultiplier(
            playerLevel = playerLevel,
            spotLevel = spot.recommendedLevel,
            skillLevel = huntingSkillLevel,
            taskEfficiencyPct = taskEfficiencyPct
        )
        val rareBonus = (huntingSkillLevel.coerceAtLeast(1) * config.rareChanceBonusPctPerSkillLevel)
            .coerceAtMost(config.maxRareChanceBonusPct)
        val collected = linkedMapOf<String, Int>()
        var rareDrops = 0
        var total = 0
        repeat(cycles) {
            val picked = pickWeightedEntry(eligible) ?: return@repeat
            val baseQty = rollQuantity(picked.minQty, picked.maxQty)
            val qty = max(1, (baseQty * quantityMultiplier).roundToInt())
            collected[picked.itemId] = (collected[picked.itemId] ?: 0) + qty
            total += qty
            val rarity = itemRegistry.entry(picked.itemId)?.rarity ?: ItemRarity.COMMON
            val baseRareChance = max(picked.rareChancePct, defaultRareChance(rarity))
            val rareChance = (baseRareChance + rareBonus).coerceIn(0.0, 95.0)
            if (rng.nextDouble(0.0, 100.0) <= rareChance) {
                rareDrops += 1
            }
        }
        return HuntingDropRoll(collectedByItemId = collected, rareDropCount = rareDrops, totalUnits = total)
    }

    private fun quantityMultiplier(
        playerLevel: Int,
        spotLevel: Int,
        skillLevel: Int,
        taskEfficiencyPct: Double
    ): Double {
        val gap = playerLevel - spotLevel
        val levelFactor = when {
            gap < 0 -> {
                val penalty = (abs(gap) * config.underLevelPenaltyPctPerLevel).coerceAtMost(config.maxUnderLevelPenaltyPct)
                1.0 - (penalty / 100.0)
            }

            else -> {
                val bônus = (gap * config.overLevelBonusPctPerLevel).coerceAtMost(config.maxOverLevelBonusPct)
                1.0 + (bônus / 100.0)
            }
        }
        val antiFarm = if (gap >= config.antiFarmLevelGap) config.antiFarmYieldMultiplier.coerceIn(0.1, 1.0) else 1.0
        val skillFactor = 1.0 + (skillLevel.coerceAtLeast(1) * config.quantityBonusPctPerSkillLevel / 100.0)
        val taskFactor = 1.0 + taskEfficiencyPct.coerceIn(0.0, 80.0) / 100.0
        val rngFactor = rng.nextDouble(config.rngMinMultiplier, config.rngMaxMultiplier)
        return (levelFactor * antiFarm * skillFactor * taskFactor * rngFactor).coerceAtLeast(0.2)
    }

    private fun pickWeightedEntry(entries: List<HuntingDropEntry>): HuntingDropEntry? {
        if (entries.isEmpty()) return null
        val total = entries.sumOf { it.weight.coerceAtLeast(0) }
        if (total <= 0) return entries.first()
        var roll = rng.nextInt(total)
        for (entry in entries) {
            roll -= entry.weight.coerceAtLeast(0)
            if (roll < 0) return entry
        }
        return entries.first()
    }

    private fun rollQuantity(minQty: Int, maxQty: Int): Int {
        val min = minQty.coerceAtLeast(1)
        val max = max(maxQty, min)
        return if (max == min) min else rng.nextInt(min, max + 1)
    }

    private fun defaultRareChance(rarity: ItemRarity): Double {
        return when (rarity) {
            ItemRarity.COMMON -> 0.0
            ItemRarity.UNCOMMON -> 3.0
            ItemRarity.RARE -> 7.0
            ItemRarity.EPIC -> 12.0
            ItemRarity.LEGENDARY -> 18.0
            ItemRarity.MYTHIC -> 24.0
        }
    }
}

