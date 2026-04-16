package rpg.monster

import kotlin.math.max
import kotlin.math.roundToInt
import rpg.model.GameBalanceDef

object MonsterXpEngine {
    fun baseXp(powerScore: Double, templateBase: Int, balance: GameBalanceDef): Int {
        val scaled = (powerScore * balance.xpPowerScale).roundToInt()
        return max(templateBase, scaled)
    }
}
