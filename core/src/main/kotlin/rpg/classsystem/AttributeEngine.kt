package rpg.classsystem

import kotlin.random.Random
import rpg.model.Attributes
import rpg.model.ClassDef
import rpg.model.PlayerState
import rpg.model.RaceDef
import rpg.model.SpecializationDef
import rpg.model.SubclassDef

object AttributeEngine {
    private const val AUTO_POINTS_PER_LEVEL = 2

    fun applyAutoPoints(
        player: PlayerState,
        classDef: ClassDef,
        raceDef: RaceDef,
        subclassDef: SubclassDef?,
        specializationDef: SpecializationDef?,
        rng: Random
    ): PlayerState {
        val weights = classDef.autoPointWeights +
            classDef.growth +
            raceDef.growth +
            (subclassDef?.autoPointWeights ?: Attributes()) +
            (subclassDef?.growth ?: Attributes()) +
            (specializationDef?.autoPointWeights ?: Attributes()) +
            (specializationDef?.growth ?: Attributes())
        val distributed = distributePoints(weights, AUTO_POINTS_PER_LEVEL, rng)
        return player.copy(baseAttributes = player.baseAttributes + distributed)
    }

    private fun distributePoints(weights: Attributes, points: Int, rng: Random): Attributes {
        if (points <= 0) return Attributes()
        val entries = listOf(
            "STR" to weights.str,
            "AGI" to weights.agi,
            "DEX" to weights.dex,
            "VIT" to weights.vit,
            "INT" to weights.`int`,
            "SPR" to weights.spr,
            "LUK" to weights.luk
        ).filter { it.second > 0 }

        if (entries.isEmpty()) return Attributes()
        var result = Attributes()
        repeat(points) {
            val total = entries.sumOf { it.second }
            var roll = rng.nextInt(total)
            for ((code, weight) in entries) {
                roll -= weight
                if (roll < 0) {
                    result = addAttr(result, code, 1)
                    break
                }
            }
        }
        return result
    }

    private fun addAttr(attrs: Attributes, code: String, delta: Int): Attributes = when (code) {
        "STR" -> attrs.copy(str = attrs.str + delta)
        "AGI" -> attrs.copy(agi = attrs.agi + delta)
        "DEX" -> attrs.copy(dex = attrs.dex + delta)
        "VIT" -> attrs.copy(vit = attrs.vit + delta)
        "INT" -> attrs.copy(`int` = attrs.`int` + delta)
        "SPR" -> attrs.copy(spr = attrs.spr + delta)
        "LUK" -> attrs.copy(luk = attrs.luk + delta)
        else -> attrs
    }
}
