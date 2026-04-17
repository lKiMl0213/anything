package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class SpecializationDef(
    val id: String,
    val name: String,
    val parentClassId: String,
    val parentSubclassId: String,
    val description: String = "",
    val bonuses: Bonuses = Bonuses(),
    val growth: Attributes = Attributes(),
    val autoPointWeights: Attributes = Attributes(),
    val talentTreeId: String? = null
)
