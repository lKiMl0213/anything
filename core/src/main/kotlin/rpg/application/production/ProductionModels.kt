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

data class ProductionMutationResult(
    val state: rpg.model.GameState,
    val messages: List<String>
)
