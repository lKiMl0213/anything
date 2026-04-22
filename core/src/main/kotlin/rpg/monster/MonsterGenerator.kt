package rpg.monster

import kotlin.random.Random
import rpg.model.Attributes
import rpg.model.MonsterArchetypeDef

object MonsterGenerator {
    fun generateAttributes(
        template: MonsterArchetypeDef,
        level: Int,
        rng: Random
    ): Pair<Attributes, Int> {
        val safeLevel = level.coerceAtLeast(1)
        val growth = multiply(template.growthAttributes, safeLevel)
        val base = template.baseAttributes + growth
        val variance = multiply(base, template.variancePct)
        val minCap = subtract(base, variance)
        val maxCap = add(base, variance)
        val final = Attributes(
            str = roll(minCap.str, maxCap.str, rng),
            agi = roll(minCap.agi, maxCap.agi, rng),
            dex = roll(minCap.dex, maxCap.dex, rng),
            vit = roll(minCap.vit, maxCap.vit, rng),
            `int` = roll(minCap.`int`, maxCap.`int`, rng),
            spr = roll(minCap.spr, maxCap.spr, rng),
            luk = roll(minCap.luk, maxCap.luk, rng)
        )
        val starCount = countStars(final, maxCap)
        return final to starCount
    }

    private fun roll(minCap: Int, maxCap: Int, rng: Random): Int {
        if (maxCap <= minCap) return minCap
        return rng.nextInt(minCap, maxCap + 1)
    }

    private fun countStars(final: Attributes, maxCap: Attributes): Int {
        val pairs = listOf(
            final.str to maxCap.str,
            final.agi to maxCap.agi,
            final.dex to maxCap.dex,
            final.vit to maxCap.vit,
            final.`int` to maxCap.`int`,
            final.spr to maxCap.spr,
            final.luk to maxCap.luk
        )
        var stars = 0
        for ((value, cap) in pairs) {
            if (cap <= 0) continue
            if (value >= cap) stars++
        }
        return stars
    }

    private fun multiply(attrs: Attributes, factor: Int): Attributes = Attributes(
        str = attrs.str * factor,
        agi = attrs.agi * factor,
        dex = attrs.dex * factor,
        vit = attrs.vit * factor,
        `int` = attrs.`int` * factor,
        spr = attrs.spr * factor,
        luk = attrs.luk * factor
    )

    private fun multiply(attrs: Attributes, factor: Double): Attributes = Attributes(
        str = (attrs.str * factor).toInt(),
        agi = (attrs.agi * factor).toInt(),
        dex = (attrs.dex * factor).toInt(),
        vit = (attrs.vit * factor).toInt(),
        `int` = (attrs.`int` * factor).toInt(),
        spr = (attrs.spr * factor).toInt(),
        luk = (attrs.luk * factor).toInt()
    )

    private fun add(a: Attributes, b: Attributes): Attributes = Attributes(
        str = a.str + b.str,
        agi = a.agi + b.agi,
        dex = a.dex + b.dex,
        vit = a.vit + b.vit,
        `int` = a.`int` + b.`int`,
        spr = a.spr + b.spr,
        luk = a.luk + b.luk
    )

    private fun subtract(a: Attributes, b: Attributes): Attributes = Attributes(
        str = (a.str - b.str).coerceAtLeast(0),
        agi = (a.agi - b.agi).coerceAtLeast(0),
        dex = (a.dex - b.dex).coerceAtLeast(0),
        vit = (a.vit - b.vit).coerceAtLeast(0),
        `int` = (a.`int` - b.`int`).coerceAtLeast(0),
        spr = (a.spr - b.spr).coerceAtLeast(0),
        luk = (a.luk - b.luk).coerceAtLeast(0)
    )
}
