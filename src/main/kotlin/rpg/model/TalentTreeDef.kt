package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class TalentNodeDef(
    val id: String,
    val name: String,
    val description: String = "",
    val cost: Int = 1,
    val requiredLevel: Int = 1,
    val prerequisites: List<String> = emptyList(),
    val bonuses: Bonuses = Bonuses(),
    val classId: String? = null,
    val subclassId: String? = null
)

@Serializable
data class TalentTreeDef(
    val id: String,
    val name: String,
    val nodes: List<TalentNodeDef> = emptyList()
)
