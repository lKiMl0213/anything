package rpg.application.production

import rpg.model.CraftRecipeDef
import rpg.model.SkillType

internal data class OrderedRecipeEntry(
    val recipe: CraftRecipeDef,
    val skillType: SkillType
)

internal data class RecipeOrderCacheEntry(
    val catalogRevision: Int,
    val entries: List<OrderedRecipeEntry>,
    val byId: Map<String, OrderedRecipeEntry>
)

internal data class RecipeListCacheKey(
    val catalogRevision: Int,
    val inventorySignature: Int,
    val skillSignature: Int
)

internal data class RecipeListCacheEntry(
    val key: RecipeListCacheKey,
    val views: List<ProductionRecipeView>
)

internal data class InventoryCountSnapshot(
    val itemCounts: Map<String, Int>,
    val templateCounts: Map<String, Int>,
    val signature: Int
)
