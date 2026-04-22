package rpg.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object Combat {
    fun attack(
        attacker: ComputedStats,
        defender: ComputedStats,
        rng: Random,
        preferMagic: Boolean? = null
    ): AttackResult {
        val type = chooseDamageType(attacker, preferMagic)
        val hit = rollHit(attacker, defender, rng)
        if (!hit) {
            return AttackResult(hit = false, crit = false, damage = 0.0, lifesteal = 0.0, type = type)
        }

        val crit = rollCrit(attacker, rng)
        val damage = calcDamage(attacker, defender, type, crit)
        val lifesteal = damage * (attacker.derived.vampirismPct / 100.0)
        return AttackResult(hit = true, crit = crit, damage = damage, lifesteal = lifesteal, type = type)
    }

    private fun chooseDamageType(attacker: ComputedStats, preferMagic: Boolean?): DamageType {
        if (preferMagic != null) {
            return if (preferMagic) DamageType.MAGIC else DamageType.PHYSICAL
        }
        return if (attacker.derived.damageMagic > attacker.derived.damagePhysical) {
            DamageType.MAGIC
        } else {
            DamageType.PHYSICAL
        }
    }

    private fun rollHit(attacker: ComputedStats, defender: ComputedStats, rng: Random): Boolean {
        val diff = attacker.derived.accuracy - defender.derived.evasion
        val chance = (80.0 + diff * 0.1).coerceIn(5.0, 95.0)
        return rng.nextDouble(0.0, 100.0) <= chance
    }

    private fun rollCrit(attacker: ComputedStats, rng: Random): Boolean {
        val chance = attacker.derived.critChancePct.coerceIn(0.0, 100.0)
        return rng.nextDouble(0.0, 100.0) <= chance
    }

    private fun calcDamage(
        attacker: ComputedStats,
        defender: ComputedStats,
        type: DamageType,
        crit: Boolean
    ): Double {
        val base = if (type == DamageType.MAGIC) attacker.derived.damageMagic else attacker.derived.damagePhysical
        val pen = if (type == DamageType.MAGIC) attacker.derived.penMagic else attacker.derived.penPhysical
        val defense = if (type == DamageType.MAGIC) defender.derived.defMagic else defender.derived.defPhysical
        val effectiveDef = max(0.0, defense - pen)
        val reduction = effectiveDef / (effectiveDef + 100.0)
        val extraReduction = defender.derived.damageReductionPct / 100.0
        val totalReduction = min(0.9, reduction + extraReduction)
        var damage = base * (1.0 - totalReduction)
        if (crit) {
            damage *= attacker.derived.critDamagePct / 100.0
        }
        return max(1.0, damage)
    }
}

data class AttackResult(
    val hit: Boolean,
    val crit: Boolean,
    val damage: Double,
    val lifesteal: Double,
    val type: DamageType
)

enum class DamageType {
    PHYSICAL,
    MAGIC
}
