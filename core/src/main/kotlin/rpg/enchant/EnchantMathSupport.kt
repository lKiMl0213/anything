package rpg.enchant

import rpg.model.AffixDef
import rpg.model.Attributes
import rpg.model.Bonuses
import rpg.model.DerivedStats
import rpg.model.ItemEffects

internal object EnchantMathSupport {
    fun minusBonuses(total: Bonuses, toSubtract: Bonuses): Bonuses {
        return Bonuses(
            attributes = Attributes(
                str = total.attributes.str - toSubtract.attributes.str,
                agi = total.attributes.agi - toSubtract.attributes.agi,
                dex = total.attributes.dex - toSubtract.attributes.dex,
                vit = total.attributes.vit - toSubtract.attributes.vit,
                `int` = total.attributes.`int` - toSubtract.attributes.`int`,
                spr = total.attributes.spr - toSubtract.attributes.spr,
                luk = total.attributes.luk - toSubtract.attributes.luk
            ),
            derivedAdd = minusDerived(total.derivedAdd, toSubtract.derivedAdd),
            derivedMult = minusDerived(total.derivedMult, toSubtract.derivedMult)
        )
    }

    fun mergeEffects(left: ItemEffects, right: ItemEffects): ItemEffects {
        return left.copy(
            hpRestore = left.hpRestore + right.hpRestore,
            mpRestore = left.mpRestore + right.mpRestore,
            hpRestorePct = left.hpRestorePct + right.hpRestorePct,
            mpRestorePct = left.mpRestorePct + right.mpRestorePct,
            fullRestore = left.fullRestore || right.fullRestore,
            clearNegativeStatuses = left.clearNegativeStatuses || right.clearNegativeStatuses,
            statusImmunitySeconds = left.statusImmunitySeconds + right.statusImmunitySeconds,
            roomAttributeMultiplierPct = left.roomAttributeMultiplierPct + right.roomAttributeMultiplierPct,
            roomAttributeDurationRooms = maxOf(left.roomAttributeDurationRooms, right.roomAttributeDurationRooms),
            runAttributeMultiplierPct = left.runAttributeMultiplierPct + right.runAttributeMultiplierPct,
            applyStatuses = left.applyStatuses + right.applyStatuses
        )
    }

    fun sumAffixBonuses(affixNames: List<String>, affixesByName: Map<String, AffixDef>): Bonuses {
        var total = Bonuses()
        for (name in affixNames) {
            val def = affixesByName[name.trim().lowercase()] ?: continue
            total += def.bonuses
        }
        return total
    }

    fun sumAffixEffects(affixNames: List<String>, affixesByName: Map<String, AffixDef>): ItemEffects {
        var total = ItemEffects()
        for (name in affixNames) {
            val def = affixesByName[name.trim().lowercase()] ?: continue
            total = mergeEffects(total, def.effects)
        }
        return total
    }

    private fun minusDerived(left: DerivedStats, right: DerivedStats): DerivedStats {
        return DerivedStats(
            damagePhysical = left.damagePhysical - right.damagePhysical,
            damageMagic = left.damageMagic - right.damageMagic,
            hpMax = left.hpMax - right.hpMax,
            mpMax = left.mpMax - right.mpMax,
            defPhysical = left.defPhysical - right.defPhysical,
            defMagic = left.defMagic - right.defMagic,
            attackSpeed = left.attackSpeed - right.attackSpeed,
            moveSpeed = left.moveSpeed - right.moveSpeed,
            critChancePct = left.critChancePct - right.critChancePct,
            critDamagePct = left.critDamagePct - right.critDamagePct,
            vampirismPct = left.vampirismPct - right.vampirismPct,
            cdrPct = left.cdrPct - right.cdrPct,
            dropBonusPct = left.dropBonusPct - right.dropBonusPct,
            penPhysical = left.penPhysical - right.penPhysical,
            penMagic = left.penMagic - right.penMagic,
            hpRegen = left.hpRegen - right.hpRegen,
            mpRegen = left.mpRegen - right.mpRegen,
            accuracy = left.accuracy - right.accuracy,
            evasion = left.evasion - right.evasion,
            tenacityPct = left.tenacityPct - right.tenacityPct,
            damageReductionPct = left.damageReductionPct - right.damageReductionPct,
            xpGainPct = left.xpGainPct - right.xpGainPct
        )
    }
}

