package rpg.application.production

import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.GameState
import rpg.model.GatheringType

class ProductionQueryService(
    private val engine: GameEngine,
    private val durationService: ProductionActionDurationService = ProductionActionDurationService(engine)
) {
    fun recipes(state: GameState, discipline: CraftDiscipline): List<ProductionRecipeView> {
        val recipes = engine.craftingService.availableRecipes(state.player.level, discipline)
        return recipes.map { recipe -> buildRecipeView(state, recipe, discipline, requestedBatchSize = null) }
    }

    fun recipe(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String,
        requestedBatchSize: Int?
    ): ProductionRecipeView? {
        val recipe = engine.craftingService.availableRecipes(state.player.level, discipline)
            .firstOrNull { it.id == recipeId }
            ?: return null
        return buildRecipeView(state, recipe, discipline, requestedBatchSize)
    }

    fun gatherNodes(state: GameState, type: GatheringType): List<ProductionGatherNodeView> {
        val player = state.player
        val nodes = engine.gatheringService.availableNodes(player.level, type)
        return nodes.map { node ->
            val skill = engine.gatheringService.nodeSkill(node)
            val snapshot = engine.skillSystem.snapshot(player, skill)
            val unlocked = snapshot.level >= node.minSkillLevel
            val duration = durationService.resolveGather(state, type, node.id)?.durationSeconds
                ?: engine.skillSystem.actionDurationSeconds(
                    baseSeconds = node.baseDurationSeconds,
                    skillLevel = snapshot.level
                )
            ProductionGatherNodeView(
                id = node.id,
                name = node.name,
                type = node.type,
                resourceLabel = itemName(node.resourceItemId),
                skillType = skill,
                skillLevel = snapshot.level,
                minSkillLevel = node.minSkillLevel,
                unlocked = unlocked,
                unlockReason = if (unlocked) null else unlockMessage(node.minSkillLevel, skill),
                durationSeconds = duration,
                available = unlocked
            )
        }
    }

    private fun itemName(itemId: String): String {
        return engine.itemRegistry.entry(itemId)?.name ?: itemId
    }

    private fun buildRecipeView(
        state: GameState,
        recipe: rpg.model.CraftRecipeDef,
        discipline: CraftDiscipline,
        requestedBatchSize: Int?
    ): ProductionRecipeView {
        val player = state.player
        val itemInstances = state.itemInstances
        val skill = engine.craftingService.recipeSkill(recipe)
        val skillSnapshot = engine.skillSystem.snapshot(player, skill)
        val ingredientLines = recipe.ingredients.map { ingredient ->
            val needed = ingredient.quantity.coerceAtLeast(1)
            val owned = player.inventory.count { id ->
                id == ingredient.itemId || itemInstances[id]?.templateId == ingredient.itemId
            }
            val ingredientName = itemName(ingredient.itemId)
            "$ingredientName: possui $owned / precisa $needed"
        }
        val maxCraftable = engine.craftingService.maxCraftable(player, itemInstances, recipe)
        val unlocked = skillSnapshot.level >= recipe.minSkillLevel
        val blockedReasons = mutableListOf<String>()
        if (!unlocked) {
            blockedReasons += "skill ${skill.name.lowercase()} ${skillSnapshot.level}/${recipe.minSkillLevel}"
        }
        if (maxCraftable <= 0) {
            blockedReasons += "ingredientes insuficientes"
        }
        val maxSelectableBatch = durationService.resolveCraft(
            state = state,
            discipline = discipline,
            recipeId = recipe.id,
            requestedTimes = Int.MAX_VALUE
        )?.times?.coerceAtLeast(1) ?: 1
        val durationResolution = durationService.resolveCraft(state, discipline, recipe.id, requestedBatchSize)
        val batchSize = durationResolution?.times?.coerceAtLeast(1) ?: 1
        val batchSeconds = durationResolution?.durationSeconds
            ?: engine.skillSystem.actionDurationSeconds(
                baseSeconds = recipe.baseDurationSeconds.coerceAtLeast(1.0),
                skillLevel = skillSnapshot.level
            )
        val perActionSeconds = (batchSeconds / batchSize.toDouble()).coerceAtLeast(0.5)
        return ProductionRecipeView(
            id = recipe.id,
            name = recipe.name,
            outputLabel = "${itemName(recipe.outputItemId)} x${recipe.outputQty}",
            discipline = recipe.discipline,
            unlocked = unlocked,
            unlockReason = if (unlocked) null else unlockMessage(recipe.minSkillLevel, skill),
            available = unlocked && maxCraftable > 0,
            maxCraftable = maxCraftable,
            maxSelectableBatch = maxSelectableBatch,
            batchSize = batchSize,
            estimatedPerActionSeconds = perActionSeconds,
            estimatedBatchSeconds = batchSeconds,
            blockedReasons = blockedReasons,
            ingredientLines = ingredientLines
        )
    }

    private fun unlockMessage(minSkillLevel: Int, skill: rpg.model.SkillType): String {
        return "Desbloqueado no nv $minSkillLevel de ${skillLabel(skill)}"
    }

    private fun skillLabel(skill: rpg.model.SkillType): String = when (skill) {
        rpg.model.SkillType.MINING -> "mineracao"
        rpg.model.SkillType.GATHERING -> "coleta"
        rpg.model.SkillType.WOODCUTTING -> "corte de madeira"
        rpg.model.SkillType.FISHING -> "pesca"
        rpg.model.SkillType.HUNTING -> "caca"
        rpg.model.SkillType.BLACKSMITH -> "forja"
        rpg.model.SkillType.ALCHEMIST -> "alquimia"
        rpg.model.SkillType.COOKING -> "culinaria"
        rpg.model.SkillType.ENCHANTING -> "encantamento"
    }
}
