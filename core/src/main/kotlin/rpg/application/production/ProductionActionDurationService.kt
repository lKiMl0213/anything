package rpg.application.production

import kotlin.math.max
import kotlin.math.min
import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.GameState
import rpg.model.GatherNodeDef
import rpg.model.GatheringType

class ProductionActionDurationService(
    private val engine: GameEngine
) {
    fun resolveCraft(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String
    ): CraftDurationResolution? {
        val recipe = engine.craftingService.availableRecipes(state.player.level, discipline)
            .firstOrNull { it.id == recipeId }
            ?: return null
        val maxCraftable = engine.craftingService.maxCraftable(state.player, state.itemInstances, recipe)
        if (maxCraftable <= 0) return null
        val craftBatchLimit = engine.permanentUpgradeService.craftBatchLimit(state.player)
        val times = min(maxCraftable, max(1, craftBatchLimit))
        val skill = engine.craftingService.recipeSkill(recipe)
        val snapshot = engine.skillSystem.snapshot(state.player, skill)
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = recipe.baseDurationSeconds * times.coerceAtLeast(1),
            skillLevel = snapshot.level
        )
        return CraftDurationResolution(
            recipe = recipe,
            times = times,
            skillLabel = skill.name.lowercase(),
            skillLevel = snapshot.level,
            durationSeconds = duration
        )
    }

    fun resolveGather(
        state: GameState,
        type: GatheringType,
        nodeId: String
    ): GatherDurationResolution? {
        val node = engine.gatheringService.availableNodes(state.player.level, type)
            .firstOrNull { it.id == nodeId }
            ?: return null
        val skill = engine.gatheringService.nodeSkill(node)
        val snapshot = engine.skillSystem.snapshot(state.player, skill)
        val duration = engine.skillSystem.actionDurationSeconds(
            baseSeconds = node.baseDurationSeconds,
            skillLevel = snapshot.level
        )
        return GatherDurationResolution(
            node = node,
            skillLabel = skill.name.lowercase(),
            skillLevel = snapshot.level,
            durationSeconds = duration
        )
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
