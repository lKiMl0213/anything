package rpg.scaling

import rpg.model.Attributes

object SoftCapEngine {
    private const val CAP_MULTIPLIER = 6
    private const val SOFTNESS = 0.5

    fun apply(attributes: Attributes, level: Int): Attributes {
        val cap = (level.coerceAtLeast(1) * CAP_MULTIPLIER).toDouble()
        return Attributes(
            str = soften(attributes.str.toDouble(), cap).toInt(),
            agi = soften(attributes.agi.toDouble(), cap).toInt(),
            dex = soften(attributes.dex.toDouble(), cap).toInt(),
            vit = soften(attributes.vit.toDouble(), cap).toInt(),
            `int` = soften(attributes.`int`.toDouble(), cap).toInt(),
            spr = soften(attributes.spr.toDouble(), cap).toInt(),
            luk = soften(attributes.luk.toDouble(), cap).toInt()
        )
    }

    private fun soften(value: Double, cap: Double): Double {
        return if (value <= cap) {
            value
        } else {
            cap + (value - cap) * SOFTNESS
        }
    }
}
