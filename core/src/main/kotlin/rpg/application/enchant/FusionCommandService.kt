package rpg.application.enchant

import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementCounterKeys
import rpg.application.production.ProductionTimedActionView
import rpg.application.support.OutOfCombatTimeService
import rpg.enchant.FusionRequest
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.SkillType

class FusionCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val timeService: OutOfCombatTimeService = OutOfCombatTimeService(engine)
) {
    fun prepare(state: GameState, slot1ItemId: String, slot2ItemId: String): FusionPrepareResult {
        val preview = engine.fusionService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = FusionRequest(slot1ItemId, slot2ItemId)
        )
        if (!preview.available || preview.mode == null) {
            return FusionPrepareResult(
                ready = false,
                messages = preview.blockedReasons.ifEmpty { listOf("Nao foi possivel preparar a fusao.") }
            )
        }
        return FusionPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Fusao",
                actionLabel = "Fundindo ${preview.slot1Label} e ${preview.slot2Label}...",
                skillLabel = "enchanting",
                skillLevel = engine.skillSystem.snapshot(state.player, SkillType.ENCHANTING).level,
                durationSeconds = preview.durationSeconds
            )
        )
    }

    fun fuse(state: GameState, slot1ItemId: String, slot2ItemId: String): FusionMutationResult {
        val result = engine.fusionService.fuse(
            player = state.player,
            itemInstances = state.itemInstances,
            request = FusionRequest(slot1ItemId, slot2ItemId)
        )

        var updatedPlayer = result.player
        if (result.goldSpent > 0) {
            updatedPlayer = achievementTracker.onGoldSpent(updatedPlayer, result.goldSpent.toLong()).player
        }
        updatedPlayer = achievementTracker.onCustomCounterIncrement(
            updatedPlayer,
            AchievementCounterKeys.Fusion.NAMESPACE,
            AchievementCounterKeys.Fusion.TOTAL,
            amount = 1
        ).player
        val spentMinutes = (result.preview?.durationSeconds ?: 0.0) / 60.0
        val advance = timeService.advance(updatedPlayer, result.itemInstances, spentMinutes)
        updatedPlayer = advance.player
        var board = state.questBoard
        var itemInstances = result.itemInstances
        val outputId = result.outputItemId
        if (outputId != null) {
            val canonicalOutput = itemInstances[outputId]?.templateId ?: outputId
            board = engine.questProgressTracker.onItemCollected(board, canonicalOutput, quantity = 1)
            val classQuest = engine.classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = itemInstances,
                collectedItems = mapOf(canonicalOutput to 1)
            )
            val classQuestGold = (classQuest.player.gold - updatedPlayer.gold).coerceAtLeast(0)
            updatedPlayer = classQuest.player
            itemInstances = classQuest.itemInstances
            if (classQuestGold > 0) {
                updatedPlayer = achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong()).player
            }
            if (canonicalOutput in trackedEnchantResourceIds()) {
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
        if (spentMinutes > 0.0) lines += "Tempo gasto na fusao: ${format(spentMinutes)} min."
        lines += advance.messages
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return FusionMutationResult(
            state = updatedState,
            messages = lines,
            outputItemId = result.outputItemId
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
