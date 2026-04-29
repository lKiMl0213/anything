package rpg.combat

import kotlin.math.max
import kotlin.random.Random
import rpg.item.ItemResolver
import rpg.model.CombatStatusApplyDef
import rpg.model.DamageChannel
import rpg.model.GameBalanceDef
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.monster.MonsterAffinityService
import rpg.status.StatusSystem
import rpg.status.StatusType
import rpg.talent.TalentCombatModifiers

internal class CombatStatusProcessor(
    private val rng: Random,
    private val balance: GameBalanceDef,
    private val itemResolver: ItemResolver,
    private val monsterAffinityService: MonsterAffinityService?,
    private val logBuilder: CombatLogBuilder
) {
    fun tickStatus(actor: CombatActor, deltaTime: Double): rpg.status.StatusTickResult {
        val rawTick = applyStatusEffect(actor, deltaTime)
        return applyMonsterAffinityToStatusTick(actor, rawTick)
    }

    fun applyActionDot(actor: CombatActor): rpg.status.StatusActionDotResult {
        if (actor.currentHp <= 0.0 || actor.runtime.state == CombatState.DEAD) {
            return rpg.status.StatusActionDotResult(totalDamage = 0.0, events = emptyList())
        }
        val raw = StatusSystem.actionDot(
            current = actor.runtime.statuses,
            targetMaxHp = actor.stats.derived.hpMax
        )
        val adjusted = applyMonsterAffinityToActionDot(actor, raw)
        if (adjusted.totalDamage <= 0.0) return adjusted

        actor.currentHp = (actor.currentHp - adjusted.totalDamage).coerceAtLeast(0.0)
        if (actor.currentHp <= 0.0) {
            actor.runtime = actor.runtime.copy(
                state = CombatState.DEAD,
                readySinceSeconds = null
            )
        }
        emitActionDotMessages(actor, adjusted)
        return adjusted
    }

    fun directDamageAffinityMultiplier(
        defender: CombatActor,
        type: rpg.engine.DamageType
    ): Double {
        if (defender.kind != CombatantKind.MONSTER) return 1.0
        val channel = if (type == rpg.engine.DamageType.MAGIC) {
            DamageChannel.MAGIC
        } else {
            DamageChannel.PHYSICAL
        }
        return resolveMonsterAffinityMultiplier(defender, channel)
    }

    fun collectPlayerOnHitStatuses(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): List<CombatStatusApplyDef> {
        val effects = mutableListOf<CombatStatusApplyDef>()
        for (itemId in player.equipped.values) {
            val resolved = itemResolver.resolve(itemId, itemInstances) ?: continue
            if (resolved.type != ItemType.EQUIPMENT) continue
            effects += resolved.effects.applyStatuses
        }
        return effects
    }

    fun applyOnHitStatuses(
        attacker: CombatActor,
        defender: CombatActor,
        extraOnHitStatuses: List<CombatStatusApplyDef> = emptyList()
    ): Int {
        val applications = if (extraOnHitStatuses.isEmpty()) {
            attacker.onHitStatuses
        } else {
            attacker.onHitStatuses + extraOnHitStatuses
        }
        if (applications.isEmpty()) return 0
        if (defender.runtime.statusImmunitySeconds > 0.0) {
            val subject = logBuilder.subjectLabel(defender)
            logBuilder.combatLog(
                logBuilder.colorize(
                    "$subject esta imune a status por ${logBuilder.format(defender.runtime.statusImmunitySeconds)}s.",
                    CombatLogBuilder.ansiBlue
                )
            )
            return 0
        }
        var current = defender.runtime.statuses
        var appliedCount = 0
        for (application in applications) {
            val source = application.source.ifBlank { attacker.name }
            val tunedApplication = tuneStatusApplication(
                base = application,
                sourceModifiers = attacker.talentModifiers,
                targetModifiers = defender.talentModifiers
            )
            val applied = StatusSystem.applyStatus(
                current = current,
                application = tunedApplication,
                rng = rng,
                defaultSource = source
            )
            current = applied.statuses
            if (applied.applied) {
                appliedCount += 1
                val sourceSuffix = if (source.isBlank()) "" else " ($source)"
                val subject = logBuilder.subjectLabel(defender)
                logBuilder.combatLog(
                    logBuilder.colorize(
                        "$subject esta ${StatusSystem.statusAdjective(application.type)}$sourceSuffix.",
                        CombatLogBuilder.ansiYellow
                    )
                )
            }
        }
        defender.runtime = defender.runtime.copy(statuses = current)
        return appliedCount
    }

    fun tuneStatusApplication(
        base: CombatStatusApplyDef,
        sourceModifiers: TalentCombatModifiers,
        targetModifiers: TalentCombatModifiers
    ): CombatStatusApplyDef {
        val sourceStatus = sourceModifiers.status
        val targetStatus = targetModifiers.status
        val type = base.type

        var chance = base.chancePct +
            sourceStatus.applyChanceBonusPct +
            (sourceStatus.applyChanceBonusByType[type] ?: 0.0)
        chance = chance.coerceIn(0.0, 100.0)

        var durationMultiplier = sourceStatus.durationMultiplier *
            (sourceStatus.durationMultiplierByType[type] ?: 1.0) *
            targetStatus.incomingDurationMultiplier *
            (targetStatus.incomingDurationMultiplierByType[type] ?: 1.0)

        if (type == StatusType.SLOW) {
            durationMultiplier *= sourceModifiers.atb.slowInflictedMultiplier
        }
        durationMultiplier = durationMultiplier.coerceIn(0.1, 5.0)
        val durationSeconds = (base.durationSeconds * durationMultiplier).coerceAtLeast(0.1)

        return base.copy(
            chancePct = chance,
            durationSeconds = durationSeconds
        )
    }

    fun statusConditionalDamageMultiplier(attacker: CombatActor, defender: CombatActor): Double {
        val mods = attacker.talentModifiers.status
        if (mods.bonusDamageVsStatusPct.isEmpty() &&
            mods.bonusDamageWhileSelfHasStatusPct.isEmpty() &&
            mods.bonusDamageVsLowHpPct <= 0.0
        ) {
            return 1.0
        }

        var bonusPct = 0.0
        for ((type, pct) in mods.bonusDamageVsStatusPct) {
            if (hasStatus(defender.runtime.statuses, type)) {
                bonusPct += pct
            }
        }
        for ((type, pct) in mods.bonusDamageWhileSelfHasStatusPct) {
            if (hasStatus(attacker.runtime.statuses, type)) {
                bonusPct += pct
            }
        }
        val defenderMaxHp = defender.stats.derived.hpMax.coerceAtLeast(1.0)
        val defenderHpPct = (defender.currentHp / defenderMaxHp) * 100.0
        if (defenderHpPct <= mods.lowHpThresholdPct) {
            bonusPct += mods.bonusDamageVsLowHpPct
        }
        return (1.0 + bonusPct / 100.0).coerceIn(0.1, 5.0)
    }

    fun consumeTargetStatusesOnHit(attacker: CombatActor, defender: CombatActor) {
        val consumeRules = attacker.talentModifiers.status.consumeChanceByType
        if (consumeRules.isEmpty() || defender.runtime.statuses.isEmpty()) return

        var current = defender.runtime.statuses
        var changed = false
        for ((type, chancePct) in consumeRules) {
            val clampedChance = chancePct.coerceIn(0.0, 100.0)
            if (clampedChance <= 0.0) continue
            val index = current.indexOfFirst { it.type == type }
            if (index < 0) continue
            if (rng.nextDouble(0.0, 100.0) > clampedChance) continue

            val (next, consumed) = consumeStatusStackAt(current, index)
            if (!consumed) continue
            current = next
            changed = true
            val subject = logBuilder.subjectLabel(defender)
            logBuilder.combatLog(
                logBuilder.colorize(
                    "$subject perdeu ${StatusSystem.displayName(type)} por consumo de efeito.",
                    CombatLogBuilder.ansiBlue
                )
            )
        }
        if (changed) {
            defender.runtime = defender.runtime.copy(statuses = current)
        }
    }

    fun applyActionBarShiftOnHit(attacker: CombatActor, defender: CombatActor) {
        val lossPct = attacker.talentModifiers.atb.actionBarLossOnHitPct.coerceIn(0.0, 95.0)
        if (lossPct <= 0.0) return
        if (defender.runtime.state == CombatState.DEAD) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val lossValue = threshold * (lossPct / 100.0)
        val newBar = (defender.runtime.actionBar - lossValue).coerceAtLeast(0.0)
        val shouldDropReady = defender.runtime.state == CombatState.READY && newBar < threshold
        val newState = if (shouldDropReady) CombatState.IDLE else defender.runtime.state
        defender.runtime = defender.runtime.copy(
            actionBar = newBar,
            state = newState,
            readySinceSeconds = if (shouldDropReady) null else defender.runtime.readySinceSeconds
        )
    }

    fun applyActionBarGainOnDamaged(defender: CombatActor) {
        val gainPct = defender.talentModifiers.atb.actionBarGainOnDamagedPct.coerceIn(0.0, 95.0)
        if (gainPct <= 0.0) return
        if (defender.runtime.state == CombatState.DEAD) return
        if (defender.runtime.state != CombatState.IDLE && defender.runtime.state != CombatState.READY) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val gainValue = threshold * (gainPct / 100.0)
        val newBar = (defender.runtime.actionBar + gainValue).coerceAtMost(threshold)
        defender.runtime = defender.runtime.copy(actionBar = newBar)
    }

    fun maybeInterruptCast(attacker: CombatActor, defender: CombatActor) {
        if (defender.runtime.state != CombatState.CASTING) return
        val chance = (
            attacker.talentModifiers.atb.interruptChanceBonusPct -
                defender.talentModifiers.atb.interruptResistPct
            ).coerceIn(0.0, 100.0)
        if (chance <= 0.0) return
        if (rng.nextDouble(0.0, 100.0) > chance) return

        val threshold = defender.runtime.actionThreshold.coerceAtLeast(1.0)
        val interruptedBar = (defender.runtime.actionBar - threshold * 0.5).coerceAtLeast(0.0)
        val interruptedGcd = max(
            defender.runtime.gcdRemaining,
            (balance.combat.globalCooldownSeconds * defender.talentModifiers.atb.gcdMultiplier).coerceAtLeast(0.05)
        )
        defender.pendingAction = null
        defender.pendingAfterResolve = null
        defender.runtime = defender.runtime.copy(
            state = CombatState.IDLE,
            castRemaining = 0.0,
            castTotal = 0.0,
            currentSkillId = null,
            readySinceSeconds = null,
            actionBar = interruptedBar,
            gcdRemaining = interruptedGcd
        )
        val subject = logBuilder.subjectLabel(defender)
        logBuilder.combatLog(logBuilder.colorize("Cast interrompido: $subject.", CombatLogBuilder.ansiYellow))
    }

    fun emitStatusTickMessages(
        actor: CombatActor,
        tick: rpg.status.StatusTickResult,
        includeDamage: Boolean = true
    ) {
        val subject = logBuilder.subjectLabel(actor)
        if (includeDamage) {
            for (event in tick.damageEvents) {
                val sourceSuffix = if (event.source.isBlank()) "" else " (${event.source})"
                logBuilder.combatLog(
                    logBuilder.colorize(
                        "$subject esta ${StatusSystem.statusAdjective(event.type)}$sourceSuffix. Sofreu ${logBuilder.format(event.damage)} de dano.",
                        CombatLogBuilder.ansiYellow
                    )
                )
            }
        }
        for (event in tick.expiredEvents) {
            logBuilder.combatLog(
                logBuilder.colorize(
                    "$subject nao esta mais ${StatusSystem.statusAdjective(event.type)}.",
                    CombatLogBuilder.ansiBlue
                )
            )
        }
    }

    private fun applyStatusEffect(actor: CombatActor, deltaTime: Double): rpg.status.StatusTickResult {
        val tick = StatusSystem.tick(
            current = actor.runtime.statuses,
            deltaSeconds = deltaTime,
            targetMaxHp = actor.stats.derived.hpMax,
            slowEffectMultiplier = actor.talentModifiers.atb.slowReceivedMultiplier
        )
        actor.runtime = actor.runtime.copy(
            statuses = tick.statuses,
            statusSpeedMultiplier = tick.speedMultiplier,
            statusDamageMultiplier = tick.damageMultiplier
        )
        return tick
    }

    private fun applyMonsterAffinityToStatusTick(
        actor: CombatActor,
        tick: rpg.status.StatusTickResult
    ): rpg.status.StatusTickResult {
        if (tick.damageEvents.isEmpty()) return tick
        if (actor.kind != CombatantKind.MONSTER) return tick
        if (monsterAffinityService == null) return tick

        val adjustedEvents = tick.damageEvents.map { event ->
            val channel = DamageChannel.fromStatusType(event.type)
            val multiplier = if (channel == null) {
                1.0
            } else {
                resolveMonsterAffinityMultiplier(actor, channel)
            }
            event.copy(damage = (event.damage * multiplier).coerceAtLeast(0.0))
        }
        val totalDamage = adjustedEvents.sumOf { it.damage }
        return tick.copy(
            dotDamage = totalDamage,
            damageEvents = adjustedEvents
        )
    }

    private fun applyMonsterAffinityToActionDot(
        actor: CombatActor,
        dot: rpg.status.StatusActionDotResult
    ): rpg.status.StatusActionDotResult {
        if (dot.events.isEmpty()) return dot
        if (actor.kind != CombatantKind.MONSTER) return dot
        if (monsterAffinityService == null) return dot

        val adjustedEvents = dot.events.map { event ->
            val channel = DamageChannel.fromStatusType(event.type)
            val multiplier = if (channel == null) {
                1.0
            } else {
                resolveMonsterAffinityMultiplier(actor, channel)
            }
            event.copy(damage = (event.damage * multiplier).coerceAtLeast(0.0))
        }
        val totalDamage = adjustedEvents.sumOf { it.damage }
        return dot.copy(totalDamage = totalDamage, events = adjustedEvents)
    }

    private fun emitActionDotMessages(
        actor: CombatActor,
        dot: rpg.status.StatusActionDotResult
    ) {
        if (dot.events.isEmpty()) return
        val subject = logBuilder.subjectLabel(actor)
        for (event in dot.events) {
            val stackLabel = if (event.stacks == 1) "Stack" else "Stacks"
            val sourceSuffix = if (event.source.isBlank()) "" else " (${event.source})"
            logBuilder.combatLog(
                logBuilder.colorize(
                    "(${event.stacks} $stackLabel): $subject esta ${StatusSystem.statusAdjective(event.type)}$sourceSuffix! Sofreu ${logBuilder.format(event.damage)} de dano.",
                    CombatLogBuilder.ansiRed
                )
            )
        }
    }

    private fun resolveMonsterAffinityMultiplier(
        defender: CombatActor,
        channel: DamageChannel
    ): Double {
        if (defender.kind != CombatantKind.MONSTER) return 1.0
        val service = monsterAffinityService ?: return 1.0
        val archetypeId = defender.monsterArchetypeId
            ?: defender.id.takeIf { it.isNotBlank() }
            ?: return 1.0
        return service.multiplierFor(
            archetypeId = archetypeId,
            typeIdHint = defender.monsterTypeId,
            tags = defender.monsterTags,
            channel = channel
        )
    }

    private fun consumeStatusStackAt(
        statuses: List<rpg.status.StatusEffectInstance>,
        index: Int
    ): Pair<List<rpg.status.StatusEffectInstance>, Boolean> {
        if (index !in statuses.indices) return statuses to false
        val target = statuses[index]
        val mutable = statuses.toMutableList()
        if (target.stackable && target.stacks > 1) {
            mutable[index] = target.copy(stacks = target.stacks - 1)
        } else {
            mutable.removeAt(index)
        }
        return mutable to true
    }

    private fun hasStatus(
        statuses: List<rpg.status.StatusEffectInstance>,
        type: StatusType
    ): Boolean {
        return statuses.any { it.type == type }
    }
}
