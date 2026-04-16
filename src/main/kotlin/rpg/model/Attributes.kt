package rpg.model

import kotlinx.serialization.Serializable

@Serializable
data class Attributes(
    val str: Int = 0,
    val agi: Int = 0,
    val dex: Int = 0,
    val vit: Int = 0,
    val `int`: Int = 0,
    val spr: Int = 0,
    val luk: Int = 0
) {
    operator fun plus(other: Attributes): Attributes = Attributes(
        str = str + other.str,
        agi = agi + other.agi,
        dex = dex + other.dex,
        vit = vit + other.vit,
        `int` = `int` + other.`int`,
        spr = spr + other.spr,
        luk = luk + other.luk
    )

    fun scale(multiplier: Double): Attributes = Attributes(
        str = (str * multiplier).toInt(),
        agi = (agi * multiplier).toInt(),
        dex = (dex * multiplier).toInt(),
        vit = (vit * multiplier).toInt(),
        `int` = (`int` * multiplier).toInt(),
        spr = (spr * multiplier).toInt(),
        luk = (luk * multiplier).toInt()
    )
}
