package rpg.cli

import kotlin.math.min
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState

internal class LegacyProductionFlow(
    private val engine: GameEngine,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val runProgressBar: (label: String, durationSeconds: Double) -> Unit,
    private val format: (Double) -> String,
    private val itemName: (itemId: String) -> String,
    private val uiColor: (text: String, colorCode: String) -> String,
    private val ansiUiHp: String,
    private val onGoldEarned: (player: PlayerState, amount: Long) -> PlayerState,
    private val advanceOutOfCombatTime: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        minutes: Double
    ) -> PlayerState,
    private val synchronizeQuestBoard: (
        board: rpg.quest.QuestBoardState,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.quest.QuestBoardState,
    private val autoSave: (GameState) -> Unit
) {
    fun openProductionMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            println("\n=== Producao ===")
            println("1. Craft")
            println("2. Coleta de ervas")
            println("3. Mineracao")
            println("4. Cortar madeira")
            println("5. Pesca")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 5)) {
                1 -> updated = openCrafting(updated)
                2 -> updated = openGathering(updated, forcedType = GatheringType.HERBALISM)
                3 -> updated = openGathering(updated, forcedType = GatheringType.MINING)
                4 -> updated = openGathering(updated, forcedType = GatheringType.WOODCUTTING)
                5 -> updated = openGathering(updated, forcedType = GatheringType.FISHING)
                null -> return updated
            }
        }
    }

    private fun openCrafting(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)
        var worldTimeMinutes = state.worldTimeMinutes
        var lastClockSync = state.lastClockSyncEpochMs

        while (true) {
            println("\n=== Craft ===")
            println("1. Forja")
            println("2. Alquimia")
            println("3. Culinaria")
            println("x. Voltar")
            val discipline = when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> rpg.model.CraftDiscipline.FORGE
                2 -> rpg.model.CraftDiscipline.ALCHEMY
                3 -> rpg.model.CraftDiscipline.COOKING
                null -> {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                }

                else -> continue
            }

            val recipes = engine.craftingService.availableRecipes(player.level, discipline)
            if (recipes.isEmpty()) {
                println("Nenhuma receita disponivel para ${discipline.name.lowercase()}.")
                continue
            }

            println("\nReceitas de ${discipline.name.lowercase()}:")
            recipes.forEachIndexed { index, recipe ->
                val skill = engine.craftingService.recipeSkill(recipe)
                val skillSnapshot = engine.skillSystem.snapshot(player, skill)
                val ingredients = recipe.ingredients.joinToString(", ") {
                    "${itemName(it.itemId)} x${it.quantity}"
                }
                val output = "${itemName(recipe.outputItemId)} x${recipe.outputQty}"
                val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
                val blockedReasons = mutableListOf<String>()
                if (player.level < recipe.minPlayerLevel) {
                    blockedReasons += "lvl necessario ${recipe.minPlayerLevel}"
                }
                if (skillSnapshot.level < recipe.minSkillLevel) {
                    blockedReasons += "skill ${skill.name.lowercase()} ${skillSnapshot.level}/${recipe.minSkillLevel}"
                }
                val availableLabel = uiColor("(${maxCraftable}x disponivel)", ansiUiHp)
                val blockLabel = if (blockedReasons.isEmpty()) {
                    ""
                } else {
                    " [${blockedReasons.joinToString(" | ")}]"
                }
                println(
                    "${index + 1}. ${recipe.name} -> $output | ingredientes: $ingredients " +
                        "$availableLabel$blockLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, recipes.size) ?: continue

            val recipe = recipes[choice - 1]
            val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
            if (maxCraftable <= 0) {
                println("Ingredientes ou requisitos insuficientes para ${recipe.name}.")
                continue
            }
            val maxAllowed = min(20, maxCraftable)
            println("Quantidade maxima disponivel agora: ${uiColor("${maxCraftable}x", ansiUiHp)}")
            val times = readInt("Quantidade de crafts (1-$maxAllowed): ", 1, maxAllowed)
            val skill = engine.craftingService.recipeSkill(recipe)
            val skillSnapshotBefore = engine.skillSystem.snapshot(player, skill)
            val duration = engine.skillSystem.actionDurationSeconds(
                baseSeconds = recipe.baseDurationSeconds * times.coerceAtLeast(1),
                skillLevel = skillSnapshotBefore.level
            )
            runProgressBar("Craftando ${recipe.name}", duration)
            val result = engine.craftingService.craft(player, itemInstances, recipe.id, times)
            println(result.message)
            if (!result.success) continue

            player = result.player
            itemInstances = result.itemInstances
            val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
            player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
            worldTimeMinutes += spentMinutes
            lastClockSync = System.currentTimeMillis()
            println("Tempo gasto em craft: ${format(spentMinutes)} min.")
            if (result.skillSnapshot != null) {
                println(
                    "Skill ${result.skillSnapshot.skill.name.lowercase()}: +" +
                        "${format(result.gainedXp)} XP (lvl ${result.skillSnapshot.level})"
                )
            }
            val outputId = result.outputItemId
            val outputQty = result.outputQuantity
            if (outputId != null && outputQty > 0 && result.recipe != null) {
                val disciplineTag = result.skillType?.name?.lowercase()
                    ?: result.recipe.discipline.name.lowercase()
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
                classQuestUpdate.messages.forEach { println(it) }
                val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
                player = classQuestUpdate.player
                if (classQuestGold > 0) {
                    player = onGoldEarned(player, classQuestGold.toLong())
                }
                itemInstances = classQuestUpdate.itemInstances
            }
            board = synchronizeQuestBoard(board, player, itemInstances)
            autoSave(
                state.copy(
                    player = player,
                    itemInstances = itemInstances,
                    questBoard = board,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
            )
        }
    }

    private fun openGathering(
        state: GameState,
        forcedType: GatheringType? = null
    ): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var board = synchronizeQuestBoard(state.questBoard, player, itemInstances)
        var worldTimeMinutes = state.worldTimeMinutes
        var lastClockSync = state.lastClockSyncEpochMs

        while (true) {
            val type = if (forcedType != null) {
                forcedType
            } else {
                println("\n=== Gathering ===")
                println("1. Mineracao")
                println("2. Coleta de Ervas")
                println("3. Corte de Madeira")
                println("4. Pesca")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 4)) {
                    1 -> GatheringType.MINING
                    2 -> GatheringType.HERBALISM
                    3 -> GatheringType.WOODCUTTING
                    4 -> GatheringType.FISHING
                    null -> {
                        val updatedState = state.copy(
                            player = player,
                            itemInstances = itemInstances,
                            questBoard = board,
                            worldTimeMinutes = worldTimeMinutes,
                            lastClockSyncEpochMs = lastClockSync
                        )
                        autoSave(updatedState)
                        return updatedState
                    }

                    else -> continue
                }
            }

            val nodes = engine.gatheringService.availableNodes(player.level, type)
            if (nodes.isEmpty()) {
                println("Nenhum ponto de coleta disponivel nesta categoria.")
                if (forcedType != null) {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                } else {
                    continue
                }
            }

            println("\n=== ${gatheringTypeLabel(type)} ===")
            println("\nPontos disponiveis:")
            nodes.forEachIndexed { index, node ->
                val skill = engine.gatheringService.nodeSkill(node)
                val skillSnapshot = engine.skillSystem.snapshot(player, skill)
                val estSeconds = engine.skillSystem.actionDurationSeconds(
                    baseSeconds = node.baseDurationSeconds,
                    skillLevel = skillSnapshot.level
                )
                val blocked = skillSnapshot.level < node.minSkillLevel
                val blockLabel = if (!blocked) {
                    ""
                } else {
                    " [skill requerida ${node.minSkillLevel}]"
                }
                println(
                    "${index + 1}. ${node.name} -> ${itemName(node.resourceItemId)} " +
                        "(skill ${skill.name.lowercase()}: ${skillSnapshot.level}, " +
                        "tempo estimado: ${format(estSeconds)}s por coleta)$blockLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, nodes.size)
            if (choice == null) {
                if (forcedType != null) {
                    val updatedState = state.copy(
                        player = player,
                        itemInstances = itemInstances,
                        questBoard = board,
                        worldTimeMinutes = worldTimeMinutes,
                        lastClockSyncEpochMs = lastClockSync
                    )
                    autoSave(updatedState)
                    return updatedState
                }
                continue
            }

            val node = nodes[choice - 1]
            val skill = engine.gatheringService.nodeSkill(node)
            val skillSnapshotBefore = engine.skillSystem.snapshot(player, skill)
            val duration = engine.skillSystem.actionDurationSeconds(
                baseSeconds = node.baseDurationSeconds,
                skillLevel = skillSnapshotBefore.level
            )
            runProgressBar("Coletando ${node.name}", duration)
            val result = engine.gatheringService.gather(player, itemInstances, node.id)
            println(result.message)
            if (!result.success || result.node == null || result.resourceItemId == null || result.quantity <= 0) {
                continue
            }

            player = result.player
            itemInstances = result.itemInstances
            val spentMinutes = (duration / 60.0).coerceAtLeast(0.01)
            player = advanceOutOfCombatTime(player, itemInstances, spentMinutes)
            worldTimeMinutes += spentMinutes
            lastClockSync = System.currentTimeMillis()
            println("Tempo gasto na coleta: ${format(spentMinutes)} min.")
            if (result.skillSnapshot != null) {
                println(
                    "Skill ${result.skillSnapshot.skill.name.lowercase()}: +" +
                        "${format(result.gainedXp)} XP (lvl ${result.skillSnapshot.level})"
                )
            }
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
            classQuestUpdate.messages.forEach { println(it) }
            val classQuestGold = (classQuestUpdate.player.gold - player.gold).coerceAtLeast(0)
            player = classQuestUpdate.player
            if (classQuestGold > 0) {
                player = onGoldEarned(player, classQuestGold.toLong())
            }
            itemInstances = classQuestUpdate.itemInstances
            board = synchronizeQuestBoard(board, player, itemInstances)
            autoSave(
                state.copy(
                    player = player,
                    itemInstances = itemInstances,
                    questBoard = board,
                    worldTimeMinutes = worldTimeMinutes,
                    lastClockSyncEpochMs = lastClockSync
                )
            )
        }
    }

    private fun gatheringTypeLabel(type: GatheringType): String = when (type) {
        GatheringType.HERBALISM -> "Coleta de Ervas"
        GatheringType.MINING -> "Mineracao"
        GatheringType.WOODCUTTING -> "Cortar Madeira"
        GatheringType.FISHING -> "Pesca"
    }
}
