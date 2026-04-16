package rpg.model

import kotlinx.serialization.Serializable
import rpg.status.StatusType

@Serializable
data class CombatStatusApplyDef(
    val type: StatusType,
    val chancePct: Double = 0.0,
    val durationSeconds: Double = 4.0,
    val tickIntervalSeconds: Double = 1.0,
    val effectValue: Double = 0.0,
    val stackable: Boolean = false,
    val maxStacks: Int = 1,
    val source: String = ""
)
