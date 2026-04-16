package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class RaceDef(
    val id: String,
    val name: String,
    val description: String = "",
    val bonuses: Bonuses = Bonuses(),
    val growth: Attributes = Attributes()
)
