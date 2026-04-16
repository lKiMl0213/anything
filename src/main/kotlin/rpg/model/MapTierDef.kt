package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class MapTierDef(
    val id: String,
    val minLevel: Int,
    val recommendedLevel: Int,
    val baseMonsterLevel: Int,
    val difficultyMultiplier: Double = 1.0,
    val allowedMonsterTemplates: List<String> = emptyList(),
    val dropTier: Int = 1,
    val dropChanceMultiplier: Double = 1.0,
    val xpMultiplier: Double = 1.0,
    val goldMultiplier: Double = 1.0,
    val biomeId: String? = null,
    val isInfinite: Boolean = false
)

@Serializable
data class BiomeDef(
    val id: String,
    val name: String,
    val description: String = "",
    val monsterTagWeights: Map<String, Double> = emptyMap(),
    val summonChanceBonusPct: Double = 0.0,
    val healingMultiplier: Double = 1.0,
    val eventRoomChanceBonusPct: Int = 0,
    val npcEventBonusPct: Int = 0
)
