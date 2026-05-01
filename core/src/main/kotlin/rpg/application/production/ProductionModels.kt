package rpg.application.production

import rpg.model.CraftDiscipline
import rpg.model.GatheringType
import rpg.model.SkillType

data class ProductionRecipeView(
    val id: String,
    val name: String,
    val outputLabel: String,
    val discipline: CraftDiscipline,
    val available: Boolean,
    val maxCraftable: Int,
    val batchSize: Int,
    val estimatedPerActionSeconds: Double,
    val estimatedBatchSeconds: Double,
    val blockedReasons: List<String>,
    val ingredientLines: List<String>
)

data class ProductionGatherNodeView(
    val id: String,
    val name: String,
    val type: GatheringType,
    val resourceLabel: String,
    val skillType: SkillType,
    val skillLevel: Int,
    val minSkillLevel: Int,
    val durationSeconds: Double,
    val available: Boolean
)

data class ProductionTimedActionView(
    val categoryLabel: String,
    val actionLabel: String,
    val skillLabel: String,
    val skillLevel: Int,
    val durationSeconds: Double
)

data class ProductionPrepareResult(
    val ready: Boolean,
    val messages: List<String>,
    val timedActionView: ProductionTimedActionView? = null
)

data class ProductionMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>
)
