package rpg.application.hunting

import rpg.achievement.AchievementCounterKeys
import rpg.achievement.AchievementTracker
import rpg.application.production.ProductionTimedActionView
import rpg.application.support.OutOfCombatTimeService
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.PlayerState

class HuntingCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val timeService: OutOfCombatTimeService = OutOfCombatTimeService(engine)
) {
    fun prepare(state: GameState, spotId: String, durationSeconds: Int): HuntingPrepareResult {
        val preview = engine.huntingService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            spotId = spotId,
            selectedDurationSeconds = durationSeconds
        )
        if (!preview.available) {
            return HuntingPrepareResult(
                ready = false,
                messages = preview.blockedReasons.ifEmpty { listOf("Não foi possível iniciar a caça.") }
            )
        }
        val skillLevel = engine.skillSystem.snapshot(state.player, rpg.model.SkillType.HUNTING).level
        return HuntingPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Caça",
                actionLabel = "Caçando em ${preview.spotName} por ${formatDuration(preview.selectedDurationSeconds)}...",
                skillLabel = "hunting",
                skillLevel = skillLevel,
                durationSeconds = preview.durationSeconds
            )
        )
    }

    fun hunt(state: GameState, spotId: String, durationSeconds: Int): HuntingMutationResult {
        val result = engine.huntingService.hunt(
            player = state.player,
            itemInstances = state.itemInstances,
            spotId = spotId,
            selectedDurationSeconds = durationSeconds
        )
        if (!result.success) {
            return HuntingMutationResult(
                state = state,
                messages = listOf(result.message)
            )
        }

        var player = result.player
        var itemInstances = result.itemInstances
        var board = state.questBoard

        if (result.goldSpent > 0) {
            player = achievementTracker.onGoldSpent(player, result.goldSpent.toLong()).player
        }
        val totalUnits = result.collectedByItemId.values.sum().coerceAtLeast(0)
        if (totalUnits > 0) {
            player = achievementTracker.onCustomCounterIncrement(
                player,
                AchievementCounterKeys.Hunting.NAMESPACE,
                AchievementCounterKeys.Hunting.TOTAL_UNITS,
                amount = totalUnits.toLong()
            ).player
            player = achievementTracker.onCustomCounterIncrement(
                player,
                AchievementCounterKeys.EnchantResources.NAMESPACE,
                AchievementCounterKeys.EnchantResources.ACQUIRED,
                amount = trackedEnchantResourceQuantity(result.collectedByItemId).toLong()
            ).player
        }
        if (result.rareDropCount > 0) {
            player = achievementTracker.onCustomCounterIncrement(
                player,
                AchievementCounterKeys.Hunting.NAMESPACE,
                AchievementCounterKeys.Hunting.RARE_DROPS,
                amount = result.rareDropCount.toLong()
            ).player
        }

        for ((itemId, quantity) in result.collectedByItemId) {
            if (quantity <= 0) continue
            board = engine.questProgressTracker.onGatheringCompleted(
                board = board,
                resourceItemId = itemId,
                gatheringTag = "hunting",
                quantity = quantity
            )
            board = engine.questProgressTracker.onItemCollected(
                board = board,
                itemId = itemId,
                quantity = quantity
            )
        }

        val classQuestUpdate = engine.classQuestService.onItemsCollected(
            player = player,
            itemInstances = itemInstances,
            collectedItems = result.collectedByItemId
        )
        val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
        player = classQuestUpdate.player
        itemInstances = classQuestUpdate.itemInstances
        if (classQuestGold > 0) {
            player = achievementTracker.onGoldEarned(player, classQuestGold.toLong()).player
        }

        board = synchronizeQuestBoard(board, player, itemInstances)
        val spentMinutes = (result.selectedDurationSeconds.toDouble() / 60.0).coerceAtLeast(0.0)
        val advance = timeService.advance(player, itemInstances, spentMinutes)
        player = advance.player
        val nextState = state.copy(
            player = player,
            itemInstances = itemInstances,
            questBoard = board,
            worldTimeMinutes = state.worldTimeMinutes + spentMinutes,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        val lines = mutableListOf<String>()
        lines += result.message
        lines += "Tempo dedicado na caça: ${formatDuration(result.selectedDurationSeconds)}."
        lines += advance.messages
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return HuntingMutationResult(nextState, lines)
    }

    private fun synchronizeQuestBoard(
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): rpg.quest.QuestBoardState {
        val synced = engine.questBoardEngine.synchronize(board, player)
        return engine.questProgressTracker.synchronizeCollectProgressFromInventory(
            board = synced,
            inventory = player.inventory,
            itemInstanceTemplateById = { id -> itemInstances[id]?.templateId }
        )
    }

    private fun trackedEnchantResourceQuantity(collected: Map<String, Int>): Int {
        val tracked = linkedSetOf<String>().apply {
            addAll(engine.enchantService.enhancementRuneItemIds())
            addAll(engine.enchantService.protectionRuneItemIds())
            addAll(engine.extractionService.enchantStoneTemplateIds())
            addAll(engine.extractionService.removalScrollItemIds())
            addAll(engine.extractionService.protectionScrollItemIds())
        }
        return collected.entries
            .filter { (itemId, _) -> itemId in tracked }
            .sumOf { (_, qty) -> qty.coerceAtLeast(0) }
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun formatDuration(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val minutes = clamped / 60
        val seconds = clamped % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        } else {
            "${seconds}s"
        }
    }
}
