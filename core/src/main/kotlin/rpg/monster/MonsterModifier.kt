package rpg.monster

import kotlinx.serialization.Serializable
import rpg.model.Bonuses

@Serializable
data class MonsterModifier(
    val id: String,
    val name: String,
    val attributeMultiplier: Double = 1.0,
    val bonuses: Bonuses = Bonuses(),
    val addTags: Set<String> = emptySet(),
    val weight: Int = 1
)
