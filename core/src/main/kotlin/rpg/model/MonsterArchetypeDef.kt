package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class MonsterArchetypeDef(
    val id: String,
    val name: String,
    val archetype: String = "",
    val baseType: String = "",
    val monsterTypeId: String = "",
    val family: String = "",
    val displayName: String = "",
    val variantName: String = "",
    val baseAttributes: Attributes = Attributes(),
    val growthAttributes: Attributes = Attributes(),
    val variancePct: Double = 0.15,
    val tags: List<String> = emptyList(),
    val questTags: List<String> = emptyList(),
    val lootProfileId: String = "",
    val behaviorProfileId: String = "",
    val skillPool: List<String> = emptyList(),
    val onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val damageAffinities: Map<String, Double> = emptyMap(),
    val baseCastTime: Double = 0.0,
    val dropTableId: String = "",
    val baseXp: Int = 0,
    val baseGold: Int = 0
)
