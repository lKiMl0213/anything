package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class Bonuses(
    val attributes: Attributes = Attributes(),
    val derivedAdd: DerivedStats = DerivedStats(),
    val derivedMult: DerivedStats = DerivedStats()
) {
    operator fun plus(other: Bonuses): Bonuses = Bonuses(
        attributes = attributes + other.attributes,
        derivedAdd = derivedAdd + other.derivedAdd,
        derivedMult = derivedMult + other.derivedMult
    )
}
