package rpg.economy

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.model.GameBalanceDef
import rpg.model.ItemType
import rpg.model.MapTierDef

class EconomyEngine(
    private val rng: Random,
    private val balance: GameBalanceDef
) {

    fun computeGold(
        monsterPower: Double,
        baseGold: Int,
        tier: MapTierDef,
        playerLevel: Int,
        difficultyModifier: Double
    ): Int {
        val scaled = monsterPower * balance.goldPowerScale
        val base = max(baseGold.toDouble(), scaled)
        val variance = base * 0.12
        var gold = base + rng.nextDouble(-variance, variance)
        gold *= tier.goldMultiplier * difficultyModifier
        if (playerLevel >= tier.recommendedLevel + balance.antiFarmLevelGap) {
            gold *= balance.antiFarmGoldMultiplier
        }
        return max(1, gold.roundToInt())
    }

    fun sellValue(itemValue: Int, type: ItemType? = null, tags: List<String> = emptyList()): Int {
        if (itemValue <= 0) return 0
        val loweredTags = tags.map { it.lowercase() }.toSet()
        val isCashItem = "cash" in loweredTags || "premium" in loweredTags
        val pct = if (isCashItem) {
            0.03
        } else {
            when (type) {
                ItemType.MATERIAL -> 0.18
                ItemType.CONSUMABLE -> 0.14
                ItemType.EQUIPMENT -> 0.12
                null -> 0.15
            }
        }
        return max(1, (itemValue * pct).roundToInt())
    }
}
