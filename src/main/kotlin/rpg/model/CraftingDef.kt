package rpg.model

import kotlinx.serialization.Serializable

@Serializable
enum class CraftDiscipline {
    FORGE,
    ALCHEMY,
    COOKING
}

@Serializable
data class RecipeIngredientDef(
    val itemId: String,
    val quantity: Int = 1
)

@Serializable
data class CraftRecipeDef(
    val id: String,
    val name: String,
    val discipline: CraftDiscipline,
    val outputItemId: String,
    val outputQty: Int = 1,
    val minPlayerLevel: Int = 1,
    val minSkillLevel: Int = 1,
    val skillType: SkillType? = null,
    val baseDurationSeconds: Double = 7.0,
    val baseXp: Double = 18.0,
    val difficulty: Double = 1.0,
    val tier: Int = 1,
    val ingredients: List<RecipeIngredientDef> = emptyList(),
    val description: String = "",
    val enabled: Boolean = true,
    val tags: List<String> = emptyList()
)
