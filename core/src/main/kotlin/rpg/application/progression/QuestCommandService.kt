package rpg.application.progression

import rpg.achievement.AchievementTracker
import rpg.classquest.ClassQuestService
import rpg.engine.GameEngine
import rpg.model.GameState

class QuestCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val classQuestService: ClassQuestService,
    private val support: QuestRulesSupport
) {
    fun acceptQuest(state: GameState, instanceId: String): ProgressionMutationResult {
        val result = engine.questBoardEngine.acceptQuest(state.questBoard, instanceId)
        return ProgressionMutationResult(
            state = state.copy(questBoard = result.board),
            messages = listOf(result.message)
        )
    }

    fun cancelQuest(state: GameState, instanceId: String): ProgressionMutationResult {
        val result = engine.questBoardEngine.cancelAcceptedQuest(state.questBoard, instanceId)
        return ProgressionMutationResult(
            state = state.copy(questBoard = result.board),
            messages = listOf(result.message)
        )
    }

    fun replaceQuest(state: GameState, section: QuestSection, instanceId: String): ProgressionMutationResult {
        val tier = support.questTier(section)
            ?: return ProgressionMutationResult(state, listOf("Essa categoria nao permite replace."))
        val result = engine.questBoardEngine.replaceQuest(
            board = state.questBoard,
            player = state.player,
            tier = tier,
            instanceId = instanceId
        )
        return ProgressionMutationResult(
            state = state.copy(questBoard = result.board),
            messages = listOf(result.message)
        )
    }

    fun claimQuest(state: GameState, instanceId: String): ProgressionMutationResult {
        val result = engine.questRewardService.claimQuest(
            player = state.player,
            itemInstances = state.itemInstances,
            board = state.questBoard,
            instanceId = instanceId
        )
        if (!result.success) {
            return ProgressionMutationResult(state, listOf(result.message))
        }

        var updatedPlayer = result.player
        var updatedItemInstances = result.itemInstances
        var updatedBoard = result.board
        val messages = mutableListOf(result.message)

        val goldEarned = (result.player.gold - state.player.gold).coerceAtLeast(0)
        if (goldEarned > 0) {
            val goldUpdate = achievementTracker.onGoldEarned(updatedPlayer, goldEarned.toLong())
            updatedPlayer = goldUpdate.player
            messages += support.achievementNotificationLines(goldUpdate.unlockedTiers)
        }

        val questUpdate = achievementTracker.onQuestCompleted(updatedPlayer)
        updatedPlayer = questUpdate.player
        messages += support.achievementNotificationLines(questUpdate.unlockedTiers)

        for ((itemId, qty) in result.grantedItems) {
            updatedBoard = engine.questProgressTracker.onItemCollected(
                board = updatedBoard,
                itemId = itemId,
                quantity = qty
            )
        }

        if (result.grantedItems.isNotEmpty()) {
            val beforeClassQuestPlayer = updatedPlayer
            val classQuestUpdate = classQuestService.onItemsCollected(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                collectedItems = result.grantedItems
            )
            updatedPlayer = classQuestUpdate.player
            updatedItemInstances = classQuestUpdate.itemInstances
            messages += classQuestUpdate.messages

            val classQuestGold = (classQuestUpdate.player.gold - beforeClassQuestPlayer.gold).coerceAtLeast(0)
            if (classQuestGold > 0) {
                val bonusGoldUpdate = achievementTracker.onGoldEarned(updatedPlayer, classQuestGold.toLong())
                updatedPlayer = bonusGoldUpdate.player
                messages += support.achievementNotificationLines(bonusGoldUpdate.unlockedTiers)
            }
        }

        return ProgressionMutationResult(
            state = state.copy(
                player = updatedPlayer,
                itemInstances = updatedItemInstances,
                questBoard = updatedBoard
            ),
            messages = messages
        )
    }

    fun chooseClassQuestPath(state: GameState, pathId: String): ProgressionMutationResult {
        val result = classQuestService.choosePath(
            player = state.player,
            itemInstances = state.itemInstances,
            pathId = pathId
        )
        return ProgressionMutationResult(
            state = state.copy(player = result.player, itemInstances = result.itemInstances),
            messages = result.messages
        )
    }

    fun cancelClassQuest(state: GameState): ProgressionMutationResult {
        val result = classQuestService.cancelCurrentQuest(
            player = state.player,
            itemInstances = state.itemInstances
        )
        return ProgressionMutationResult(
            state = state.copy(player = result.player, itemInstances = result.itemInstances),
            messages = result.messages
        )
    }
}
