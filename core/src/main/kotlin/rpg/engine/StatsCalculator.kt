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

        val damagePhysical = (str * 2.5) + (dex * 1.2)
        val damageMagic = (intel * 3.0)
        val hpMax = 100.0 + (vit * 12.0)
        val mpMax = 50.0 + (spr * 10.0)
        val defPhysical = (vit * 1.5) + (str * 0.5)
        val defMagic = (spr * 1.8) + (intel * 0.7)
        val attackSpeed = 1.0 + (agi * 0.02)
        val moveSpeed = 5.0 + (agi * 0.05)
        val critChancePct = 5.0 + (dex * 0.3) + (luk * 0.2)
        val critDamagePct = 150.0 + (dex * 0.5)
        val vampirismPct = floor(luk / 10.0)
        val cdrPct = spr * 0.15
        val dropBonusPct = luk * 0.2
        val penPhysical = str * 0.5
        val penMagic = intel * 0.7
        val hpRegen = 1.0 + (vit * 0.2)
        val mpRegen = 1.0 + (spr * 0.3)
        val accuracy = dex * 1.5
        val evasion = agi * 0.8
        val tenacityPct = vit * 0.2

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
