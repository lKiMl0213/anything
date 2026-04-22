package rpg.combat

import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.inventory.ArrowConsumeResult
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.status.StatusSystem

internal interface CombatActionGateway {
    val rng: Random
    val ansiYellow: String
    val ansiBlue: String

    fun combatLog(message: String)
    fun colorize(text: String, colorCode: String): String
    fun format(value: Double): String
    fun effectiveCastTime(actor: CombatActor, baseCastSeconds: Double): Double
    fun applySkillCooldown(actor: CombatActor, spec: CombatSkillSpec)
    fun startCasting(actor: CombatActor, action: CombatAction, castTime: Double, afterResolve: (() -> Unit)? = null)
    fun endAction(actor: CombatActor, actionBarCarryPct: Double = 0.0)
    fun resolveSkill(
        attacker: CombatActor,
        defender: CombatActor,
        preferMagic: Boolean?,
        telemetry: MutableCombatTelemetry,
        actionMultiplier: Double = 1.0,
        actionName: String? = null,
        extraOnHitStatuses: List<CombatStatusApplyDef> = emptyList(),
        extraBonuses: Bonuses = Bonuses(),
        selfHealFlat: Double = 0.0,
        selfHealPctMaxHp: Double = 0.0,
        skillRank: Int = 1,
        aoeUnlockRank: Int = 0,
        aoeBonusDamagePct: Double = 0.0
    ): SkillResolutionResult
    fun useItem(
        selfActor: CombatActor,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        itemId: String,
        currentStatuses: List<rpg.status.StatusEffectInstance>,
        currentImmunitySeconds: Double
    ): UseItemResult
    fun applyReviveIfNeeded(player: PlayerState, actor: CombatActor): PlayerState
    fun syncPlayerHp(player: PlayerState, actor: CombatActor): PlayerState
    fun rollEscape(playerStats: ComputedStats, monsterStats: ComputedStats): EscapeAttempt
    fun rangedAmmoRequirementReason(player: PlayerState, itemInstances: Map<String, ItemInstance>): String?
    fun playerUsesBowAmmo(player: PlayerState, itemInstances: Map<String, ItemInstance>): Boolean
    fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int = 1
    ): ArrowConsumeResult?
    fun previewAmmoPayload(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): AmmoPayload
}

internal class CombatActionExecutor(
    private val gateway: CombatActionGateway
) {
    fun processAction(
        actor: CombatActor,
        target: CombatActor,
        action: CombatAction,
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        telemetry: MutableCombatTelemetry,
        onPlayerUpdate: (PlayerState, Map<String, ItemInstance>) -> Unit
    ): ActionOutcome {
        if (actor.runtime.state != CombatState.READY) {
            return ActionOutcome(consumedReady = false)
        }
        if (StatusSystem.rollParalyzedFailure(actor.runtime.statuses, gateway.rng)) {
            gateway.combatLog(gateway.colorize("Voce esta paralisado e falhou na acao!", gateway.ansiYellow))
            gateway.endAction(actor, actionBarCarryPct = 0.0)
            return ActionOutcome()
        }

        return when (action) {
            is CombatAction.Attack -> {
                val ammoBlock = gateway.rangedAmmoRequirementReason(playerState, itemInstances)
                if (ammoBlock != null) {
                    gateway.combatLog(gateway.colorize(ammoBlock, gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val castTime = gateway.effectiveCastTime(actor, 0.0)
                if (castTime > 0.0) {
                    gateway.startCasting(actor, action, castTime)
                } else {
                    val ammoPayload = gateway.previewAmmoPayload(playerState, itemInstances, amount = 1)
                    val ammoConsumed = gateway.consumeArrowAmmo(playerState, itemInstances, amount = 1)
                    if (ammoConsumed == null && gateway.playerUsesBowAmmo(playerState, itemInstances)) {
                        gateway.combatLog(gateway.colorize("Voce esta sem flechas para atacar.", gateway.ansiYellow))
                        return ActionOutcome(consumedReady = false)
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                    val resolution = gateway.resolveSkill(
                        attacker = actor,
                        defender = target,
                        preferMagic = action.preferMagic,
                        telemetry = telemetry,
                        extraBonuses = ammoPayload.bonuses,
                        extraOnHitStatuses = ammoPayload.statuses
                    )
                    val carryPct = if (resolution.hit) {
                        actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
                    } else {
                        resolution.carryBonusPct
                    }
                    gateway.endAction(actor, actionBarCarryPct = carryPct)
                }
                ActionOutcome()
            }

            is CombatAction.Skill -> {
                val spec = action.spec
                if (spec.id.isBlank()) {
                    gateway.combatLog(gateway.colorize("Habilidade invalida.", gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val ammoBlock = gateway.rangedAmmoRequirementReason(playerState, itemInstances)
                if (ammoBlock != null) {
                    gateway.combatLog(gateway.colorize(ammoBlock, gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val cooldown = actor.runtime.skillCooldowns[spec.id] ?: 0.0
                if (cooldown > 0.0) {
                    gateway.combatLog(gateway.colorize("${spec.name} em cooldown (${gateway.format(cooldown)}s).", gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val noManaCostChance = actor.talentModifiers.atb.noManaCostChancePct.coerceIn(0.0, 100.0)
                val freeCast = spec.mpCost > 0.0 && gateway.rng.nextDouble(0.0, 100.0) <= noManaCostChance
                if (!freeCast && actor.currentMp + 1e-6 < spec.mpCost) {
                    gateway.combatLog(gateway.colorize("Mana insuficiente para ${spec.name}.", gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                if (!freeCast) {
                    actor.currentMp = (actor.currentMp - spec.mpCost).coerceAtLeast(0.0)
                } else {
                    gateway.combatLog(gateway.colorize("Proc de talento: ${spec.name} nao consumiu mana.", gateway.ansiBlue))
                }
                gateway.applySkillCooldown(actor, spec)
                val castTime = gateway.effectiveCastTime(actor, spec.castTimeSeconds)
                if (castTime > 0.0) {
                    gateway.startCasting(actor, action, castTime)
                } else {
                    val ammoPayload = gateway.previewAmmoPayload(playerState, itemInstances, amount = spec.ammoCost)
                    val ammoConsumed = gateway.consumeArrowAmmo(playerState, itemInstances, amount = spec.ammoCost)
                    if (ammoConsumed == null && gateway.playerUsesBowAmmo(playerState, itemInstances)) {
                        gateway.combatLog(gateway.colorize("Voce esta sem flechas para usar ${spec.name}.", gateway.ansiYellow))
                        return ActionOutcome(consumedReady = false)
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = actor.currentHp, currentMp = actor.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                    val resolution = gateway.resolveSkill(
                        attacker = actor,
                        defender = target,
                        preferMagic = spec.preferMagic,
                        telemetry = telemetry,
                        actionMultiplier = spec.damageMultiplier.coerceAtLeast(0.1),
                        actionName = spec.name,
                        extraOnHitStatuses = spec.onHitStatuses + ammoPayload.statuses,
                        extraBonuses = ammoPayload.bonuses,
                        selfHealFlat = spec.selfHealFlat,
                        selfHealPctMaxHp = spec.selfHealPctMaxHp,
                        skillRank = spec.rank,
                        aoeUnlockRank = spec.aoeUnlockRank,
                        aoeBonusDamagePct = spec.aoeBonusDamagePct
                    )
                    val carryPct = if (resolution.hit) {
                        actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
                    } else {
                        resolution.carryBonusPct
                    }
                    gateway.endAction(actor, actionBarCarryPct = carryPct)
                }
                ActionOutcome()
            }

            is CombatAction.UseItem -> {
                if (action.itemId.isBlank()) {
                    gateway.combatLog(gateway.colorize("Nenhum item selecionado.", gateway.ansiYellow))
                    return ActionOutcome(consumedReady = false)
                }
                val result = gateway.useItem(
                    selfActor = actor,
                    player = playerState,
                    itemInstances = itemInstances,
                    itemId = action.itemId,
                    currentStatuses = actor.runtime.statuses,
                    currentImmunitySeconds = actor.runtime.statusImmunitySeconds
                )
                onPlayerUpdate(result.player, result.itemInstances)
                actor.runtime = actor.runtime.copy(
                    statuses = result.statuses,
                    statusImmunitySeconds = result.statusImmunitySeconds
                )
                gateway.endAction(actor, actionBarCarryPct = 0.0)
                ActionOutcome()
            }

            is CombatAction.Escape -> {
                val attempt = gateway.rollEscape(actor.stats, target.stats)
                if (attempt.success) {
                    gateway.combatLog("Voce fugiu! (${gateway.format(attempt.chancePct)}%)")
                    ActionOutcome(escaped = true)
                } else {
                    gateway.combatLog("Voce tentou fugir, mas falhou. (${gateway.format(attempt.chancePct)}%)")
                    gateway.endAction(actor, actionBarCarryPct = 0.0)
                    ActionOutcome()
                }
            }
        }
    }

    fun processMonsterAction(
        actor: CombatActor,
        target: CombatActor,
        castTime: Double,
        onMonsterBehavior: () -> Unit,
        onSummon: () -> Unit,
        onAfterAttack: () -> Unit,
        playerProvider: () -> PlayerState,
        telemetry: MutableCombatTelemetry,
        onPlayerUpdate: (PlayerState) -> Unit
    ): Boolean {
        if (actor.runtime.state != CombatState.READY) return true

        if (StatusSystem.rollParalyzedFailure(actor.runtime.statuses, gateway.rng)) {
            gateway.combatLog(gateway.colorize("${actor.name} falhou em agir (paralisado).", gateway.ansiYellow))
            gateway.endAction(actor, actionBarCarryPct = 0.0)
            return true
        }
        onMonsterBehavior()
        val effectiveCast = gateway.effectiveCastTime(actor, castTime)
        if (effectiveCast > 0.0) {
            gateway.startCasting(
                actor,
                CombatAction.Attack(preferMagic = actor.preferMagic),
                effectiveCast
            ) {
                val updated = gateway.applyReviveIfNeeded(gateway.syncPlayerHp(playerProvider(), target), target)
                onPlayerUpdate(updated)
                onSummon()
                onAfterAttack()
            }
            return true
        }
        val resolution = gateway.resolveSkill(
            attacker = actor,
            defender = target,
            preferMagic = actor.preferMagic,
            telemetry = telemetry
        )
        val updated = gateway.applyReviveIfNeeded(gateway.syncPlayerHp(playerProvider(), target), target)
        onPlayerUpdate(updated)
        onSummon()
        onAfterAttack()
        val carryPct = if (resolution.hit) {
            actor.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
        } else {
            resolution.carryBonusPct
        }
        gateway.endAction(actor, actionBarCarryPct = carryPct)
        return true
    }

    fun handleCastComplete(
        caster: CombatActor,
        target: CombatActor,
        telemetry: MutableCombatTelemetry,
        playerState: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        onPlayerUpdate: (PlayerState, Map<String, ItemInstance>) -> Unit
    ) {
        val pending = caster.pendingAction ?: return
        var resolution = SkillResolutionResult(hit = false)
        when (pending) {
            is CombatAction.Attack -> {
                val ammoPayload = if (caster.kind == CombatantKind.PLAYER) {
                    gateway.previewAmmoPayload(playerState, itemInstances, amount = 1)
                } else {
                    AmmoPayload()
                }
                if (caster.kind == CombatantKind.PLAYER) {
                    val ammoConsumed = gateway.consumeArrowAmmo(playerState, itemInstances, amount = 1)
                    if (ammoConsumed == null && gateway.playerUsesBowAmmo(playerState, itemInstances)) {
                        gateway.combatLog(gateway.colorize("Voce ficou sem flechas antes do disparo.", gateway.ansiYellow))
                        caster.pendingAfterResolve?.invoke()
                        gateway.endAction(caster, actionBarCarryPct = 0.0)
                        return
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = caster.currentHp, currentMp = caster.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                }
                resolution = gateway.resolveSkill(
                    attacker = caster,
                    defender = target,
                    preferMagic = pending.preferMagic ?: caster.preferMagic,
                    telemetry = telemetry,
                    extraBonuses = ammoPayload.bonuses,
                    extraOnHitStatuses = ammoPayload.statuses
                )
            }
            is CombatAction.Skill -> {
                val ammoPayload = if (caster.kind == CombatantKind.PLAYER) {
                    gateway.previewAmmoPayload(playerState, itemInstances, amount = pending.spec.ammoCost)
                } else {
                    AmmoPayload()
                }
                if (caster.kind == CombatantKind.PLAYER) {
                    val ammoConsumed = gateway.consumeArrowAmmo(playerState, itemInstances, amount = pending.spec.ammoCost)
                    if (ammoConsumed == null && gateway.playerUsesBowAmmo(playerState, itemInstances)) {
                        gateway.combatLog(gateway.colorize("Voce ficou sem flechas antes de concluir ${pending.spec.name}.", gateway.ansiYellow))
                        caster.pendingAfterResolve?.invoke()
                        gateway.endAction(caster, actionBarCarryPct = 0.0)
                        return
                    }
                    if (ammoConsumed != null) {
                        val syncedPlayer = ammoConsumed.player.copy(currentHp = caster.currentHp, currentMp = caster.currentMp)
                        onPlayerUpdate(syncedPlayer, ammoConsumed.itemInstances)
                    }
                }
                resolution = gateway.resolveSkill(
                    attacker = caster,
                    defender = target,
                    preferMagic = pending.spec.preferMagic,
                    telemetry = telemetry,
                    actionMultiplier = pending.spec.damageMultiplier.coerceAtLeast(0.1),
                    actionName = pending.spec.name,
                    extraOnHitStatuses = pending.spec.onHitStatuses + ammoPayload.statuses,
                    extraBonuses = ammoPayload.bonuses,
                    selfHealFlat = pending.spec.selfHealFlat,
                    selfHealPctMaxHp = pending.spec.selfHealPctMaxHp,
                    skillRank = pending.spec.rank,
                    aoeUnlockRank = pending.spec.aoeUnlockRank,
                    aoeBonusDamagePct = pending.spec.aoeBonusDamagePct
                )
            }
            is CombatAction.UseItem -> {
                if (caster.kind == CombatantKind.PLAYER) {
                    // Casting for consumables is not supported.
                }
            }

            is CombatAction.Escape -> {
                // Escape is always resolved immediately.
            }
        }
        caster.pendingAfterResolve?.invoke()
        val carryPct = if (resolution.hit) {
            caster.talentModifiers.atb.actionBarGainOnHitPct + resolution.carryBonusPct
        } else {
            resolution.carryBonusPct
        }
        gateway.endAction(caster, actionBarCarryPct = carryPct)
    }
}
