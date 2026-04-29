package rpg.classsystem

import kotlin.math.ceil
import rpg.model.RaceDef
import rpg.model.SkillType

object RaceBonusSupport {
    fun professionBonusPct(raceDef: RaceDef?, skillType: SkillType): Double {
        if (raceDef == null) return 0.0
        if (raceDef.professionBonusesPct.isEmpty()) return 0.0
        val normalized = raceDef.professionBonusesPct
            .mapKeys { (raw, _) -> raw.trim().lowercase() }
        val keys = professionKeys(skillType)
        return keys.maxOfOrNull { key -> normalized[key] ?: Double.NEGATIVE_INFINITY }
            ?.takeIf { it.isFinite() }
            ?.coerceIn(-50.0, 250.0)
            ?: 0.0
    }

    fun tradeBuyDiscountPct(raceDef: RaceDef?): Double {
        return (raceDef?.tradeBuyDiscountPct ?: 0.0).coerceIn(0.0, 25.0)
    }

    fun tradeSellBonusPct(raceDef: RaceDef?): Double {
        return (raceDef?.tradeSellBonusPct ?: 0.0).coerceIn(0.0, 25.0)
    }

    fun applyTradeSellBonus(baseSaleValue: Int, bonusPct: Double): Int {
        if (baseSaleValue <= 0) return 0
        val clamped = bonusPct.coerceIn(0.0, 25.0)
        if (clamped <= 0.0) return baseSaleValue
        return ceil(baseSaleValue * (1.0 + clamped / 100.0)).toInt().coerceAtLeast(1)
    }

    private fun professionKeys(skillType: SkillType): List<String> {
        return when (skillType) {
            SkillType.BLACKSMITH -> listOf("blacksmith", "forja", "craft", "crafting")
            SkillType.ALCHEMIST -> listOf("alchemy", "alquimia", "alchemist")
            SkillType.COOKING -> listOf("cooking", "culinaria", "cozinha")
            SkillType.MINING -> listOf("mining", "mineracao")
            SkillType.WOODCUTTING -> listOf("woodcutting", "lenhador", "madeira")
            SkillType.FISHING -> listOf("fishing", "pesca")
            SkillType.GATHERING -> listOf("gathering", "coleta", "herbalism", "herbalismo")
        } + skillType.name.lowercase()
    }
}
