package rpg.application.production

import kotlin.math.max
import kotlin.math.min
import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.GameState
import rpg.model.GatherNodeDef
import rpg.model.GatheringType
import rpg.premium.PremiumSupport

class ProductionActionDurationService(
    private val engine: GameEngine
) {
    fun resolveCraft(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String,
        requestedTimes: Int? = null
    ): CraftDurationResolution? {
        val recipe = engine.craftingService.availableRecipes(state.player.level, discipline)
            .firstOrNull { it.id == recipeId }
            ?: return null
        val maxCraftable = engine.craftingService.maxCraftable(state.player, state.itemInstances, recipe)
        return resolveCraftFromRecipe(
            state = state,
            discipline = discipline,
            recipe = recipe,
            maxCraftable = maxCraftable,
            requestedTimes = requestedTimes
        )
    }

    internal fun resolveCraftFromRecipe(
        state: GameState,
        discipline: CraftDiscipline,
        recipe: CraftRecipeDef,
        maxCraftable: Int,
        requestedTimes: Int? = null,
        skillLevelOverride: Int? = null
    ): CraftDurationResolution? {
        if (maxCraftable <= 0) return null
        val craftBatchLimit = engine.permanentUpgradeService.craftBatchLimit(state.player)
        val maxBatch = min(maxCraftable, max(1, craftBatchLimit))
        val times = requestedTimes?.coerceAtLeast(1)?.coerceAtMost(maxBatch) ?: maxBatch
        val skill = engine.craftingService.recipeSkill(recipe)
        val skillLevel = skillLevelOverride ?: engine.skillSystem.snapshot(state.player, skill).level
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = recipe.baseDurationSeconds * times.coerceAtLeast(1) * craftDurationMultiplier(discipline),
            skillLevel = skillLevel
        ) * taskDurationMultiplier(state.player, discipline.name.lowercase()) *
            PremiumSupport.productionDurationMultiplier(state.player)
        return CraftDurationResolution(
            recipe = recipe,
            times = times,
            skillLabel = skill.name.lowercase(),
            skillLevel = skillLevel,
            durationSeconds = duration
        )
    }

    fun resolveGather(
        state: GameState,
        type: GatheringType,
        nodeId: String
    ): GatherDurationResolution? {
        val node = engine.gatheringService.enabledNodeCatalog(type)
            .firstOrNull { it.id == nodeId }
            ?: return null
        return resolveGatherFromNode(state, type, node)
    }

    internal fun resolveGatherFromNode(
        state: GameState,
        type: GatheringType,
        node: GatherNodeDef,
        skillLevelOverride: Int? = null
    ): GatherDurationResolution {
        val skill = engine.gatheringService.nodeSkill(node)
        val skillLevel = skillLevelOverride ?: engine.skillSystem.snapshot(state.player, skill).level
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = node.baseDurationSeconds,
            skillLevel = skillLevel
        ) * taskDurationMultiplier(state.player, taskIdForGathering(type)) *
            PremiumSupport.productionDurationMultiplier(state.player)
        return GatherDurationResolution(
            node = node,
            skillLabel = skill.name.lowercase(),
            skillLevel = skillLevel,
            durationSeconds = duration
        )
    }

    private fun taskDurationMultiplier(player: rpg.model.PlayerState, taskId: String): Double {
        val efficiencyPct = engine.cookingBuffService.taskEfficiencyPct(player, taskId)
        return (1.0 - efficiencyPct.coerceIn(0.0, 80.0) / 100.0).coerceIn(0.20, 1.0)
    }

    private fun craftDurationMultiplier(discipline: CraftDiscipline): Double = when (discipline) {
        CraftDiscipline.FORGE -> 0.95
        CraftDiscipline.ALCHEMY -> 1.0
        CraftDiscipline.COOKING -> 0.82
    }

    private fun taskIdForGathering(type: GatheringType): String = when (type) {
        GatheringType.HERBALISM -> "herbalism"
        GatheringType.MINING -> "mining"
        GatheringType.WOODCUTTING -> "woodcutting"
        GatheringType.FISHING -> "fishing"
    }
}

data class CraftDurationResolution(
    val recipe: CraftRecipeDef,
    val times: Int,
    val skillLabel: String,
    val skillLevel: Int,
    val durationSeconds: Double
)

data class GatherDurationResolution(
    val node: GatherNodeDef,
    val skillLabel: String,
    val skillLevel: Int,
    val durationSeconds: Double
)
