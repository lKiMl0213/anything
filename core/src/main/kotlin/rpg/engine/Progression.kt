package rpg.engine

import rpg.progression.AttributePointSystem
import rpg.progression.LevelTable
import rpg.progression.SkillUnlockSystem

object Progression {
    const val ATTR_POINTS_PER_LEVEL: Int = AttributePointSystem.POINTS_PER_LEVEL
    const val SKILL_POINTS_PER_LEVEL: Int = SkillUnlockSystem.POINTS_PER_LEVEL

    fun xpForNext(level: Int): Int = LevelTable.xpForNext(level)
}
