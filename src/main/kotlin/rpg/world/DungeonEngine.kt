package rpg.world

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.DungeonRun
import rpg.model.MapTierDef

enum class RunRoomType {
    MONSTER,
    REST,
    EVENT,
    BOSS
}

class DungeonEngine(private val repo: DataRepository, private val rng: Random) {
    private val bossWinInterval = 10
    private val eventChancePct = 5
    private val restChancePct = 10
    private val maxEventPerCycle = 2
    private val maxRestPerCycle = 2

    fun availableTiers(playerLevel: Int): List<MapTierDef> {
        return repo.mapTiers.values.sortedBy { it.minLevel }
            .filter { playerLevel >= it.minLevel }
    }

    fun tierById(id: String): MapTierDef = repo.mapTiers[id] ?: error("Tier nao encontrado: $id")

    fun startRun(tierId: String): DungeonRun {
        val tier = tierById(tierId)
        return DungeonRun(
            tierId = tier.id,
            biomeId = tier.biomeId,
            difficultyLevel = 1,
            depth = 0,
            roomsCleared = 0,
            victoriesInRun = 0,
            bossesDefeatedInRun = 0,
            mutationTier = 0,
            isActive = true
        )
    }

    fun isBossRoomDue(run: DungeonRun): Boolean {
        if (run.victoriesInRun <= 0) return false
        val milestonesReached = run.victoriesInRun / bossWinInterval
        return milestonesReached > run.bossesDefeatedInRun
    }

    fun nextRoomType(run: DungeonRun): RunRoomType {
        if (isBossRoomDue(run)) return RunRoomType.BOSS

        val winsInCycle = run.victoriesInRun % bossWinInterval
        val winsUntilBoss = bossWinInterval - winsInCycle
        val biome = run.biomeId?.let { repo.biomes[it] }
        val biomeEventBonus = biome?.eventRoomChanceBonusPct ?: 0
        var eventChance = if (run.eventRoomsInCycle >= maxEventPerCycle) 0 else eventChancePct + biomeEventBonus
        var restChance = if (run.restRoomsInCycle >= maxRestPerCycle) 0 else restChancePct
        eventChance = eventChance.coerceIn(0, 65)
        restChance = restChance.coerceIn(0, 35)

        if (winsUntilBoss <= 1 && run.eventRoomsInCycle == 0 && run.restRoomsInCycle == 0) {
            eventChance = minOf(eventChance, 4)
            restChance = minOf(restChance, 6)
        }

        val roll = rng.nextInt(100)
        return when {
            roll < eventChance -> RunRoomType.EVENT
            roll < eventChance + restChance -> RunRoomType.REST
            else -> RunRoomType.MONSTER
        }
    }

    fun advanceRun(
        run: DungeonRun,
        bossDefeated: Boolean,
        clearedRoomType: RunRoomType,
        victoryInRoom: Boolean = false
    ): DungeonRun {
        val newDifficulty = if (bossDefeated) run.difficultyLevel + 1 else run.difficultyLevel
        val newDepth = run.depth + 1
        val newMutationTier = if (newDepth > 0 && newDepth % 10 == 0) run.mutationTier + 1 else run.mutationTier
        val victoriesInRun = run.victoriesInRun + if (victoryInRoom) 1 else 0
        val bossesDefeatedInRun = run.bossesDefeatedInRun + if (bossDefeated && victoryInRoom) 1 else 0
        val isNewCycle = bossDefeated && victoryInRoom
        val nextEvent = if (isNewCycle) 0 else run.eventRoomsInCycle + if (clearedRoomType == RunRoomType.EVENT) 1 else 0
        val nextRest = if (isNewCycle) 0 else run.restRoomsInCycle + if (clearedRoomType == RunRoomType.REST) 1 else 0
        return run.copy(
            depth = newDepth,
            roomsCleared = run.roomsCleared + 1,
            difficultyLevel = newDifficulty,
            mutationTier = newMutationTier,
            victoriesInRun = victoriesInRun,
            bossesDefeatedInRun = bossesDefeatedInRun,
            eventRoomsInCycle = nextEvent,
            restRoomsInCycle = nextRest
        )
    }
}
