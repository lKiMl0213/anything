package rpg.events

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import rpg.engine.ComputedStats
import rpg.model.Attributes
import rpg.model.DerivedStats
import rpg.model.PlayerState

enum class Attribute {
    STR, AGI, DEX, VIT, INT, SPR, LUK
}

enum class Rarity(val weight: Int, val colorHex: String) {
    COMMON(60, "#B0B0B0"),
    RARE(25, "#4FC3F7"),
    EPIC(10, "#BA68C8"),
    LEGENDARY(5, "#FFD54F")
}

data class EventDefinition(
    val id: String,
    val rarity: Rarity,
    val description: String,
    val effects: List<EventEffect>
)

data class EventContext(
    val statsProvider: (PlayerState) -> ComputedStats,
    val rng: Random,
    val playerLevel: Int = 1,
    val depth: Int = 0,
    val tierId: String? = null
)

sealed class EventEffect {
    abstract fun apply(player: PlayerState, context: EventContext): PlayerState

    data class HealFlat(val hp: Double = 0.0, val mp: Double = 0.0) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return applyHealing(player, context, hpDelta = hp, mpDelta = mp)
        }
    }

    data class HealPercentMax(val hpPercent: Double = 0.0, val mpPercent: Double = 0.0) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val stats = context.statsProvider(player)
            val hpDelta = stats.derived.hpMax * hpPercent
            val mpDelta = stats.derived.mpMax * mpPercent
            return applyHealing(player, context, hpDelta, mpDelta)
        }
    }

    data class HealPercentCurrent(val hpPercent: Double = 0.0, val mpPercent: Double = 0.0) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val hpDelta = player.currentHp * hpPercent
            val mpDelta = player.currentMp * mpPercent
            return applyHealing(player, context, hpDelta, mpDelta)
        }
    }

    data class DamagePercentCurrent(val hpPercent: Double = 0.0, val mpPercent: Double = 0.0) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val hpDamage = player.currentHp * hpPercent
            val mpDamage = player.currentMp * mpPercent
            val newHp = max(1.0, player.currentHp - hpDamage)
            val newMp = max(0.0, player.currentMp - mpDamage)
            return player.copy(currentHp = newHp, currentMp = newMp)
        }
    }

    data class BuffAllAttributes(val multiplier: Double, val duration: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return applyRoomMultiplier(player, multiplier, duration)
        }
    }

    data class BuffAttributePercent(
        val multiplier: Double,
        val duration: Int,
        val attributes: List<Attribute>
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val stats = context.statsProvider(player)
            val percent = multiplier - 1.0
            var bonus = Attributes()
            for (attr in attributes) {
                val current = getAttr(stats.attributes, attr)
                val delta = max(1, ceil(current * kotlin.math.abs(percent)).toInt())
                val signed = if (percent >= 0) delta else -delta
                bonus = addAttr(bonus, attr, signed)
            }
            return applyRoomAttrBonus(player, bonus, duration, isDebuff = percent < 0)
        }
    }

    data class BuffAttribute(
        val multiplier: Double,
        val duration: Int,
        val attributes: List<Attribute>
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return BuffAttributePercent(multiplier, duration, attributes).apply(player, context)
        }
    }

    data class FlatAttributeBonus(
        val bonus: Attributes,
        val duration: Int
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val isDebuff = listOf(bonus.str, bonus.agi, bonus.dex, bonus.vit, bonus.`int`, bonus.spr, bonus.luk)
                .any { it < 0 }
            return applyRoomAttrBonus(player, bonus, duration, isDebuff)
        }
    }

    data class BuffHighestAttribute(val multiplier: Double, val duration: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val stats = context.statsProvider(player)
            val (attr, value) = highestAttribute(stats.attributes)
            val percent = multiplier - 1.0
            val delta = max(1, ceil(value * kotlin.math.abs(percent)).toInt())
            val signed = if (percent >= 0) delta else -delta
            val bonus = addAttr(Attributes(), attr, signed)
            return applyRoomAttrBonus(player, bonus, duration, isDebuff = percent < 0)
        }
    }

    data class BuffLowestAttribute(val multiplier: Double, val duration: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val stats = context.statsProvider(player)
            val (attr, value) = lowestAttribute(stats.attributes)
            val percent = multiplier - 1.0
            val delta = max(1, ceil(value * kotlin.math.abs(percent)).toInt())
            val signed = if (percent >= 0) delta else -delta
            val bonus = addAttr(Attributes(), attr, signed)
            return applyRoomAttrBonus(player, bonus, duration, isDebuff = percent < 0)
        }
    }

    data class PermanentAttributePercent(
        val percent: Double,
        val attributes: List<Attribute>
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val stats = context.statsProvider(player)
            var bonus = Attributes()
            for (attr in attributes) {
                val current = getAttr(stats.attributes, attr)
                val delta = max(1, ceil(current * kotlin.math.abs(percent)).toInt())
                val signed = if (percent >= 0) delta else -delta
                bonus = addAttr(bonus, attr, signed)
            }
            return player.copy(runAttrBonus = player.runAttrBonus + bonus)
        }
    }

    data class PermanentBuff(
        val percent: Double,
        val attributes: List<Attribute>
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return PermanentAttributePercent(percent, attributes).apply(player, context)
        }
    }

    data class PermanentAllAttributes(val multiplier: Double) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(runAttrMultiplier = player.runAttrMultiplier * multiplier)
        }
    }

    data class DerivedMultiplier(
        val duration: Int,
        val damagePhysicalPct: Double = 0.0,
        val damageMagicPct: Double = 0.0,
        val hpMaxPct: Double = 0.0,
        val mpMaxPct: Double = 0.0,
        val defPhysicalPct: Double = 0.0,
        val defMagicPct: Double = 0.0,
        val attackSpeedPct: Double = 0.0,
        val moveSpeedPct: Double = 0.0,
        val critChancePct: Double = 0.0,
        val evasionPct: Double = 0.0,
        val damageReductionPct: Double = 0.0
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val mult = DerivedStats(
                damagePhysical = damagePhysicalPct,
                damageMagic = damageMagicPct,
                hpMax = hpMaxPct,
                mpMax = mpMaxPct,
                defPhysical = defPhysicalPct,
                defMagic = defMagicPct,
                attackSpeed = attackSpeedPct,
                moveSpeed = moveSpeedPct,
                critChancePct = critChancePct,
                evasion = evasionPct,
                damageReductionPct = damageReductionPct
            )
            val isDebuff = listOf(
                damagePhysicalPct,
                damageMagicPct,
                hpMaxPct,
                mpMaxPct,
                defPhysicalPct,
                defMagicPct,
                attackSpeedPct,
                moveSpeedPct,
                critChancePct,
                evasionPct,
                damageReductionPct
            ).any { it < 0.0 }
            return applyRoomDerivedMult(player, mult, duration, isDebuff)
        }
    }

    data class RegenPerRoom(
        val duration: Int,
        val hpPct: Double = 0.0,
        val mpPct: Double = 0.0
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            var updated = player
            if (hpPct > 0.0) {
                val rooms = max(player.roomRegenHpRooms, duration)
                val pct = max(player.roomRegenHpPct, hpPct)
                updated = updated.copy(roomRegenHpPct = pct, roomRegenHpRooms = rooms)
            }
            if (mpPct > 0.0) {
                val rooms = max(player.roomRegenMpRooms, duration)
                val pct = max(player.roomRegenMpPct, mpPct)
                updated = updated.copy(roomRegenMpPct = pct, roomRegenMpRooms = rooms)
            }
            return updated
        }
    }

    data class SetNextHealMultiplier(val multiplier: Double) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(nextHealMultiplier = multiplier)
        }
    }

    data class DerivedAdd(
        val duration: Int,
        val add: DerivedStats
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val isDebuff = listOf(
                add.damagePhysical,
                add.damageMagic,
                add.hpMax,
                add.mpMax,
                add.defPhysical,
                add.defMagic,
                add.attackSpeed,
                add.moveSpeed,
                add.critChancePct,
                add.evasion,
                add.damageReductionPct
            ).any { it < 0.0 }
            return applyRoomDerivedAdd(player, add, duration, isDebuff)
        }
    }

    data class PermanentDerivedAdd(val add: DerivedStats) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(runDerivedAdd = player.runDerivedAdd + add)
        }
    }

    data class PermanentDerivedMult(val mult: DerivedStats) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(runDerivedMult = player.runDerivedMult + mult)
        }
    }

    data class ConvertMpToHp(val percent: Double) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val mpCost = player.currentMp * percent
            if (mpCost <= 0.0) return player
            val stats = context.statsProvider(player)
            val newMp = max(0.0, player.currentMp - mpCost)
            val newHp = min(stats.derived.hpMax, player.currentHp + mpCost)
            return player.copy(currentHp = newHp, currentMp = newMp)
        }
    }

    data class ExtendRoomBuffs(val extraRooms: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val rooms = extraRooms.coerceAtLeast(0)
            return player.copy(
                roomEffectRooms = if (player.roomEffectRooms > 0) player.roomEffectRooms + rooms else player.roomEffectRooms,
                roomAttrRooms = if (player.roomAttrRooms > 0) player.roomAttrRooms + rooms else player.roomAttrRooms,
                roomDerivedRooms = if (player.roomDerivedRooms > 0) player.roomDerivedRooms + rooms else player.roomDerivedRooms,
                roomRegenHpRooms = if (player.roomRegenHpRooms > 0) player.roomRegenHpRooms + rooms else player.roomRegenHpRooms,
                roomRegenMpRooms = if (player.roomRegenMpRooms > 0) player.roomRegenMpRooms + rooms else player.roomRegenMpRooms,
                roomAttrRollRooms = if (player.roomAttrRollRooms > 0) player.roomAttrRollRooms + rooms else player.roomAttrRollRooms
            )
        }
    }

    data class RandomAttributePerRoom(val amount: Int, val duration: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val rooms = max(player.roomAttrRollRooms, duration)
            val amt = max(player.roomAttrRollAmount, amount)
            return player.copy(roomAttrRollRooms = rooms, roomAttrRollAmount = amt)
        }
    }

    data class BuffRandomAttributes(
        val multiplier: Double,
        val duration: Int,
        val count: Int
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val attrs = Attribute.values().toList().shuffled(context.rng).take(count)
            return BuffAttributePercent(multiplier, duration, attrs).apply(player, context)
        }
    }

    data class PermanentRandomAttributes(
        val percent: Double,
        val count: Int
    ) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val attrs = Attribute.values().toList().shuffled(context.rng).take(count)
            return PermanentAttributePercent(percent, attrs).apply(player, context)
        }
    }

    data class IgnoreNextDebuff(val enabled: Boolean = true) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(ignoreNextDebuff = enabled)
        }
    }

    data class ReviveOnce(val enabled: Boolean = true) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(reviveOnce = enabled)
        }
    }

    data class AddGold(val amount: Int) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            return player.copy(gold = player.gold + amount)
        }
    }

    data class AddItem(val itemId: String) : EventEffect() {
        override fun apply(player: PlayerState, context: EventContext): PlayerState {
            val inventory = player.inventory.toMutableList()
            inventory.add(itemId)
            return player.copy(inventory = inventory)
        }
    }
}

data class EventRecipe(
    val rarity: Rarity,
    val weight: Int,
    val effects: List<EventEffect>,
    val minDepth: Int = 0,
    val maxDepth: Int = Int.MAX_VALUE,
    val minLevel: Int = 1,
    val maxLevel: Int = Int.MAX_VALUE
)

enum class EventSource {
    LIQUID,
    NPC_HELP,
    CHEST_REWARD
}

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

object TextGenerator {
    private val liquidIntros = listOf(
        "O liquido vibra ao tocar sua pele.",
        "Voce hesita antes de beber.",
        "Algo antigo desperta no frasco.",
        "O gosto e metalico.",
        "O liquido evapora e deixa um brilho no ar.",
        "A mistura reage ao calor da sua mao."
    )

    private val npcIntros = listOf(
        "O viajante observa seu equipamento em silencio.",
        "Uma voz cansada pede ajuda na trilha.",
        "Um estranho acena de longe e se aproxima devagar.",
        "O forasteiro parece nervoso, mas nao hostil.",
        "Um viajante sujo de poeira pede sua atencao."
    )

    private val chestIntros = listOf(
        "O bau range quando voce toca a tampa.",
        "Trancas antigas cobrem o recipiente de madeira.",
        "O metal frio do bau parece recem-polido.",
        "Marcas de garra cercam o bau abandonado.",
        "A fechadura esta torta, como se alguem fugisse com pressa."
    )

    private val sensations = listOf(
        "Seu coracao acelera.",
        "Sua visao distorce por um instante.",
        "Sua respiracao fica pesada.",
        "Voce sente o corpo mais leve.",
        "Uma onda curta de energia passa por voce.",
        "A sensacao some tao rapido quanto veio."
    )

    fun generate(source: EventSource, rarity: Rarity, effects: List<EventEffect>, rng: Random): String {
        val intro = when (source) {
            EventSource.LIQUID -> liquidIntros.random(rng)
            EventSource.NPC_HELP -> npcIntros.random(rng)
            EventSource.CHEST_REWARD -> chestIntros.random(rng)
        }
        val sensation = sensations.random(rng)
        val tone = when (rarity) {
            Rarity.LEGENDARY -> "A energia e avassaladora."
            Rarity.EPIC -> "Algo incomum acabou de acontecer."
            Rarity.RARE -> "Existe valor real nessa escolha."
            Rarity.COMMON -> "Nada explode, mas algo mudou."
        }
        val hint = effectHint(effects)

        val lines = linkedSetOf(intro, sensation, tone, hint)
        return lines.joinToString(" ")
    }

    private fun effectHint(effects: List<EventEffect>): String {
        return when {
            effects.any { it is EventEffect.DamagePercentCurrent } -> "Nem todo ganho vem sem risco."
            effects.any { it is EventEffect.AddGold } -> "Algumas moedas mudaram de dono."
            effects.any { it is EventEffect.AddItem } -> "Voce encontra algo util para seguir."
            effects.any { it is EventEffect.HealFlat || it is EventEffect.HealPercentCurrent || it is EventEffect.HealPercentMax } ->
                "Seu corpo responde com alivio imediato."
            effects.any { it is EventEffect.BuffAllAttributes || it is EventEffect.BuffAttribute || it is EventEffect.BuffAttributePercent } ->
                "Seu desempenho melhora de forma notavel."
            effects.any { it is EventEffect.PermanentAllAttributes || it is EventEffect.PermanentBuff || it is EventEffect.PermanentAttributePercent } ->
                "A mudanca parece mais profunda e duradoura."
            else -> "O ambiente volta ao silencio."
        }
    }
}

class EventBuilder {
    private var source: EventSource = EventSource.LIQUID
    private var rarity: Rarity = Rarity.COMMON
    private val effects = mutableListOf<EventEffect>()

    fun source(value: EventSource) = apply { source = value }

    fun rarity(r: Rarity) = apply { rarity = r }

    fun addEffect(effect: EventEffect) = apply { effects.add(effect) }

    fun addEffects(items: List<EventEffect>) = apply { effects.addAll(items) }

    fun build(rng: Random): EventDefinition {
        val description = TextGenerator.generate(source, rarity, effects, rng)
        return EventDefinition(
            id = UUID.randomUUID().toString(),
            rarity = rarity,
            description = description,
            effects = effects.toList()
        )
    }
}

object EventEngine {
    fun generateEvent(source: EventSource, context: EventContext): EventDefinition {
        val rarity = rollRarity(context)
        val eligible = EventPool.recipesFor(source).filter { isEligible(it, context) }
        val candidates = eligible.filter { it.rarity == rarity }
        val selected = if (candidates.isEmpty()) {
            pickWeighted(eligible.ifEmpty { EventPool.recipesFor(source) }, context.rng)
        } else {
            pickWeighted(candidates, context.rng)
        }
        val builder = EventBuilder()
            .source(source)
            .rarity(rarity)
            .addEffects(selected.effects)

        val extraEffects = rollCompositeEffects(rarity, context)
        if (extraEffects.isNotEmpty()) {
            builder.addEffects(extraEffects)
        }

        return builder.build(context.rng)
    }

    private fun rollRarity(context: EventContext): Rarity {
        val base = Rarity.values().associateWith { it.weight }.toMutableMap()
        if (context.depth < 3) {
            base[Rarity.EPIC] = 0
            base[Rarity.LEGENDARY] = 0
        } else if (context.depth < 6) {
            base[Rarity.LEGENDARY] = 0
        }
        if (context.playerLevel < 5) {
            base[Rarity.RARE] = (base[Rarity.RARE] ?: 0) / 2
        }
        val total = base.values.sum().coerceAtLeast(1)
        val roll = context.rng.nextInt(total)
        var cumulative = 0
        for (rarity in Rarity.values()) {
            cumulative += base[rarity] ?: 0
            if (roll < cumulative) return rarity
        }
        return Rarity.COMMON
    }

    private fun isEligible(recipe: EventRecipe, context: EventContext): Boolean {
        return context.depth in recipe.minDepth..recipe.maxDepth &&
            context.playerLevel in recipe.minLevel..recipe.maxLevel
    }

    private fun rollCompositeEffects(rarity: Rarity, context: EventContext): List<EventEffect> {
        val chance = when (rarity) {
            Rarity.COMMON -> 0.15
            Rarity.RARE -> 0.25
            Rarity.EPIC -> 0.35
            Rarity.LEGENDARY -> 0.5
        }
        if (context.rng.nextDouble() > chance) return emptyList()
        val pool = EventPool.compositeFor(rarity)
        if (pool.isEmpty()) return emptyList()
        val selected = pickWeighted(pool, context.rng)
        return selected.effects
    }
}

object EventExecutor {
    fun execute(event: EventDefinition, player: PlayerState, context: EventContext): PlayerState {
        return event.effects.fold(player) { acc, effect ->
            effect.apply(acc, context)
        }
    }
}

private fun pickWeighted(recipes: List<EventRecipe>, rng: Random): EventRecipe {
    val total = recipes.sumOf { it.weight }
    var roll = rng.nextInt(total)
    for (recipe in recipes) {
        roll -= recipe.weight
        if (roll < 0) return recipe
    }
    return recipes.first()
}

private fun applyHealing(player: PlayerState, context: EventContext, hpDelta: Double, mpDelta: Double): PlayerState {
    val stats = context.statsProvider(player)
    val multiplier = player.nextHealMultiplier
    val finalHp = (player.currentHp + hpDelta * multiplier).coerceAtMost(stats.derived.hpMax)
    val finalMp = (player.currentMp + mpDelta * multiplier).coerceAtMost(stats.derived.mpMax)
    val consumed = multiplier != 1.0 && (hpDelta > 0.0 || mpDelta > 0.0)
    return if (consumed) {
        player.copy(currentHp = finalHp, currentMp = finalMp, nextHealMultiplier = 1.0)
    } else {
        player.copy(currentHp = finalHp, currentMp = finalMp)
    }
}

private fun applyRoomMultiplier(player: PlayerState, multiplier: Double, duration: Int): PlayerState {
    val isDebuff = multiplier < 1.0
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    return player.copy(roomEffectMultiplier = multiplier, roomEffectRooms = duration)
}

private fun applyRoomAttrBonus(player: PlayerState, bonus: Attributes, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomAttrRooms > 0) player.roomAttrBonus + bonus else bonus
    val rooms = max(player.roomAttrRooms, duration)
    return player.copy(roomAttrBonus = combined, roomAttrRooms = rooms)
}

private fun applyRoomDerivedMult(player: PlayerState, mult: DerivedStats, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomDerivedRooms > 0) player.roomDerivedMult + mult else mult
    val rooms = max(player.roomDerivedRooms, duration)
    return player.copy(roomDerivedMult = combined, roomDerivedRooms = rooms)
}

private fun applyRoomDerivedAdd(player: PlayerState, add: DerivedStats, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomDerivedRooms > 0) player.roomDerivedAdd + add else add
    val rooms = max(player.roomDerivedRooms, duration)
    return player.copy(roomDerivedAdd = combined, roomDerivedRooms = rooms)
}

private fun getAttr(attrs: Attributes, attr: Attribute): Int = when (attr) {
    Attribute.STR -> attrs.str
    Attribute.AGI -> attrs.agi
    Attribute.DEX -> attrs.dex
    Attribute.VIT -> attrs.vit
    Attribute.INT -> attrs.`int`
    Attribute.SPR -> attrs.spr
    Attribute.LUK -> attrs.luk
}

private fun addAttr(attrs: Attributes, attr: Attribute, delta: Int): Attributes = when (attr) {
    Attribute.STR -> attrs.copy(str = attrs.str + delta)
    Attribute.AGI -> attrs.copy(agi = attrs.agi + delta)
    Attribute.DEX -> attrs.copy(dex = attrs.dex + delta)
    Attribute.VIT -> attrs.copy(vit = attrs.vit + delta)
    Attribute.INT -> attrs.copy(`int` = attrs.`int` + delta)
    Attribute.SPR -> attrs.copy(spr = attrs.spr + delta)
    Attribute.LUK -> attrs.copy(luk = attrs.luk + delta)
}

private fun highestAttribute(attrs: Attributes): Pair<Attribute, Int> {
    val values = listOf(
        Attribute.STR to attrs.str,
        Attribute.AGI to attrs.agi,
        Attribute.DEX to attrs.dex,
        Attribute.VIT to attrs.vit,
        Attribute.INT to attrs.`int`,
        Attribute.SPR to attrs.spr,
        Attribute.LUK to attrs.luk
    )
    return values.maxBy { it.second }
}

private fun lowestAttribute(attrs: Attributes): Pair<Attribute, Int> {
    val values = listOf(
        Attribute.STR to attrs.str,
        Attribute.AGI to attrs.agi,
        Attribute.DEX to attrs.dex,
        Attribute.VIT to attrs.vit,
        Attribute.INT to attrs.`int`,
        Attribute.SPR to attrs.spr,
        Attribute.LUK to attrs.luk
    )
    return values.minBy { it.second }
}
