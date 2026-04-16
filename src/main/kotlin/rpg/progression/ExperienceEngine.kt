package rpg.progression

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import rpg.model.PlayerState

object ExperienceEngine {
    fun xpForNext(level: Int): Int = LevelTable.xpForNext(level)

    fun levelDiffModifier(playerLevel: Int, monsterLevel: Int): Double {
        val diff = monsterLevel - playerLevel
        return when {
            diff <= -4 -> 0.3
            diff < 0 -> (1.0 + diff * 0.15).coerceAtLeast(0.3)
            diff == 0 -> 1.0
            else -> min(1.5, 1.0 + diff * 0.1)
        }
    }

    fun computeXpGain(
        baseXp: Int,
        playerLevel: Int,
        monsterLevel: Int,
        xpBonusPct: Double
    ): Int {
        if (baseXp <= 0) return 0
        val levelMod = levelDiffModifier(playerLevel, monsterLevel)
        val bonusMult = 1.0 + (xpBonusPct / 100.0)
        val raw = baseXp.toDouble() * levelMod * bonusMult
        return max(1, raw.roundToInt())
    }

    fun applyXp(player: PlayerState, xpGain: Int): PlayerState {
        var updated = player.copy(xp = player.xp + xpGain)
        while (updated.xp >= xpForNext(updated.level)) {
            val nextXp = xpForNext(updated.level)
            updated = updated.copy(
                level = updated.level + 1,
                xp = updated.xp - nextXp,
                unspentAttrPoints = updated.unspentAttrPoints + AttributePointSystem.POINTS_PER_LEVEL,
                unspentSkillPoints = updated.unspentSkillPoints + SkillUnlockSystem.POINTS_PER_LEVEL
            )
        }
        return updated
    }
}
