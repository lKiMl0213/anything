package rpg.progression

import kotlin.math.pow

object LevelTable {
    private const val BASE_XP = 100.0
    private const val EXPONENT = 2.2

    fun xpForNext(level: Int): Int {
        val safeLevel = if (level < 1) 1 else level
        return (BASE_XP * safeLevel.toDouble().pow(EXPONENT)).toInt()
    }
}
