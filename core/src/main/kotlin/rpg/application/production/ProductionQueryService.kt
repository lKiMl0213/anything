package rpg.application.production

import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.GameState
import rpg.model.GatheringType

class ProductionQueryService(
    private val engine: GameEngine
) {
    fun recipes(state: GameState, discipline: CraftDiscipline): List<ProductionRecipeView> {
        val player = state.player
        val itemInstances = state.itemInstances
        val recipes = engine.craftingService.availableRecipes(player.level, discipline)
        return recipes.map { recipe ->
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
            ProductionRecipeView(
                id = recipe.id,
                name = recipe.name,
                outputLabel = "${itemName(recipe.outputItemId)} x${recipe.outputQty}",
                discipline = recipe.discipline,
                available = blockedReasons.isEmpty(),
                maxCraftable = maxCraftable,
                blockedReasons = blockedReasons,
                ingredientLines = ingredientLines
            )
        }
    }

    fun gatherNodes(state: GameState, type: GatheringType): List<ProductionGatherNodeView> {
        val player = state.player
        val nodes = engine.gatheringService.availableNodes(player.level, type)
        return nodes.map { node ->
            val skill = engine.gatheringService.nodeSkill(node)
            val snapshot = engine.skillSystem.snapshot(player, skill)
            val duration = engine.skillSystem.actionDurationSeconds(
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
                durationSeconds = duration,
                available = snapshot.level >= node.minSkillLevel
            )
        }
    }

    private fun itemName(itemId: String): String {
        return engine.itemRegistry.entry(itemId)?.name ?: itemId
    }
}
