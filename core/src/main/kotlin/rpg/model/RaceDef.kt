package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class RaceDef(
    val id: String,
    val name: String,
    val description: String = "",
    val bonuses: Bonuses = Bonuses(),
    val growth: Attributes = Attributes(),
    val professionBonusesPct: Map<String, Double> = emptyMap(),
    val tradeBuyDiscountPct: Double = 0.0,
    val tradeSellBonusPct: Double = 0.0
)
