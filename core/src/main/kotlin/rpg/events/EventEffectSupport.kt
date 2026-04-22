package rpg.events

import kotlin.math.max
import rpg.model.Attributes
import rpg.model.DerivedStats
import rpg.model.PlayerState

internal fun applyHealing(player: PlayerState, context: EventContext, hpDelta: Double, mpDelta: Double): PlayerState {
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

internal fun applyRoomMultiplier(player: PlayerState, multiplier: Double, duration: Int): PlayerState {
    val isDebuff = multiplier < 1.0
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    return player.copy(roomEffectMultiplier = multiplier, roomEffectRooms = duration)
}

internal fun applyRoomAttrBonus(player: PlayerState, bonus: Attributes, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomAttrRooms > 0) player.roomAttrBonus + bonus else bonus
    val rooms = max(player.roomAttrRooms, duration)
    return player.copy(roomAttrBonus = combined, roomAttrRooms = rooms)
}

internal fun applyRoomDerivedMult(player: PlayerState, mult: DerivedStats, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomDerivedRooms > 0) player.roomDerivedMult + mult else mult
    val rooms = max(player.roomDerivedRooms, duration)
    return player.copy(roomDerivedMult = combined, roomDerivedRooms = rooms)
}

internal fun applyRoomDerivedAdd(player: PlayerState, add: DerivedStats, duration: Int, isDebuff: Boolean): PlayerState {
    if (isDebuff && player.ignoreNextDebuff) {
        return player.copy(ignoreNextDebuff = false)
    }
    val combined = if (player.roomDerivedRooms > 0) player.roomDerivedAdd + add else add
    val rooms = max(player.roomDerivedRooms, duration)
    return player.copy(roomDerivedAdd = combined, roomDerivedRooms = rooms)
}

internal fun getAttr(attrs: Attributes, attr: Attribute): Int = when (attr) {
    Attribute.STR -> attrs.str
    Attribute.AGI -> attrs.agi
    Attribute.DEX -> attrs.dex
    Attribute.VIT -> attrs.vit
    Attribute.INT -> attrs.`int`
    Attribute.SPR -> attrs.spr
    Attribute.LUK -> attrs.luk
}

internal fun addAttr(attrs: Attributes, attr: Attribute, delta: Int): Attributes = when (attr) {
    Attribute.STR -> attrs.copy(str = attrs.str + delta)
    Attribute.AGI -> attrs.copy(agi = attrs.agi + delta)
    Attribute.DEX -> attrs.copy(dex = attrs.dex + delta)
    Attribute.VIT -> attrs.copy(vit = attrs.vit + delta)
    Attribute.INT -> attrs.copy(`int` = attrs.`int` + delta)
    Attribute.SPR -> attrs.copy(spr = attrs.spr + delta)
    Attribute.LUK -> attrs.copy(luk = attrs.luk + delta)
}

internal fun highestAttribute(attrs: Attributes): Pair<Attribute, Int> {
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

internal fun lowestAttribute(attrs: Attributes): Pair<Attribute, Int> {
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
