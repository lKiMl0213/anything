package rpg.hunting

import kotlinx.serialization.Serializable

@Serializable
data class HuntingDropEntry(
    val itemId: String,
    val weight: Int = 1,
    val minQty: Int = 1,
    val maxQty: Int = 1,
    val rareChancePct: Double = 0.0,
    val minPlayerLevel: Int = 1
)

@Serializable
data class HuntingSpot(
    val id: String,
    val name: String,
    val recommendedLevel: Int = 1,
    val minCycleSeconds: Int = 30,
    val baseXp: Double = 16.0,
    val difficulty: Double = 1.0,
    val description: String = "",
    val drops: List<HuntingDropEntry> = emptyList()
)
