package rpg.cooking

import kotlinx.serialization.Serializable

@Serializable
enum class CookingBuffType {
    HP_REGEN,
    MP_REGEN,
    DAMAGE,
    DEFENSE,
    TASK_EFFICIENCY
}

@Serializable
data class CookingBuffDef(
    val id: String,
    val itemId: String,
    val name: String,
    val type: CookingBuffType,
    val baseValue: Double,
    val baseDurationMinutes: Double = 8.0,
    val taskId: String? = null
)

@Serializable
data class CookingBuffConfig(
    val powerScalePerDifficulty: Double = 0.22,
    val durationScalePerDifficulty: Double = 0.18,
    val powerScalePerIngredient: Double = 0.06,
    val durationScalePerIngredient: Double = 0.04,
    val maxPowerMultiplier: Double = 1.9,
    val maxDurationMultiplier: Double = 1.8,
    val defaultDurationMinutes: Double = 8.0,
    val buffs: List<CookingBuffDef> = emptyList()
)
