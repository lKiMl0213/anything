package rpg.achievement

object MonsterTypeMasteryService {
    val trackedTypes: List<String> = listOf(
        "slime",
        "undead",
        "beast",
        "humanoid",
        "insect",
        "demon",
        "elemental",
        "plant",
        "construct",
        "dragon"
    )

    val achievementMilestones: List<Long> = listOf(10L, 25L, 50L, 100L, 250L, 500L, 1000L)

    fun normalizeType(raw: String?): String {
        return raw
            ?.trim()
            ?.lowercase()
            ?.ifBlank { "unknown" }
            ?: "unknown"
    }

    fun damageBonusPctForKills(kills: Long): Double {
        if (kills <= 0L) return 0.0
        val baseMilestonesReached = achievementMilestones.count { kills >= it }
        val tailStep = achievementMilestones.lastOrNull()?.coerceAtLeast(1L) ?: 1000L
        val extraMilestonesReached = if (kills > tailStep) {
            ((kills - tailStep) / tailStep).coerceAtLeast(0L).toInt()
        } else {
            0
        }
        val totalMilestonesReached = baseMilestonesReached + extraMilestonesReached
        return (totalMilestonesReached * 0.1).coerceAtMost(10.0)
    }

    fun damageBonusPctForType(
        killsByType: Map<String, Long>,
        monsterTypeId: String?
    ): Double {
        val key = normalizeType(monsterTypeId)
        val kills = killsByType[key] ?: 0L
        return damageBonusPctForKills(kills)
    }
}
