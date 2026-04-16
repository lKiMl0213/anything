package rpg.engine

import kotlin.math.max
import rpg.model.GameBalanceDef

object PowerScoreEngine {
    fun fromStats(stats: ComputedStats, balance: GameBalanceDef): Double {
        val weights = balance.powerWeights
        val offense = (stats.derived.damagePhysical + stats.derived.damageMagic) * weights.damage
        val defense = (stats.derived.defPhysical + stats.derived.defMagic) * weights.defense
        val hp = stats.derived.hpMax * weights.hp
        val speed = stats.derived.attackSpeed * weights.attackSpeed
        val crit = stats.derived.critChancePct * weights.crit
        return max(1.0, offense + defense + hp + speed + crit)
    }

    fun difficultyModifier(
        monsterPower: Double,
        playerPower: Double,
        balance: GameBalanceDef
    ): Double {
        if (playerPower <= 0.0) return balance.difficultyMax
        val ratio = monsterPower / playerPower
        return ratio.coerceIn(balance.difficultyMin, balance.difficultyMax)
    }
}
