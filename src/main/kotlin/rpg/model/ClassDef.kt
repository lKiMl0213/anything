package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class ClassDef(
    val id: String,
    val name: String,
    val description: String = "",
    val bonuses: Bonuses = Bonuses(),
    val growth: Attributes = Attributes(),
    val autoPointWeights: Attributes = Attributes(),
    val talentTreeId: String? = null,
    val secondClassIds: List<String> = emptyList(),
    val secondClassUnlockLevel: Int = 10,
    val secondClassUnlockQuestTemplateId: String? = null,
    val specializationUnlockLevel: Int = 50,
    val specializationUnlockQuestTemplateId: String? = null
)
