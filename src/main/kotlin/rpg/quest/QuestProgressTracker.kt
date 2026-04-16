package rpg.quest

import kotlin.math.max
import rpg.model.QuestObjectiveType

class QuestProgressTracker {
    fun onMonsterKilled(
        board: QuestBoardState,
        monsterId: String,
        monsterBaseType: String? = null,
        monsterTags: Set<String>,
        amount: Int = 1
    ): QuestBoardState {
        val normalizedTags = monsterTags.mapTo(mutableSetOf()) { it.lowercase() }
        val normalizedBaseType = monsterBaseType?.lowercase()
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || amount <= 0) return@updateBoard quest
            when (quest.objectiveType) {
                QuestObjectiveType.KILL_MONSTER -> {
                    val target = quest.generatedTargetId?.lowercase()
                    if (target != null && (target == monsterId.lowercase() || target == normalizedBaseType)) {
                        advanceProgress(quest, amount)
                    } else {
                        quest
                    }
                }
                QuestObjectiveType.KILL_TAG -> {
                    val target = quest.generatedTargetTag?.lowercase()
                    if (target != null && target in normalizedTags) {
                        advanceProgress(quest, amount)
                    } else {
                        quest
                    }
                }
                else -> quest
            }
        }
    }

    fun onItemCollected(board: QuestBoardState, itemId: String, quantity: Int = 1): QuestBoardState {
        if (quantity <= 0) return board
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || quest.objectiveType != QuestObjectiveType.COLLECT_ITEM) return@updateBoard quest
            if (quest.generatedTargetId != itemId) return@updateBoard quest
            advanceProgress(quest, quantity)
        }
    }

    fun onItemCrafted(
        board: QuestBoardState,
        outputItemId: String,
        disciplineTag: String? = null,
        quantity: Int = 1
    ): QuestBoardState {
        if (quantity <= 0) return board
        val normalizedDiscipline = disciplineTag?.lowercase()
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || quest.objectiveType != QuestObjectiveType.CRAFT_ITEM) return@updateBoard quest
            val targetId = quest.generatedTargetId
            val targetTag = quest.generatedTargetTag?.lowercase()
            if (targetId != null && targetId != outputItemId) return@updateBoard quest
            if (targetTag != null && targetTag != normalizedDiscipline) return@updateBoard quest
            advanceProgress(quest, quantity)
        }
    }

    fun onItemDelivered(board: QuestBoardState, _itemId: String, _quantity: Int = 1): QuestBoardState {
        // Entrega e validacao final acontecem no QuestRewardService.
        // Este hook existe para extensoes futuras (ex: quests de entrega parcial).
        return board
    }

    fun onGatheringCompleted(
        board: QuestBoardState,
        resourceItemId: String,
        gatheringTag: String? = null,
        quantity: Int = 1
    ): QuestBoardState {
        if (quantity <= 0) return board
        val normalizedType = gatheringTag?.lowercase()
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || quest.objectiveType != QuestObjectiveType.GATHER_RESOURCE) return@updateBoard quest
            val targetId = quest.generatedTargetId
            val targetTag = quest.generatedTargetTag?.lowercase()
            if (targetId != null && targetId != resourceItemId) return@updateBoard quest
            if (targetTag != null && targetTag != normalizedType) return@updateBoard quest
            advanceProgress(quest, quantity)
        }
    }

    fun onFloorReached(board: QuestBoardState, floor: Int): QuestBoardState {
        if (floor <= 0) return board
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || quest.objectiveType != QuestObjectiveType.REACH_FLOOR) return@updateBoard quest
            val progressed = max(quest.currentProgress, floor)
            finalizeProgress(quest, progressed)
        }
    }

    fun onRunCompleted(board: QuestBoardState, count: Int = 1): QuestBoardState {
        if (count <= 0) return board
        return updateBoard(board) { quest ->
            if (!quest.isTrackable() || quest.objectiveType != QuestObjectiveType.COMPLETE_RUN) return@updateBoard quest
            advanceProgress(quest, count)
        }
    }

    fun synchronizeCollectProgressFromInventory(
        board: QuestBoardState,
        inventory: List<String>,
        itemInstanceTemplateById: (String) -> String?
    ): QuestBoardState {
        return updateBoard(board) { quest ->
            if (!quest.isTrackable()) return@updateBoard quest
            if (quest.objectiveType != QuestObjectiveType.COLLECT_ITEM) return@updateBoard quest
            val targetId = quest.generatedTargetId ?: return@updateBoard quest
            val owned = inventory.count { itemId ->
                itemId == targetId || itemInstanceTemplateById(itemId) == targetId
            }
            finalizeProgress(quest, owned.coerceAtMost(quest.requiredAmount))
        }
    }

    private fun updateBoard(
        board: QuestBoardState,
        updater: (QuestInstance) -> QuestInstance
    ): QuestBoardState {
        return board.copy(
            dailyQuests = board.dailyQuests.map(updater),
            weeklyQuests = board.weeklyQuests.map(updater),
            monthlyQuests = board.monthlyQuests.map(updater),
            acceptedQuests = board.acceptedQuests.map(updater)
        )
    }

    private fun advanceProgress(quest: QuestInstance, amount: Int): QuestInstance {
        val next = (quest.currentProgress + amount).coerceAtMost(quest.requiredAmount)
        return finalizeProgress(quest, next)
    }

    private fun finalizeProgress(quest: QuestInstance, progress: Int): QuestInstance {
        val done = progress >= quest.requiredAmount
        return quest.copy(
            currentProgress = progress.coerceIn(0, quest.requiredAmount),
            status = if (done) QuestStatus.READY_TO_CLAIM else QuestStatus.ACTIVE
        )
    }

    private fun QuestInstance.isTrackable(): Boolean {
        return status == QuestStatus.ACTIVE || status == QuestStatus.READY_TO_CLAIM
    }
}
