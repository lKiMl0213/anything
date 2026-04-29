// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import kotlin.math.min
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState
import rpg.model.CraftRecipeDef

internal class LegacyProductionFlow(
    private val engine: GameEngine,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val clearScreen: () -> Unit,
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
            clearScreen()
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
            clearScreen()
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
            val visibleRecipes = if (discipline == rpg.model.CraftDiscipline.FORGE) {
                chooseForgeRecipeSlice(recipes, player, itemInstances) ?: continue
            } else {
                recipes
            }
            if (visibleRecipes.isEmpty()) {
                println("Nenhuma receita encontrada para o filtro escolhido.")
                continue
            }

            println("\nReceitas de ${discipline.name.lowercase()}:")
            visibleRecipes.forEachIndexed { index, recipe ->
                val skill = engine.craftingService.recipeSkill(recipe)
                val skillSnapshot = engine.skillSystem.snapshot(player, skill)
                val ingredientLines = recipe.ingredients.map { ingredient ->
                    val needed = ingredient.quantity.coerceAtLeast(1)
                    val owned = player.inventory.count { id ->
                        id == ingredient.itemId || itemInstances[id]?.templateId == ingredient.itemId
                    }
                    val ingredientLabel = "${itemName(ingredient.itemId)}: possui $owned / precisa $needed"
                    if (owned < needed) {
                        uiColor(ingredientLabel, LegacyCliAnsiPalette.combatEnemy)
                    } else {
                        ingredientLabel
                    }
                }
                val ingredients = ingredientLines.joinToString(" | ")
                val output = "${itemName(recipe.outputItemId)} x${recipe.outputQty}"
                val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
                val blockedReasons = mutableListOf<String>()
                if (player.level < recipe.minPlayerLevel) {
                    blockedReasons += "lvl necessario ${recipe.minPlayerLevel}"
                }
                if (skillSnapshot.level < recipe.minSkillLevel) {
                    blockedReasons += "skill ${skill.name.lowercase()} ${skillSnapshot.level}/${recipe.minSkillLevel}"
                }
                if (maxCraftable <= 0) {
                    blockedReasons += "ingredientes insuficientes"
                }
                val availableLabel = if (maxCraftable > 0) {
                    "Disponivel: ${uiColor("${maxCraftable}x", ansiUiHp)}"
                } else {
                    uiColor("Indisponivel", LegacyCliAnsiPalette.combatEnemy)
                }
                val blockLabel = if (blockedReasons.isEmpty()) {
                    ""
                } else {
                    " [${blockedReasons.joinToString(" | ")}]"
                }
                println(
                    "${index + 1}. ${recipe.name} -> $output | ingredientes: $ingredients " +
                        "| $availableLabel$blockLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, visibleRecipes.size) ?: continue

            val recipe = visibleRecipes[choice - 1]
            val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
            if (maxCraftable <= 0) {
                println("Ingredientes ou requisitos insuficientes para ${recipe.name}.")
                continue
            }
            val craftBatchLimit = engine.permanentUpgradeService.craftBatchLimit(player)
            val maxAllowed = min(craftBatchLimit, maxCraftable)
            println("Quantidade maxima disponivel agora: ${uiColor("${maxCraftable}x", ansiUiHp)}")
            println("Limite de crafting por rodada: ${uiColor("$craftBatchLimit", ansiUiHp)}")
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
                clearScreen()
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

    private enum class ForgeClassFilter {
        SWORDMAN,
        MAGE,
        ARCHER,
        ALL
    }

    private fun chooseForgeRecipeSlice(
        recipes: List<CraftRecipeDef>,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): List<CraftRecipeDef>? {
        val groupedByMaterial = recipes.groupBy { forgeMaterialCategory(it) }
        val orderedCategories = buildList {
            add("geral")
            addAll(groupedByMaterial.keys.filter { it != "geral" }.sorted())
        }
        while (true) {
            println("\n=== Craft > Forja por Categoria ===")
            orderedCategories.forEachIndexed { index, category ->
                println("${index + 1}. ${forgeCategoryLabel(category)} (${groupedByMaterial[category]?.size ?: 0})")
            }
            println("x. Voltar")
            val catChoice = readMenuChoice("Escolha: ", 1, orderedCategories.size) ?: return null
            val categoryKey = orderedCategories[catChoice - 1]
            val categoryRecipes = groupedByMaterial[categoryKey].orEmpty()
            if (categoryRecipes.isEmpty()) {
                println("Nao ha receitas nesta categoria.")
                continue
            }
            if (categoryKey == "geral") {
                return categoryRecipes
            }
            val classFiltered = chooseForgeClassFilter(categoryRecipes, player, itemInstances) ?: continue
            if (classFiltered.isNotEmpty()) {
                return classFiltered
            }
            println("Nao ha receitas para esse filtro de classe.")
        }
    }

    private fun chooseForgeClassFilter(
        recipes: List<CraftRecipeDef>,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): List<CraftRecipeDef>? {
        while (true) {
            println("\nFiltro de classe:")
            println("1. Espadachim")
            println("2. Mago")
            println("3. Arqueiro")
            println("4. Todos")
            println("x. Voltar")
            val filter = when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> ForgeClassFilter.SWORDMAN
                2 -> ForgeClassFilter.MAGE
                3 -> ForgeClassFilter.ARCHER
                4 -> ForgeClassFilter.ALL
                null -> return null
                else -> continue
            }
            return recipes.filter { recipe ->
                val group = forgeRecipeClassGroup(recipe, itemInstances)
                when (filter) {
                    ForgeClassFilter.SWORDMAN -> group == ForgeClassFilter.SWORDMAN
                    ForgeClassFilter.MAGE -> group == ForgeClassFilter.MAGE
                    ForgeClassFilter.ARCHER -> group == ForgeClassFilter.ARCHER
                    ForgeClassFilter.ALL -> true
                }
            }
        }
    }

    private fun forgeMaterialCategory(recipe: CraftRecipeDef): String {
        val tokens = (recipe.id + " " + recipe.outputItemId + " " + recipe.name).lowercase()
        return when {
            "copper" in tokens || "cobre" in tokens -> "cobre"
            "iron" in tokens || "ferro" in tokens -> "ferro"
            "silver" in tokens || "prata" in tokens -> "prata"
            "gold" in tokens || "ouro" in tokens -> "ouro"
            "titanium" in tokens || "titanio" in tokens -> "titanio"
            "mithril" in tokens -> "mithril"
            "obsidian" in tokens || "obsidiana" in tokens -> "obsidiana"
            "adamantite" in tokens || "adamantita" in tokens -> "adamantita"
            "runic" in tokens || "runica" in tokens -> "runica"
            "reinforced" in tokens || "reforc" in tokens -> "liga reforcada"
            else -> "geral"
        }
    }

    private fun forgeCategoryLabel(key: String): String = when (key) {
        "geral" -> "Itens Gerais"
        "cobre" -> "Itens de Cobre"
        "ferro" -> "Itens de Ferro"
        "prata" -> "Itens de Prata"
        "ouro" -> "Itens de Ouro"
        "titanio" -> "Itens de Titanio"
        "mithril" -> "Itens de Mithril"
        "obsidiana" -> "Itens de Obsidiana"
        "adamantita" -> "Itens de Adamantita"
        "runica" -> "Itens Runicos"
        "liga reforcada" -> "Itens de Liga Reforcada"
        else -> "Itens ${key.replaceFirstChar { it.uppercase() }}"
    }

    private fun forgeRecipeClassGroup(
        recipe: CraftRecipeDef,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): ForgeClassFilter {
        val entry = engine.itemRegistry.entry(recipe.outputItemId)
        val tags = entry?.tags.orEmpty().map { it.trim().lowercase() }
        if (tags.any { it.contains("swordman") || it.contains("warrior") || it.contains("{swordman}") }) {
            return ForgeClassFilter.SWORDMAN
        }
        if (tags.any { it.contains("mage") || it.contains("{mage}") }) {
            return ForgeClassFilter.MAGE
        }
        if (tags.any { it.contains("archer") || it.contains("{archer}") }) {
            return ForgeClassFilter.ARCHER
        }

        val outputName = itemName(recipe.outputItemId).lowercase()
        if ("cajado" in outputName || "staff" in outputName || "scepter" in outputName || "rod" in outputName) {
            return ForgeClassFilter.MAGE
        }
        if ("arco" in outputName || "bow" in outputName || "flecha" in outputName || "arrow" in outputName || "quiver" in outputName || "aljava" in outputName) {
            return ForgeClassFilter.ARCHER
        }
        if ("espada" in outputName || "sword" in outputName || "machado" in outputName || "axe" in outputName || "escudo" in outputName || "shield" in outputName) {
            return ForgeClassFilter.SWORDMAN
        }
        return ForgeClassFilter.ALL
    }
}
