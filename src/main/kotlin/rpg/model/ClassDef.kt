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
    val subclassIds: List<String> = emptyList(),
    val subclassUnlockLevel: Int = 10,
    val subclassUnlockQuestTemplateId: String? = null,
    val specializationIds: List<String> = emptyList(),
    val specializationUnlockLevel: Int = 50,
    val specializationUnlockQuestTemplateId: String? = null
)
