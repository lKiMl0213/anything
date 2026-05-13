package rpg.application.enchant

import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementCounterKeys
import rpg.application.production.ProductionTimedActionView
import rpg.application.support.OutOfCombatTimeService
import rpg.enchant.ExtractionRequest
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.SkillType

class ExtractionCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val timeService: OutOfCombatTimeService = OutOfCombatTimeService(engine)
) {
    fun prepare(
        state: GameState,
        itemId: String,
        useRemovalScroll: Boolean,
        useProtectionScroll: Boolean
    ): ExtractionPrepareResult {
        val preview = engine.extractionService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = ExtractionRequest(
                itemId = itemId,
                useRemovalScroll = useRemovalScroll,
                useProtectionScroll = useProtectionScroll
            )
        )
        if (!preview.available) {
            return ExtractionPrepareResult(
                ready = false,
                messages = preview.blockedReasons.ifEmpty { listOf("Não foi possível preparar a extração.") }
            )
        }
        return ExtractionPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Extração",
                actionLabel = "Extraindo de ${preview.itemName}...",
                skillLabel = "enchanting",
                skillLevel = engine.skillSystem.snapshot(state.player, SkillType.ENCHANTING).level,
                durationSeconds = preview.durationSeconds
            )
        )
    }

    fun extract(
        state: GameState,
        itemId: String,
        useRemovalScroll: Boolean,
        useProtectionScroll: Boolean
    ): ExtractionMutationResult {
        val result = engine.extractionService.extract(
            player = state.player,
            itemInstances = state.itemInstances,
            request = ExtractionRequest(
                itemId = itemId,
                useRemovalScroll = useRemovalScroll,
                useProtectionScroll = useProtectionScroll
            )
        )

        var updatedPlayer = result.player
        if (result.goldSpent > 0) {
            updatedPlayer = achievementTracker.onGoldSpent(updatedPlayer, result.goldSpent.toLong()).player
        }
        updatedPlayer = achievementTracker.onCustomCounterIncrement(
            updatedPlayer,
            AchievementCounterKeys.Extraction.NAMESPACE,
            AchievementCounterKeys.Extraction.TOTAL,
            amount = 1
        ).player
        val spentMinutes = (result.preview?.durationSeconds ?: 0.0) / 60.0
        val advance = timeService.advance(updatedPlayer, result.itemInstances, spentMinutes)
        updatedPlayer = advance.player
        var board = state.questBoard
        var itemInstances = result.itemInstances
        val stoneId = result.extractedStoneId
        if (stoneId != null) {
            val canonicalStone = itemInstances[stoneId]?.templateId ?: stoneId
            board = engine.questProgressTracker.onItemCollected(board, canonicalStone, quantity = 1)
            val classQuest = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = itemInstances,
                collectedItems = mapOf(canonicalStone to 1)
            )
            val classQuestGold = (classQuest.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuest.player
            itemInstances = classQuest.itemInstances
            if (classQuestGold > 0) {
                updatedPlayer = achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong()).player
            }
            updatedPlayer = achievementTracker.onCustomCounterIncrement(
                updatedPlayer,
                AchievementCounterKeys.Extraction.NAMESPACE,
                AchievementCounterKeys.Extraction.STONES_CREATED,
                amount = 1
            ).player
            if (canonicalStone in trackedEnchantResourceIds()) {
                updatedPlayer = achievementTracker.onCustomCounterIncrement(
                    updatedPlayer,
                    AchievementCounterKeys.EnchantResources.NAMESPACE,
                    AchievementCounterKeys.EnchantResources.ACQUIRED,
                    amount = 1
                ).player
            }
        }
        board = synchronizeQuestBoard(board, updatedPlayer, itemInstances)
        val updatedState = state.copy(
            player = updatedPlayer,
            itemInstances = itemInstances,
            questBoard = board,
            worldTimeMinutes = state.worldTimeMinutes + spentMinutes.coerceAtLeast(0.0),
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        val lines = mutableListOf(result.message)
        if (spentMinutes > 0.0) lines += "Tempo gasto na extração: ${format(spentMinutes)} min."
        lines += advance.messages
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return ExtractionMutationResult(
            state = updatedState,
            messages = lines,
            selectedItemId = if (result.itemDestroyed) null else itemId
        )
    }

    private fun synchronizeQuestBoard(
        board: rpg.quest.QuestBoardState,
        player: rpg.model.PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): rpg.quest.QuestBoardState {
        val synced = engine.questBoardEngine.synchronize(board, player)
        return engine.questProgressTracker.synchronizeCollectProgressFromInventory(
            board = synced,
            inventory = player.inventory,
            itemInstanceTemplateById = { id -> itemInstances[id]?.templateId }
        )
    }

    private fun trackedEnchantResourceIds(): Set<String> = linkedSetOf<String>().apply {
        addAll(engine.enchantService.enhancementRuneItemIds())
        addAll(engine.enchantService.protectionRuneItemIds())
        addAll(engine.extractionService.enchantStoneTemplateIds())
        addAll(engine.extractionService.removalScrollItemIds())
        addAll(engine.extractionService.protectionScrollItemIds())
    }

    private fun format(value: Double): String = "%.1f".format(value)
}



