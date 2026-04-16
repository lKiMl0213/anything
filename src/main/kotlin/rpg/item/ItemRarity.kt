package rpg.item

import kotlinx.serialization.Serializable

@Serializable
enum class ItemRarity(
    val weight: Int,
    val powerMultiplier: Double,
    val affixMin: Int,
    val affixMax: Int
) {
    COMMON(55, 1.0, 0, 0),
    UNCOMMON(25, 1.08, 1, 1),
    RARE(12, 1.16, 2, 2),
    EPIC(6, 1.30, 3, 3),
    LEGENDARY(2, 1.50, 4, 4);

    companion object {
        fun roll(rng: kotlin.random.Random): ItemRarity {
            val total = values().sumOf { it.weight }
            val roll = rng.nextInt(total)
            var acc = 0
            for (rarity in values()) {
                acc += rarity.weight
                if (roll < acc) return rarity
            }
            return COMMON
        }
    }
}
