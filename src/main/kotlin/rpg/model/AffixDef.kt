package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class AffixKind {
    PREFIX,
    SUFFIX,
    SPECIAL
}

@Serializable
data class AffixDef(
    val id: String,
    val name: String,
    val kind: AffixKind = AffixKind.SUFFIX,
    val bonuses: Bonuses = Bonuses(),
    val cost: Int = 0,
    val weight: Int = 1
)
