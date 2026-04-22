package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class MonsterBehaviorDef(
    val tag: String,
    val summonChancePct: Double = 0.0,
    val enrageChancePct: Double = 0.0,
    val evolveChancePct: Double = 0.0,
    val maxSummons: Int = 0,
    val enrageTurns: Int = 0,
    val enrageHpThresholdPct: Double = 60.0,
    val evolveHpThresholdPct: Double = 35.0,
    val enrageMultiplier: DerivedStats = DerivedStats(),
    val evolveMultiplier: DerivedStats = DerivedStats()
)
