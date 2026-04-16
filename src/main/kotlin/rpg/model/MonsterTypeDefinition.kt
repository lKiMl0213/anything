package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class MonsterTypeDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val damageAffinities: Map<String, Double> = emptyMap()
)

