package rpg.combat

import rpg.engine.ComputedStats
import rpg.model.CombatStatusApplyDef
import rpg.talent.TalentCombatModifiers

internal data class MutableCombatTelemetry(
    var playerDamageDealt: Double = 0.0,
    var playerDamageTaken: Double = 0.0,
    var playerCriticalHits: Int = 0
)

internal enum class CombatantKind {
    PLAYER,
    MONSTER
}

internal data class CombatActor(
    val id: String,
    val name: String,
    val kind: CombatantKind,
    val monsterArchetypeId: String? = null,
    val monsterTypeId: String? = null,
    val monsterTags: Set<String> = emptySet(),
    var stats: ComputedStats,
    var currentHp: Double,
    var currentMp: Double,
    var runtime: CombatRuntimeState,
    var pendingAction: CombatAction? = null,
    var pendingAfterResolve: (() -> Unit)? = null,
    var preferMagic: Boolean? = null,
    var speedBonusPct: Double = 0.0,
    val monsterTypeDamageBonusPct: Map<String, Double> = emptyMap(),
    var onHitStatuses: List<CombatStatusApplyDef> = emptyList(),
    val talentModifiers: TalentCombatModifiers = TalentCombatModifiers()
)
