package rpg.events

import rpg.model.Attributes
import rpg.model.DerivedStats

object EventPool {
    private val liquidEffects = listOf(
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.HealFlat(hp = 1.0))),
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.HealFlat(hp = 5.0))),
        EventRecipe(Rarity.COMMON, 15, listOf(EventEffect.HealFlat(hp = 10.0))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.HealPercentMax(hpPercent = 0.10))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.HealPercentCurrent(hpPercent = 0.20))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.HealFlat(mp = 5.0))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.HealFlat(mp = 15.0))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.HealPercentCurrent(mpPercent = 0.20))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.BuffAllAttributes(multiplier = 1.05, duration = 3))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.DerivedMultiplier(duration = 3, hpMaxPct = 5.0))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.DerivedMultiplier(duration = 3, mpMaxPct = 10.0))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.RegenPerRoom(duration = 5, hpPct = 0.02))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.RegenPerRoom(duration = 5, mpPct = 0.02))),
        EventRecipe(Rarity.COMMON, 12, listOf(EventEffect.DamagePercentCurrent(hpPercent = 0.10))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.DamagePercentCurrent(hpPercent = 0.20))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.BuffAllAttributes(multiplier = 0.90, duration = 3))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, hpMaxPct = -5.0))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.AGI)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.DEX)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.VIT)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.INT)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.SPR)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.LUK)))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.HealFlat(mp = 3.0))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.HealFlat(hp = 7.0))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.FlatAttributeBonus(bonus = Attributes(str = 1), duration = 1))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedAdd(duration = 2, add = DerivedStats(defPhysical = 2.0)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.DerivedAdd(duration = 1, add = DerivedStats(defPhysical = 4.0)))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.BuffAttributePercent(multiplier = 1.03, duration = 2, attributes = listOf(Attribute.INT)))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, defPhysicalPct = 10.0))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, defMagicPct = 10.0))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, damageReductionPct = 10.0))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.DerivedMultiplier(duration = 2, attackSpeedPct = 10.0))),
        EventRecipe(Rarity.COMMON, 5, listOf(EventEffect.DerivedMultiplier(duration = 2, moveSpeedPct = 10.0))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.BuffAllAttributes(multiplier = 1.01, duration = 2))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, hpMaxPct = 2.0))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.PermanentAttributePercent(percent = 0.01, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.COMMON, 6, listOf(EventEffect.PermanentAttributePercent(percent = 0.01, attributes = listOf(Attribute.AGI))))
    )

    private val rareLiquid = listOf(
        EventRecipe(Rarity.RARE, 12, listOf(EventEffect.BuffAllAttributes(multiplier = 1.10, duration = 3))),
        EventRecipe(Rarity.RARE, 12, listOf(EventEffect.BuffAttribute(multiplier = 1.15, duration = 2, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.RARE, 12, listOf(EventEffect.BuffAttribute(multiplier = 1.15, duration = 2, attributes = listOf(Attribute.INT)))),
        EventRecipe(Rarity.RARE, 12, listOf(EventEffect.BuffAttribute(multiplier = 1.15, duration = 2, attributes = listOf(Attribute.AGI)))),
        EventRecipe(Rarity.RARE, 10, listOf(EventEffect.DerivedMultiplier(duration = 3, hpMaxPct = 10.0))),
        EventRecipe(Rarity.RARE, 10, listOf(EventEffect.DerivedMultiplier(duration = 2, hpMaxPct = 15.0))),
        EventRecipe(Rarity.RARE, 10, listOf(EventEffect.DerivedMultiplier(duration = 2, mpMaxPct = 15.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.SetNextHealMultiplier(2.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.IgnoreNextDebuff())),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.ReviveOnce())),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.BuffAttribute(multiplier = 1.10, duration = 5, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 1, damagePhysicalPct = 25.0, damageMagicPct = 25.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 3, critChancePct = 15.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 2, attackSpeedPct = 15.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 2, moveSpeedPct = 15.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.BuffRandomAttributes(multiplier = 1.10, duration = 3, count = 2))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.BuffAttributePercent(multiplier = 1.10, duration = 3, attributes = listOf(Attribute.INT, Attribute.SPR)))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 3, evasionPct = 20.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 2, defPhysicalPct = 15.0, defMagicPct = 15.0))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentAttributePercent(percent = 0.05, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentAttributePercent(percent = 0.05, attributes = listOf(Attribute.AGI)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentAttributePercent(percent = 0.05, attributes = listOf(Attribute.INT)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentDerivedMult(mult = DerivedStats(hpMax = 5.0)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentDerivedMult(mult = DerivedStats(mpMax = 5.0)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.PermanentDerivedAdd(add = DerivedStats(hpMax = 10.0)))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.ConvertMpToHp(percent = 0.10))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.RandomAttributePerRoom(amount = 1, duration = 3))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.ExtendRoomBuffs(1))),
        EventRecipe(Rarity.RARE, 4, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.02)))
    )

    private val epicLiquid = listOf(
        EventRecipe(Rarity.EPIC, 10, listOf(EventEffect.BuffAllAttributes(multiplier = 1.15, duration = 1))),
        EventRecipe(Rarity.EPIC, 10, listOf(EventEffect.BuffAllAttributes(multiplier = 1.20, duration = 1))),
        EventRecipe(Rarity.EPIC, 8, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.05))),
        EventRecipe(Rarity.EPIC, 8, listOf(EventEffect.PermanentBuff(percent = 0.05, attributes = Attribute.values().toList()))),
        EventRecipe(Rarity.EPIC, 8, listOf(EventEffect.BuffHighestAttribute(multiplier = 2.0, duration = 1))),
        EventRecipe(Rarity.EPIC, 8, listOf(EventEffect.BuffLowestAttribute(multiplier = 1.20, duration = 3))),
        EventRecipe(Rarity.EPIC, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, critChancePct = 15.0))),
        EventRecipe(Rarity.EPIC, 6, listOf(EventEffect.DerivedMultiplier(duration = 3, damageReductionPct = 10.0))),
        EventRecipe(Rarity.EPIC, 6, listOf(EventEffect.DerivedMultiplier(duration = 2, hpMaxPct = 30.0))),
        EventRecipe(Rarity.EPIC, 6, listOf(EventEffect.HealPercentCurrent(mpPercent = 0.50))),
        EventRecipe(Rarity.EPIC, 5, listOf(EventEffect.PermanentRandomAttributes(percent = 0.10, count = 2))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.PermanentDerivedAdd(add = DerivedStats(hpMax = 100.0)))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.DerivedMultiplier(duration = 1, damagePhysicalPct = 20.0, damageMagicPct = 20.0))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.BuffAllAttributes(multiplier = 1.20, duration = 2)))
    )

    private val legendaryLiquid = listOf(
        EventRecipe(Rarity.LEGENDARY, 5, listOf(EventEffect.BuffAllAttributes(multiplier = 1.25, duration = 1))),
        EventRecipe(Rarity.LEGENDARY, 5, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.10))),
        EventRecipe(Rarity.LEGENDARY, 5, listOf(EventEffect.PermanentBuff(percent = 0.15, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.LEGENDARY, 5, listOf(EventEffect.PermanentBuff(percent = 0.15, attributes = listOf(Attribute.INT)))),
        EventRecipe(Rarity.LEGENDARY, 5, listOf(EventEffect.HealPercentMax(hpPercent = 1.0, mpPercent = 1.0))),
        EventRecipe(Rarity.LEGENDARY, 4, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.10))),
        EventRecipe(Rarity.LEGENDARY, 4, listOf(EventEffect.PermanentDerivedMult(mult = DerivedStats(hpMax = 10.0, mpMax = 10.0)))),
        EventRecipe(Rarity.LEGENDARY, 3, listOf(EventEffect.PermanentDerivedAdd(add = DerivedStats(hpMax = 100.0)))),
        EventRecipe(Rarity.LEGENDARY, 3, listOf(EventEffect.BuffAllAttributes(multiplier = 1.20, duration = 1))),
        EventRecipe(Rarity.LEGENDARY, 3, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.10)))
    )

    private val npcHelpEffects = listOf(
        EventRecipe(Rarity.COMMON, 25, listOf(EventEffect.AddGold(10))),
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.AddGold(20))),
        EventRecipe(Rarity.COMMON, 15, listOf(EventEffect.HealPercentMax(hpPercent = 0.10))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.AddItem("potion_small"))),
        EventRecipe(Rarity.RARE, 10, listOf(EventEffect.BuffAllAttributes(multiplier = 1.10, duration = 2))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.AddGold(50))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.AddItem("ether_small"))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.IgnoreNextDebuff())),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.BuffAllAttributes(multiplier = 1.20, duration = 1))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.ReviveOnce()))
    )

    private val chestRewards = listOf(
        EventRecipe(Rarity.COMMON, 30, listOf(EventEffect.AddGold(20))),
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.AddGold(40))),
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.AddItem("potion_small"))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.AddItem("ether_small"))),
        EventRecipe(Rarity.COMMON, 20, listOf(EventEffect.HealPercentCurrent(hpPercent = 0.20))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.DamagePercentCurrent(hpPercent = 0.15))),
        EventRecipe(Rarity.RARE, 15, listOf(EventEffect.BuffAllAttributes(multiplier = 1.10, duration = 2))),
        EventRecipe(Rarity.RARE, 10, listOf(EventEffect.DerivedMultiplier(duration = 2, hpMaxPct = 10.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.AddItem("potion_small"), EventEffect.AddItem("ether_small"))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.AddGold(80))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.DerivedMultiplier(duration = 2, critChancePct = 10.0))),
        EventRecipe(Rarity.EPIC, 6, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.05))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.PermanentBuff(percent = 0.10, attributes = listOf(Attribute.DEX)))),
        EventRecipe(Rarity.EPIC, 4, listOf(EventEffect.AddGold(120))),
        EventRecipe(Rarity.LEGENDARY, 2, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.10)))
    )

    private val compositeEffects = listOf(
        EventRecipe(Rarity.COMMON, 12, listOf(EventEffect.HealFlat(hp = 5.0))),
        EventRecipe(Rarity.COMMON, 10, listOf(EventEffect.HealFlat(mp = 5.0))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.BuffAllAttributes(multiplier = 1.05, duration = 2))),
        EventRecipe(Rarity.COMMON, 8, listOf(EventEffect.BuffAllAttributes(multiplier = 0.95, duration = 2))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 2, damagePhysicalPct = 10.0))),
        EventRecipe(Rarity.RARE, 8, listOf(EventEffect.DerivedMultiplier(duration = 2, damageMagicPct = 10.0))),
        EventRecipe(Rarity.RARE, 6, listOf(EventEffect.BuffRandomAttributes(multiplier = 1.10, duration = 2, count = 2))),
        EventRecipe(Rarity.EPIC, 5, listOf(EventEffect.BuffAllAttributes(multiplier = 1.15, duration = 1))),
        EventRecipe(Rarity.EPIC, 5, listOf(EventEffect.PermanentAttributePercent(percent = 0.05, attributes = listOf(Attribute.STR)))),
        EventRecipe(Rarity.LEGENDARY, 3, listOf(EventEffect.PermanentAllAttributes(multiplier = 1.05))),
        EventRecipe(Rarity.LEGENDARY, 3, listOf(EventEffect.HealPercentMax(hpPercent = 1.0, mpPercent = 1.0)))
    )

    fun recipesFor(source: EventSource): List<EventRecipe> {
        return when (source) {
            EventSource.LIQUID -> liquidEffects + rareLiquid + epicLiquid + legendaryLiquid
            EventSource.NPC_HELP -> npcHelpEffects
            EventSource.CHEST_REWARD -> chestRewards
        }
    }

    fun compositeFor(rarity: Rarity): List<EventRecipe> {
        return compositeEffects.filter { it.rarity == rarity }
    }
}
