package rpg.item

import kotlinx.serialization.Serializable

@Serializable
enum class ItemRarity(
    val weight: Int,
    val powerMultiplier: Double,
    val affixMin: Int,
    val affixMax: Int,
    val valueMultiplier: Double,
    val colorLabel: String,
    val ansiColorCode: String
) {
    COMMON(58, 1.0, 0, 0, 1.0, "Comum", "\u001B[37m"),
    UNCOMMON(24, 1.08, 1, 1, 1.15, "Incomum", "\u001B[32m"),
    RARE(10, 1.18, 2, 2, 1.45, "Raro", "\u001B[34m"),
    EPIC(5, 1.34, 3, 3, 2.0, "Epico", "\u001B[33m"),
    LEGENDARY(2, 1.58, 4, 4, 3.0, "Lendario", "\u001B[35m"),
    MYTHIC(1, 1.9, 5, 5, 4.8, "Mitico", "\u001B[31m");

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

        fun clamp(value: ItemRarity, min: ItemRarity, max: ItemRarity): ItemRarity {
            return when {
                value.ordinal < min.ordinal -> min
                value.ordinal > max.ordinal -> max
                else -> value
            }
        }

        fun promote(value: ItemRarity, steps: Int = 1): ItemRarity {
            if (steps <= 0) return value
            val values = values().toList()
            val promotedIndex = (value.ordinal + steps).coerceAtMost(values.lastIndex)
            return values[promotedIndex]
        }
    }
}
