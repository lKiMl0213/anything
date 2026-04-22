package rpg.combat

import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.ItemInstance
import rpg.model.PlayerState

internal data class UseItemResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val statuses: List<rpg.status.StatusEffectInstance> = emptyList(),
    val statusImmunitySeconds: Double = 0.0
)

internal data class AmmoPayload(
    val bonuses: Bonuses = Bonuses(),
    val statuses: List<CombatStatusApplyDef> = emptyList(),
    val label: String = ""
)

internal data class SkillResolutionResult(
    val hit: Boolean,
    val crit: Boolean = false,
    val targetDefeated: Boolean = false,
    val statusesApplied: Int = 0,
    val carryBonusPct: Double = 0.0
)

internal data class ActionOutcome(
    val escaped: Boolean = false,
    val consumedReady: Boolean = true
)

internal data class EscapeAttempt(
    val success: Boolean,
    val chancePct: Double
)
