package rpg.cli

import rpg.achievement.AchievementCounterKeys
import rpg.model.PlayerState

internal fun updateInfiniteHighestFloorCounter(player: PlayerState, floor: Int): PlayerState {
    if (floor <= 0) return player
    val key = AchievementCounterKeys.customCounterKey(
        AchievementCounterKeys.Dungeon.NAMESPACE,
        AchievementCounterKeys.Dungeon.INFINITE_HIGHEST_FLOOR
    )
    val current = player.lifetimeStats.customCounters[key] ?: 0L
    val next = maxOf(current, floor.toLong())
    if (next <= current) return player
    return player.copy(
        lifetimeStats = player.lifetimeStats.copy(
            customCounters = player.lifetimeStats.customCounters + (key to next)
        )
    )
}
