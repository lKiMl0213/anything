package rpg.combat

import rpg.status.StatusEffectInstance

data class CombatRuntimeState(
    val actionBar: Double = 0.0,
    val actionThreshold: Double = 100.0,
    val gcdRemaining: Double = 0.0,
    val skillCooldowns: Map<String, Double> = emptyMap(),
    val castRemaining: Double = 0.0,
    val castTotal: Double = 0.0,
    val readySinceSeconds: Double? = null,
    val state: CombatState = CombatState.IDLE,
    val currentSkillId: String? = null,
    val statuses: List<StatusEffectInstance> = emptyList(),
    val statusImmunitySeconds: Double = 0.0,
    val statusSpeedMultiplier: Double = 1.0,
    val statusDamageMultiplier: Double = 1.0,
    val tempBuffRemainingSeconds: Double = 0.0,
    val tempBuffDamageMultiplier: Double = 1.0,
    val tempBuffFillRateMultiplier: Double = 1.0
)

enum class CombatState {
    IDLE,
    READY,
    CASTING,
    STUNNED,
    DEAD
}
