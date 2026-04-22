package rpg.combat

import rpg.engine.ComputedStats
import rpg.model.CombatStatusApplyDef
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.monster.MonsterInstance

sealed class CombatAction {
    data class Attack(val preferMagic: Boolean? = null) : CombatAction()
    data class Skill(val spec: CombatSkillSpec) : CombatAction()
    data class UseItem(val itemId: String) : CombatAction()
    data object Escape : CombatAction()
}

data class CombatSkillSpec(
    val id: String,
    val name: String,
    val mpCost: Double,
    val cooldownSeconds: Double,
    val damageMultiplier: Double = 1.0,
    val preferMagic: Boolean? = null,
    val castTimeSeconds: Double = 0.0,
    val onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val selfHealFlat: Double = 0.0,
    val selfHealPctMaxHp: Double = 0.0,
    val ammoCost: Int = 1,
    val rank: Int = 1,
    val aoeUnlockRank: Int = 0,
    val aoeBonusDamagePct: Double = 0.0
)

data class CombatSnapshot(
    val player: PlayerState,
    val playerStats: ComputedStats,
    val monster: MonsterInstance,
    val monsterStats: ComputedStats,
    val monsterHp: Double,
    val itemInstances: Map<String, ItemInstance>,
    val playerRuntime: CombatRuntimeState,
    val monsterRuntime: CombatRuntimeState,
    val playerFillRate: Double,
    val monsterFillRate: Double,
    val pausedForDecision: Boolean
)

interface PlayerCombatController {
    fun onFrame(snapshot: CombatSnapshot) {}
    fun onDecisionStarted(snapshot: CombatSnapshot) {}
    fun onDecisionEnded() {}
    fun pollAction(snapshot: CombatSnapshot): CombatAction?
}

data class CombatResult(
    val playerAfter: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val victory: Boolean,
    val escaped: Boolean = false,
    val telemetry: CombatTelemetry = CombatTelemetry()
)

data class CombatTelemetry(
    val playerDamageDealt: Double = 0.0,
    val playerDamageTaken: Double = 0.0,
    val playerCriticalHits: Int = 0
)
