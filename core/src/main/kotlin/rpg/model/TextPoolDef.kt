package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class TextPoolDef(
    val id: String,
    val intros: List<String> = emptyList(),
    val descriptors: List<String> = emptyList(),
    val threats: List<String> = emptyList(),
    val monsterTags: List<String> = emptyList(),
    val biomeIds: List<String> = emptyList(),
    val dangerLevels: List<String> = emptyList()
)
