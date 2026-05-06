package rpg.application.production

import rpg.achievement.AchievementTracker
import rpg.achievement.AchievementCounterKeys
import rpg.application.support.OutOfCombatTimeService
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.CraftDiscipline
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState

class ProductionCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val durationService: ProductionActionDurationService = ProductionActionDurationService(engine),
    private val timeService: OutOfCombatTimeService = OutOfCombatTimeService(engine)
) {
    fun prepareCraft(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String,
        times: Int = 1
    ): ProductionPrepareResult {
        val resolution = durationService.resolveCraft(state, discipline, recipeId, requestedTimes = times)
            ?: return ProductionPrepareResult(
                ready = false,
                messages = listOf("Ingredientes ou requisitos insuficientes para esta receita.")
            )
        if (!canStoreTemplateOutput(state, resolution.recipe.outputItemId)) {
            return ProductionPrepareResult(
                ready = false,
                messages = listOf("Inventario cheio para esse tipo de item. Libere espaco ou venda itens antes de craftar.")
            )
        }
        return ProductionPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Craft | ${disciplineLabel(discipline)}",
                actionLabel = "Craftando ${resolution.recipe.name}...",
                skillLabel = resolution.skillLabel,
                skillLevel = resolution.skillLevel,
                durationSeconds = resolution.durationSeconds
            )
        )
    }

    fun prepareGather(
        state: GameState,
        type: GatheringType,
        nodeId: String
    ): ProductionPrepareResult {
        val resolution = durationService.resolveGather(state, type, nodeId)
            ?: return ProductionPrepareResult(
                ready = false,
                messages = listOf("Ponto de coleta indisponivel.")
            )
        if (!canStoreTemplateOutput(state, resolution.node.resourceItemId)) {
            return ProductionPrepareResult(
                ready = false,
                messages = listOf("Inventario cheio para esse recurso. Libere espaco ou venda itens antes de coletar.")
            )
        }
        return ProductionPrepareResult(
            ready = true,
            messages = emptyList(),
            timedActionView = ProductionTimedActionView(
                categoryLabel = "Producao | ${gatheringTypeLabel(type)}",
                actionLabel = "Coletando ${resolution.node.name}...",
                skillLabel = resolution.skillLabel,
                skillLevel = resolution.skillLevel,
                durationSeconds = resolution.durationSeconds
            )
        )
    }

    fun craft(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String,
        times: Int = 1
    ): ProductionMutationResult {
        val resolution = durationService.resolveCraft(state, discipline, recipeId, requestedTimes = times)
            ?: return ProductionMutationResult(state, listOf("Ingredientes ou requisitos insuficientes para esta receita."))
        val result = engine.craftingService.craft(
            state.player,
            state.itemInstances,
            resolution.recipe.id,
            resolution.times
        )
        if (!result.success) {
            return ProductionMutationResult(state, listOf(result.message))
        }

        var player = result.player
        var itemInstances = result.itemInstances
        val spentMinutes = (resolution.durationSeconds / 60.0).coerceAtLeast(0.01)
        val advance = timeService.advance(player, itemInstances, spentMinutes)
        player = advance.player
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
            if (result.recipe.discipline == CraftDiscipline.COOKING) {
                player = achievementTracker.onCustomCounterIncrement(
                    player,
                    AchievementCounterKeys.Cooking.NAMESPACE,
                    AchievementCounterKeys.Cooking.RECIPES_DONE,
                    amount = result.successfulCrafts.toLong().coerceAtLeast(1L)
                ).player
            }
            if (outputId in trackedEnchantResourceIds()) {
                player = achievementTracker.onCustomCounterIncrement(
                    player,
                    AchievementCounterKeys.EnchantResources.NAMESPACE,
                    AchievementCounterKeys.EnchantResources.ACQUIRED,
                    amount = outputQty.toLong().coerceAtLeast(1L)
                ).player
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
        lines += advance.messages
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
        val resolution = durationService.resolveGather(state, type, nodeId)
            ?: return ProductionMutationResult(state, listOf("Ponto de coleta indisponivel."))
        val result = engine.gatheringService.gather(state.player, state.itemInstances, resolution.node.id)
        if (!result.success || result.node == null || result.resourceItemId == null || result.quantity <= 0) {
            return ProductionMutationResult(state, listOf(result.message))
        }

        var player = result.player
        var itemInstances = result.itemInstances
        val spentMinutes = (resolution.durationSeconds / 60.0).coerceAtLeast(0.01)
        val advance = timeService.advance(player, itemInstances, spentMinutes)
        player = advance.player
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
        if (result.resourceItemId in trackedEnchantResourceIds()) {
            player = achievementTracker.onCustomCounterIncrement(
                player,
                AchievementCounterKeys.EnchantResources.NAMESPACE,
                AchievementCounterKeys.EnchantResources.ACQUIRED,
                amount = result.quantity.toLong().coerceAtLeast(1L)
            ).player
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
        lines += advance.messages
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

    private fun trackedEnchantResourceIds(): Set<String> = linkedSetOf<String>().apply {
        addAll(engine.enchantService.enhancementRuneItemIds())
        addAll(engine.enchantService.protectionRuneItemIds())
        addAll(engine.extractionService.enchantStoneTemplateIds())
        addAll(engine.extractionService.removalScrollItemIds())
        addAll(engine.extractionService.protectionScrollItemIds())
    }

    private fun disciplineLabel(discipline: CraftDiscipline): String = when (discipline) {
        CraftDiscipline.FORGE -> "Forja"
        CraftDiscipline.ALCHEMY -> "Alquimia"
        CraftDiscipline.COOKING -> "Culinaria"
    }

    private fun gatheringTypeLabel(type: GatheringType): String = when (type) {
        GatheringType.HERBALISM -> "Coleta de Ervas"
        GatheringType.MINING -> "Mineracao"
        GatheringType.WOODCUTTING -> "Corte de Madeira"
        GatheringType.FISHING -> "Pesca"
    }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun canStoreTemplateOutput(state: GameState, templateId: String): Boolean {
        val hasTemplateStack = state.player.inventory.any { itemId ->
            itemId == templateId || state.itemInstances[itemId]?.templateId == templateId
        }
        if (hasTemplateStack) return true
        val usedSlots = InventorySystem.slotsUsed(state.player, state.itemInstances, engine.itemRegistry)
        val limit = InventorySystem.inventoryLimit(state.player, state.itemInstances, engine.itemRegistry)
        return usedSlots < limit
    }
}
