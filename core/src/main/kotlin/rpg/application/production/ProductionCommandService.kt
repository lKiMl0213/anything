package rpg.application.production

import kotlin.math.max
import kotlin.math.min
import rpg.achievement.AchievementTracker
import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState

class ProductionCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker
) {
    fun craft(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String
    ): ProductionMutationResult {
        val recipe = engine.craftingService.availableRecipes(state.player.level, discipline)
            .firstOrNull { it.id == recipeId }
            ?: return ProductionMutationResult(state, listOf("Receita indisponivel para esta disciplina."))
        val maxCraftable = engine.craftingService.maxCraftable(state.player, state.itemInstances, recipe)
        if (maxCraftable <= 0) {
            return ProductionMutationResult(state, listOf("Ingredientes ou requisitos insuficientes para ${recipe.name}."))
        }

        val craftBatchLimit = engine.permanentUpgradeService.craftBatchLimit(state.player)
        val times = min(maxCraftable, max(1, craftBatchLimit))
        val skill = engine.craftingService.recipeSkill(recipe)
        val skillSnapshotBefore = engine.skillSystem.snapshot(state.player, skill)
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = recipe.baseDurationSeconds * times.coerceAtLeast(1),
            skillLevel = skillSnapshotBefore.level
        )
        val result = engine.craftingService.craft(state.player, state.itemInstances, recipe.id, times)
        if (!result.success) {
            return ProductionMutationResult(state, listOf(result.message))
        }

        var player = result.player
        var itemInstances = result.itemInstances
        val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
        player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
        var worldTimeMinutes = state.worldTimeMinutes + spentMinutes
        var board = state.questBoard

        val outputId = result.outputItemId
        val outputQty = result.outputQuantity
        if (outputId != null && outputQty > 0 && result.recipe != null) {
            val disciplineTag = result.skillType?.name?.lowercase() ?: result.recipe.discipline.name.lowercase()
            board = engine.questProgressTracker.onItemCrafted(
                board = board,
                outputItemId = outputId,
                disciplineTag = disciplineTag,
                quantity = outputQty
            )
            board = engine.questProgressTracker.onItemCollected(
                board = board,
                itemId = outputId,
                quantity = outputQty
            )
            val classQuestUpdate = engine.classQuestService.onItemsCollected(
                player = player,
                itemInstances = itemInstances,
                collectedItems = mapOf(outputId to outputQty)
            )
            val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
            player = classQuestUpdate.player
            if (classQuestGold > 0) {
                player = achievementTracker.onGoldEarned(player, classQuestGold.toLong()).player
            }
            itemInstances = classQuestUpdate.itemInstances
        }
        board = synchronizeQuestBoard(board, player, itemInstances)

        val updatedState = state.copy(
            player = player,
            itemInstances = itemInstances,
            questBoard = board,
            worldTimeMinutes = worldTimeMinutes,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        val lines = mutableListOf<String>()
        lines += result.message
        lines += "Tempo gasto em craft: ${format(spentMinutes)} min."
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return ProductionMutationResult(updatedState, lines)
    }

    fun gather(
        state: GameState,
        type: GatheringType,
        nodeId: String
    ): ProductionMutationResult {
        val node = engine.gatheringService.availableNodes(state.player.level, type).firstOrNull { it.id == nodeId }
            ?: return ProductionMutationResult(state, listOf("Ponto de coleta indisponivel."))
        val skill = engine.gatheringService.nodeSkill(node)
        val snapshotBefore = engine.skillSystem.snapshot(state.player, skill)
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = node.baseDurationSeconds,
            skillLevel = snapshotBefore.level
        )
        val result = engine.gatheringService.gather(state.player, state.itemInstances, node.id)
        if (!result.success || result.node == null || result.resourceItemId == null || result.quantity <= 0) {
            return ProductionMutationResult(state, listOf(result.message))
        }

        var player = result.player
        var itemInstances = result.itemInstances
        val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
        player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
        var worldTimeMinutes = state.worldTimeMinutes + spentMinutes
        var board = state.questBoard

        board = engine.questProgressTracker.onGatheringCompleted(
            board = board,
            resourceItemId = result.resourceItemId,
            gatheringTag = result.skillType?.name?.lowercase() ?: result.node.type.name.lowercase(),
            quantity = result.quantity
        )
        board = engine.questProgressTracker.onItemCollected(
            board = board,
            itemId = result.resourceItemId,
            quantity = result.quantity
        )

        val classQuestUpdate = engine.classQuestService.onItemsCollected(
            player = player,
            itemInstances = itemInstances,
            collectedItems = mapOf(result.resourceItemId to result.quantity)
        )
        val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
        player = classQuestUpdate.player
        if (classQuestGold > 0) {
            player = achievementTracker.onGoldEarned(player, classQuestGold.toLong()).player
        }
        itemInstances = classQuestUpdate.itemInstances
        board = synchronizeQuestBoard(board, player, itemInstances)

        val updatedState = state.copy(
            player = player,
            itemInstances = itemInstances,
            questBoard = board,
            worldTimeMinutes = worldTimeMinutes,
            lastClockSyncEpochMs = System.currentTimeMillis()
        )
        val lines = mutableListOf<String>()
        lines += result.message
        lines += "Tempo gasto na coleta: ${format(spentMinutes)} min."
        result.skillSnapshot?.let { snapshot ->
            lines += "Skill ${snapshot.skill.name.lowercase()}: +${format(result.gainedXp)} XP (lvl ${snapshot.level})"
        }
        return ProductionMutationResult(updatedState, lines)
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

    private fun advanceOutOfCombatTime(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        minutes: Double
    ): PlayerState {
        if (minutes <= 0.0) return player
        val stats = engine.computePlayerStats(player, itemInstances)
        val newHp = (player.currentHp + stats.derived.hpRegen * minutes).coerceAtMost(stats.derived.hpMax)
        val newMp = (player.currentMp + stats.derived.mpRegen * minutes).coerceAtMost(stats.derived.mpMax)
        var updated = player.copy(currentHp = newHp, currentMp = newMp)

        if (updated.deathDebuffMinutes > 0.0) {
            val remaining = (updated.deathDebuffMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remaining <= 0.0) {
                updated.copy(
                    deathDebuffMinutes = 0.0,
                    deathDebuffStacks = 0,
                    deathXpPenaltyMinutes = 0.0,
                    deathXpPenaltyPct = 0.0
                )
            } else {
                updated.copy(
                    deathDebuffMinutes = remaining,
                    deathXpPenaltyMinutes = remaining
                )
            }
        }
        if (updated.deathDebuffMinutes <= 0.0 && updated.deathXpPenaltyMinutes > 0.0) {
            val remainingXpPenalty = (updated.deathXpPenaltyMinutes - minutes).coerceAtLeast(0.0)
            updated = if (remainingXpPenalty <= 0.0) {
                updated.copy(deathXpPenaltyMinutes = 0.0, deathXpPenaltyPct = 0.0)
            } else {
                updated.copy(deathXpPenaltyMinutes = remainingXpPenalty)
            }
        }
        return updated
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
