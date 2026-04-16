package rpg.model

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
enum class SkillType {
    MINING,
    GATHERING,
    WOODCUTTING,
    FISHING,
    BLACKSMITH,
    ALCHEMIST,
    COOKING
}

@Serializable
data class SkillUnlockDef(
    val level: Int,
    val unlockType: String,
    val targetId: String,
    val description: String = ""
)

@Serializable
data class SkillDef(
    val id: SkillType,
    val name: String,
    val baseXp: Double = 100.0,
    val unlocks: List<SkillUnlockDef> = emptyList()
)

@Serializable
data class SkillProgressState(
    val level: Int = 1,
    val currentXp: Double = 0.0,
    val lifetimeXp: Double = 0.0
)

data class SkillSnapshot(
    val skill: SkillType,
    val level: Int,
    val currentXp: Double,
    val requiredXp: Double,
    val efficiencyMultiplier: Double,
    val doubleDropChancePct: Double,
    val criticalCraftChancePct: Double,
    val materialReductionChancePct: Double,
    val noConsumeChancePct: Double,
    val unlocks: List<SkillUnlockDef>
)

fun requiredXpForLevel(baseXp: Double, level: Int): Double {
    val normalized = level.coerceAtLeast(1)
    return baseXp * normalized.toDouble().pow(1.5)
}
