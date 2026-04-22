package rpg.events

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import rpg.model.Attributes
import rpg.model.DerivedStats
import rpg.model.PlayerState

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
