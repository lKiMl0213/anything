package rpg.status

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.serialization.Serializable
import rpg.model.CombatStatusApplyDef

@Serializable
enum class StatusType {
    BURNING,
    FROZEN,
    POISONED,
    PARALYZED,
    BLEEDING,
    WEAKNESS,
    SLOW,
    MARKED
}

@Serializable
data class StatusEffectInstance(
    val type: StatusType,
    val remainingSeconds: Double,
    val tickIntervalSeconds: Double,
    val effectValue: Double,
    val stackable: Boolean,
    val source: String,
    val appliedAtEpochMs: Long,
    val stacks: Int = 1,
    val maxStacks: Int = 1,
    val elapsedSinceTick: Double = 0.0
)

data class StatusApplyResult(
    val statuses: List<StatusEffectInstance>,
    val applied: Boolean,
    val message: String? = null
)

data class StatusDamageEvent(
    val type: StatusType,
    val source: String,
    val damage: Double
)

data class StatusExpireEvent(
    val type: StatusType,
    val source: String
)

data class StatusTickResult(
    val statuses: List<StatusEffectInstance>,
    val dotDamage: Double = 0.0,
    val damageEvents: List<StatusDamageEvent> = emptyList(),
    val expiredEvents: List<StatusExpireEvent> = emptyList(),
    val speedMultiplier: Double = 1.0,
    val damageMultiplier: Double = 1.0,
    val actionBlocked: Boolean = false
)

object StatusSystem {
    fun applyStatus(
        current: List<StatusEffectInstance>,
        application: CombatStatusApplyDef,
        rng: Random,
        defaultSource: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): StatusApplyResult {
        val chance = application.chancePct.coerceIn(0.0, 100.0)
        if (chance <= 0.0 || rng.nextDouble(0.0, 100.0) > chance) {
            return StatusApplyResult(current, applied = false)
        }
        val source = application.source.ifBlank { defaultSource }
        val candidate = StatusEffectInstance(
            type = application.type,
            remainingSeconds = application.durationSeconds.coerceAtLeast(0.1),
            tickIntervalSeconds = application.tickIntervalSeconds.coerceAtLeast(0.1),
            effectValue = application.effectValue,
            stackable = application.stackable,
            source = source,
            appliedAtEpochMs = nowEpochMs,
            stacks = 1,
            maxStacks = application.maxStacks.coerceAtLeast(1)
        )

        val existingIndex = current.indexOfFirst { it.type == candidate.type }
        if (existingIndex < 0) {
            return StatusApplyResult(
                statuses = current + candidate,
                applied = true,
                message = "${displayName(candidate.type)} aplicado (${candidate.source})."
            )
        }

        val existing = current[existingIndex]
        val merged = if (candidate.stackable && existing.stackable && existing.maxStacks > 1) {
            val nextStacks = (existing.stacks + 1).coerceAtMost(existing.maxStacks)
            existing.copy(
                remainingSeconds = max(existing.remainingSeconds, candidate.remainingSeconds),
                effectValue = max(existing.effectValue, candidate.effectValue),
                stacks = nextStacks,
                appliedAtEpochMs = nowEpochMs
            )
        } else {
            existing.copy(
                remainingSeconds = max(existing.remainingSeconds, candidate.remainingSeconds),
                effectValue = max(existing.effectValue, candidate.effectValue),
                appliedAtEpochMs = nowEpochMs
            )
        }

        val mutable = current.toMutableList()
        mutable[existingIndex] = merged
        return StatusApplyResult(
            statuses = mutable,
            applied = true,
            message = "${displayName(candidate.type)} renovado (${candidate.source})."
        )
    }

    fun tick(
        current: List<StatusEffectInstance>,
        deltaSeconds: Double,
        targetMaxHp: Double,
        slowEffectMultiplier: Double = 1.0
    ): StatusTickResult {
        if (current.isEmpty()) return StatusTickResult(statuses = emptyList())

        var totalDot = 0.0
        var speedMultiplier = 1.0
        var damageMultiplier = 1.0
        var actionBlocked = false
        val damageEvents = mutableListOf<StatusDamageEvent>()
        val expiredEvents = mutableListOf<StatusExpireEvent>()
        val updated = mutableListOf<StatusEffectInstance>()

        for (status in current) {
            val remaining = (status.remainingSeconds - deltaSeconds).coerceAtLeast(0.0)
            val elapsed = status.elapsedSinceTick + deltaSeconds
            val interval = status.tickIntervalSeconds.coerceAtLeast(0.1)
            val ticks = floor(elapsed / interval).toInt().coerceAtLeast(0)
            val leftover = if (ticks > 0) elapsed - ticks * interval else elapsed

            if (ticks > 0) {
                val dotPerTick = dotDamagePerTick(status, targetMaxHp)
                if (dotPerTick > 0.0) {
                    val damage = dotPerTick * ticks
                    totalDot += damage
                    damageEvents += StatusDamageEvent(
                        type = status.type,
                        source = status.source,
                        damage = damage
                    )
                }
            }

            when (status.type) {
                StatusType.FROZEN -> actionBlocked = true
                StatusType.SLOW -> {
                    val penalty = (status.effectValue.coerceAtLeast(0.0) / 100.0) *
                        status.stacks *
                        slowEffectMultiplier.coerceIn(0.1, 5.0)
                    speedMultiplier *= (1.0 - penalty).coerceIn(0.2, 1.0)
                }
                StatusType.WEAKNESS -> {
                    val penalty = (status.effectValue.coerceAtLeast(0.0) / 100.0) * status.stacks
                    damageMultiplier *= (1.0 - penalty).coerceIn(0.2, 1.0)
                }
                else -> Unit
            }

            if (remaining > 0.0) {
                updated += status.copy(
                    remainingSeconds = remaining,
                    elapsedSinceTick = leftover
                )
            } else {
                expiredEvents += StatusExpireEvent(status.type, status.source)
            }
        }

        return StatusTickResult(
            statuses = updated,
            dotDamage = totalDot,
            damageEvents = damageEvents,
            expiredEvents = expiredEvents,
            speedMultiplier = speedMultiplier,
            damageMultiplier = damageMultiplier,
            actionBlocked = actionBlocked
        )
    }

    fun rollParalyzedFailure(current: List<StatusEffectInstance>, rng: Random): Boolean {
        val paralyze = current.firstOrNull { it.type == StatusType.PARALYZED } ?: return false
        val chance = (paralyze.effectValue * paralyze.stacks).coerceIn(0.0, 90.0)
        if (chance <= 0.0) return false
        return rng.nextDouble(0.0, 100.0) <= chance
    }

    private fun dotDamagePerTick(status: StatusEffectInstance, targetMaxHp: Double): Double {
        val stacks = status.stacks.coerceAtLeast(1)
        return when (status.type) {
            StatusType.BURNING -> (status.effectValue.coerceAtLeast(0.0) * stacks).coerceAtLeast(0.0)
            StatusType.POISONED -> (status.effectValue.coerceAtLeast(0.0) * stacks).coerceAtLeast(0.0)
            StatusType.BLEEDING -> (targetMaxHp * status.effectValue.coerceAtLeast(0.0) * stacks)
                .coerceAtLeast(0.0)
            else -> 0.0
        }
    }

    fun displayName(type: StatusType): String = when (type) {
        StatusType.BURNING -> "Queimando"
        StatusType.FROZEN -> "Congelado"
        StatusType.POISONED -> "Envenenado"
        StatusType.PARALYZED -> "Paralisado"
        StatusType.BLEEDING -> "Sangramento"
        StatusType.WEAKNESS -> "Fraqueza"
        StatusType.SLOW -> "Lentidao"
        StatusType.MARKED -> "Marcado"
    }

    fun statusAdjective(type: StatusType): String = when (type) {
        StatusType.BURNING -> "queimando"
        StatusType.FROZEN -> "congelado"
        StatusType.POISONED -> "envenenado"
        StatusType.PARALYZED -> "paralisado"
        StatusType.BLEEDING -> "sangrando"
        StatusType.WEAKNESS -> "enfraquecido"
        StatusType.SLOW -> "lento"
        StatusType.MARKED -> "marcado"
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
