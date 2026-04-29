package rpg.combat

import rpg.model.GameBalanceDef

internal class CombatTurnResolver(
    private val balance: GameBalanceDef,
    private val statusProcessor: CombatStatusProcessor
) {
    fun nextReadyActor(candidates: List<CombatActor>): CombatActor? {
        val ready = candidates
            .filter { it.currentHp > 0.0 }
            .filter { it.runtime.state == CombatState.READY }
            .filter { it.runtime.gcdRemaining <= 0.0 }
        if (ready.isEmpty()) return null

        return ready.sortedWith(
            compareBy<CombatActor> { it.runtime.readySinceSeconds ?: Double.MAX_VALUE }
                .thenByDescending { computeFillRate(it) }
                .thenBy { if (it.kind == CombatantKind.PLAYER) 0 else 1 }
        ).first()
    }

    fun advanceTick(
        deltaTime: Double,
        elapsedSeconds: Double,
        player: CombatActor,
        monster: CombatActor,
        onCastComplete: (CombatActor, CombatActor) -> Unit,
        telemetry: MutableCombatTelemetry
    ) {
        listOf(player, monster).forEach { actor ->
            val opponent = if (actor.kind == CombatantKind.PLAYER) monster else player

            if (actor.runtime.state == CombatState.DEAD) return@forEach

            val tick = statusProcessor.tickStatus(actor, deltaTime)
            statusProcessor.emitStatusTickMessages(actor, tick, includeDamage = false)

            if (actor.currentHp <= 0.0) {
                actor.currentHp = 0.0
                actor.runtime = actor.runtime.copy(
                    state = CombatState.DEAD,
                    readySinceSeconds = null
                )
                return@forEach
            }

            var runtime = actor.runtime.copy(
                gcdRemaining = (actor.runtime.gcdRemaining - deltaTime).coerceAtLeast(0.0),
                skillCooldowns = decrementCooldowns(actor.runtime.skillCooldowns, deltaTime),
                statusImmunitySeconds = (actor.runtime.statusImmunitySeconds - deltaTime).coerceAtLeast(0.0),
                tempBuffRemainingSeconds = (actor.runtime.tempBuffRemainingSeconds - deltaTime).coerceAtLeast(0.0)
            )
            if (runtime.tempBuffRemainingSeconds <= 0.0) {
                runtime = runtime.copy(
                    tempBuffDamageMultiplier = 1.0,
                    tempBuffFillRateMultiplier = 1.0
                )
            }

            if (runtime.state == CombatState.CASTING) {
                val castRemaining = (runtime.castRemaining - deltaTime).coerceAtLeast(0.0)
                runtime = runtime.copy(castRemaining = castRemaining)
                actor.runtime = runtime
                if (castRemaining <= 0.0) {
                    onCastComplete(actor, opponent)
                }
                return@forEach
            }

            if (tick.actionBlocked) {
                actor.runtime = runtime.copy(
                    state = CombatState.STUNNED,
                    readySinceSeconds = null
                )
                return@forEach
            }
            if (runtime.state == CombatState.STUNNED) {
                runtime = runtime.copy(state = CombatState.IDLE)
            }

            if (runtime.state == CombatState.READY) {
                actor.runtime = runtime
                return@forEach
            }

            if (runtime.state != CombatState.IDLE || runtime.gcdRemaining > 0.0) {
                actor.runtime = runtime
                return@forEach
            }

            val fillRate = computeFillRate(actor)
            val newBar = (runtime.actionBar + fillRate * deltaTime).coerceAtMost(runtime.actionThreshold)
            runtime = runtime.copy(actionBar = newBar)
            if (newBar >= runtime.actionThreshold) {
                runtime = runtime.copy(
                    state = CombatState.READY,
                    readySinceSeconds = elapsedSeconds + deltaTime
                )
            }
            actor.runtime = runtime
        }
    }

    fun effectiveCooldownSeconds(
        baseCooldownSeconds: Double,
        cdrPct: Double,
        cooldownMultiplier: Double = 1.0
    ): Double {
        if (baseCooldownSeconds <= 0.0) return 0.0
        val multiplier = (1.0 - cdrPct.coerceIn(0.0, 90.0) / 100.0).coerceAtLeast(0.1)
        val talentMultiplier = cooldownMultiplier.coerceIn(0.1, 5.0)
        return (baseCooldownSeconds * multiplier * talentMultiplier).coerceAtLeast(0.1)
    }

    fun applySkillCooldown(actor: CombatActor, spec: CombatSkillSpec) {
        if (spec.cooldownSeconds <= 0.0) return
        val effectiveCd = effectiveCooldownSeconds(
            baseCooldownSeconds = spec.cooldownSeconds,
            cdrPct = actor.stats.derived.cdrPct,
            cooldownMultiplier = actor.talentModifiers.atb.cooldownMultiplier
        )
        actor.runtime = actor.runtime.copy(
            skillCooldowns = actor.runtime.skillCooldowns + (spec.id to effectiveCd)
        )
    }

    fun effectiveCastTime(actor: CombatActor, baseCastSeconds: Double): Double {
        if (baseCastSeconds <= 0.0) return 0.0
        return (baseCastSeconds * actor.talentModifiers.atb.castTimeMultiplier).coerceAtLeast(0.0)
    }

    fun startCasting(
        actor: CombatActor,
        action: CombatAction,
        castTime: Double,
        afterResolve: (() -> Unit)? = null
    ) {
        actor.pendingAction = action
        actor.pendingAfterResolve = afterResolve
        actor.runtime = actor.runtime.copy(
            state = CombatState.CASTING,
            castRemaining = castTime,
            castTotal = castTime,
            readySinceSeconds = null,
            currentSkillId = action.javaClass.simpleName
        )
    }

    fun endAction(actor: CombatActor, actionBarCarryPct: Double = 0.0) {
        actor.pendingAction = null
        actor.pendingAfterResolve = null
        val carryPct = actionBarCarryPct.coerceIn(0.0, 95.0)
        val threshold = actor.runtime.actionThreshold.coerceAtLeast(1.0)
        val carryValue = threshold * (carryPct / 100.0)
        val gcdSeconds = (
            balance.combat.globalCooldownSeconds *
                actor.talentModifiers.atb.gcdMultiplier
            ).coerceAtLeast(0.05)
        actor.runtime = actor.runtime.copy(
            actionBar = carryValue,
            gcdRemaining = gcdSeconds,
            castRemaining = 0.0,
            castTotal = 0.0,
            readySinceSeconds = null,
            state = CombatState.IDLE,
            currentSkillId = null
        )
    }

    fun computeFillRate(actor: CombatActor): Double {
        if (actor.currentHp <= 0.0 || actor.runtime.state == CombatState.DEAD) return 0.0
        val baseSpeed = actor.stats.derived.attackSpeed
        val speedRating = baseSpeed *
            balance.combat.speedScale *
            (1.0 + actor.speedBonusPct / 100.0) *
            actor.talentModifiers.atb.fillRateMultiplier *
            actor.talentModifiers.atb.hasteMultiplier *
            actor.runtime.statusSpeedMultiplier *
            actor.runtime.tempBuffFillRateMultiplier
        val effectiveSpeed = applySoftCap(speedRating)
        return effectiveSpeed / balance.combat.speedNormalization
    }

    fun decrementCooldowns(cooldowns: Map<String, Double>, deltaTime: Double): Map<String, Double> {
        if (cooldowns.isEmpty()) return emptyMap()
        val updated = mutableMapOf<String, Double>()
        for ((id, seconds) in cooldowns) {
            val remain = (seconds - deltaTime).coerceAtLeast(0.0)
            if (remain > 0.0) {
                updated[id] = remain
            }
        }
        return updated
    }

    private fun applySoftCap(speed: Double): Double {
        val softCap = balance.combat.softCap
        return when {
            speed <= softCap.threshold1 -> speed
            speed <= softCap.threshold2 -> {
                softCap.threshold1 + (speed - softCap.threshold1) * softCap.multiplier1
            }

            else -> {
                val first = softCap.threshold1
                val second = (softCap.threshold2 - softCap.threshold1) * softCap.multiplier1
                val rest = (speed - softCap.threshold2) * softCap.multiplier2
                first + second + rest
            }
        }
    }
}
