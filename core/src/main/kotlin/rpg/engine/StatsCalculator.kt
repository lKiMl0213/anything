package rpg.engine

import kotlin.math.floor
import kotlin.math.min
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.DerivedStats

object StatsCalculator {
    private const val CDR_CAP = 40.0

    fun baseDerived(attributes: Attributes): DerivedStats {
        val str = attributes.str.toDouble()
        val agi = attributes.agi.toDouble()
        val dex = attributes.dex.toDouble()
        val vit = attributes.vit.toDouble()
        val intel = attributes.`int`.toDouble()
        val spr = attributes.spr.toDouble()
        val luk = attributes.luk.toDouble()

        val damagePhysical = (str * 0.9) + (dex * 0.8) + (agi * 0.8)
        val damageMagic = (intel * 1.8) + (spr * 0.8)
        val hpMax = 100.0 + (vit * 10.0) + (spr * 0.1)
        val mpMax = 50.0 + (spr * 10.0) + (vit * 0.1)
        val defPhysical = (vit * 0.9) + (str * 0.8) + (luk * 0.4)
        val defMagic = (intel * 0.9) + (spr * 1.5) + (luk * 0.2)
        val attackSpeed = 0.8 + (agi * 0.02) + (dex * 0.02) + (luk * 0.01)
        val moveSpeed = 5.0 + (agi * 0.05) + (dex * 0.02)
        val critChancePct = (dex * 0.3) + (luk * 0.5)
        val critDamagePct = 50 + (luk * 0.5) + (str * 0.1)
        val vampirismPct = (luk / 10.0) + (spr * 0.01)
        val cdrPct = (spr * 0.05) + (luk * 0.05)
        val dropBonusPct = (luk * 0.18)
        val penPhysical = (str * 0.5) + (dex * 0.02) + (luk * 0.01)
        val penMagic = (intel * 0.5) + (spr * 0.5) + (luk * 0.2)
        val hpRegen = 1.0 + (vit * 0.2) + (spr * 0.5)
        val mpRegen = 1.0 + (spr * 0.5)
        val accuracy = (dex * 0.8) + (luk * 0.5)
        val evasion = (agi * 0.8) + (dex * 0.5) + (luk * 0.2)
        val tenacityPct = (vit * 0.18)  + (luk * 0.2)

        return DerivedStats(
            damagePhysical = damagePhysical,
            damageMagic = damageMagic,
            hpMax = hpMax,
            mpMax = mpMax,
            defPhysical = defPhysical,
            defMagic = defMagic,
            attackSpeed = attackSpeed,
            moveSpeed = moveSpeed,
            critChancePct = critChancePct,
            critDamagePct = critDamagePct,
            vampirismPct = vampirismPct,
            cdrPct = cdrPct,
            dropBonusPct = dropBonusPct,
            penPhysical = penPhysical,
            penMagic = penMagic,
            hpRegen = hpRegen,
            mpRegen = mpRegen,
            accuracy = accuracy,
            evasion = evasion,
            tenacityPct = tenacityPct
        )
    }

    fun compute(
        baseAttributes: Attributes,
        bonuses: List<Bonuses>,
        attributeMultiplier: Double = 1.0
    ): ComputedStats {
        val totalBonus = bonuses.fold(Bonuses()) { acc, next -> acc + next }
        val finalAttributes = (baseAttributes + totalBonus.attributes).scale(attributeMultiplier)
        val baseDerived = baseDerived(finalAttributes)
        val withAdd = baseDerived + totalBonus.derivedAdd
        val withMult = withAdd.applyMultiplier(totalBonus.derivedMult)
        val capped = withMult.copy(cdrPct = min(withMult.cdrPct, CDR_CAP))
        return ComputedStats(finalAttributes, capped)
    }
}

data class ComputedStats(
    val attributes: Attributes,
    val derived: DerivedStats
)
