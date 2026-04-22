package rpg.events

import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.model.PlayerState

enum class Attribute {
    STR, AGI, DEX, VIT, INT, SPR, LUK
}

enum class Rarity(val weight: Int, val colorHex: String) {
    COMMON(60, "#B0B0B0"),
    RARE(25, "#4FC3F7"),
    EPIC(10, "#BA68C8"),
    LEGENDARY(5, "#FFD54F")
}

data class EventDefinition(
    val id: String,
    val rarity: Rarity,
    val description: String,
    val effects: List<EventEffect>
)

data class EventContext(
    val statsProvider: (PlayerState) -> ComputedStats,
    val rng: Random,
    val playerLevel: Int = 1,
    val depth: Int = 0,
    val tierId: String? = null
)

data class EventRecipe(
    val rarity: Rarity,
    val weight: Int,
    val effects: List<EventEffect>,
    val minDepth: Int = 0,
    val maxDepth: Int = Int.MAX_VALUE,
    val minLevel: Int = 1,
    val maxLevel: Int = Int.MAX_VALUE
)

enum class EventSource {
    LIQUID,
    NPC_HELP,
    CHEST_REWARD
}