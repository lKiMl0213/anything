package rpg.monster

internal data class MonsterThreatProfile(
    val progression: Double,
    val varianceScale: Double,
    val statusScale: Double,
    val rarityPressure: Double,
    val maxModifierCount: Int,
    val starCap: Int
)
