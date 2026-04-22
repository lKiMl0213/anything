package rpg.quest

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.GatherNodeDef
import rpg.model.GatheringType
import rpg.model.ItemType

internal class QuestGenerationCatalogSupport(
    private val repo: DataRepository,
    private val rng: Random
) {
    fun craftDisciplineTag(recipe: CraftRecipeDef): String {
        return craftSkillTag(recipe)
    }

    fun gatherTag(node: GatherNodeDef): String {
        return gatherSkillTag(node)
    }

    fun craftSkillTag(recipe: CraftRecipeDef): String {
        return recipe.skillType?.name?.lowercase() ?: when (recipe.discipline) {
            CraftDiscipline.FORGE -> "blacksmith"
            CraftDiscipline.ALCHEMY -> "alchemist"
            CraftDiscipline.COOKING -> "cooking"
        }
    }

    fun gatherSkillTag(node: GatherNodeDef): String {
        return node.skillType?.name?.lowercase() ?: when (node.type) {
            GatheringType.MINING -> "mining"
            GatheringType.HERBALISM -> "gathering"
            GatheringType.WOODCUTTING -> "woodcutting"
            GatheringType.FISHING -> "fishing"
        }
    }

    fun itemDifficulty(itemId: String): Double {
        val template = repo.itemTemplates[itemId]
        if (template != null) {
            return 1.0 + (template.dropTier * 0.25)
        }
        val dropCount = repo.dropTables.values.count { table -> table.entries.any { it.itemId == itemId } }
        return if (dropCount <= 0) 1.0 else (1.2 / dropCount).coerceAtLeast(0.8)
    }

    fun itemName(itemId: String): String {
        return repo.items[itemId]?.name
            ?: repo.itemTemplates[itemId]?.name
            ?: itemId
    }

    fun pickCollectItem(context: QuestGenerationContext): String? {
        val fromDrops = repo.dropTables.values
            .flatMap { it.entries.map { entry -> entry.itemId } }
            .filter { itemId ->
                itemId in context.availableItemIds &&
                    (repo.items[itemId]?.type == ItemType.CONSUMABLE ||
                        repo.items[itemId]?.type == ItemType.MATERIAL)
            }
            .distinct()
        val fromGather = context.gatherableNodes.map { it.resourceItemId }
        val pool = (fromDrops + fromGather).distinct()
        return pool.randomOrNull(rng)
    }

    fun itemSourceHint(itemId: String): String {
        val fromGather = repo.gatherNodes.values.filter { it.resourceItemId == itemId }.map { it.name }
        if (fromGather.isNotEmpty()) {
            return "Pode ser obtido em gathering: ${fromGather.joinToString(", ")}."
        }

        val recipe = repo.craftRecipes.values.firstOrNull { it.outputItemId == itemId }
        if (recipe != null) {
            return "Pode ser criado via ${recipe.discipline.name.lowercase()} na receita ${recipe.name}."
        }

        val tables = repo.dropTables.values.filter { table ->
            table.entries.any { it.itemId == itemId }
        }.map { it.id }
        if (tables.isNotEmpty()) {
            val archetypes = repo.monsterArchetypes.values
                .filter { it.dropTableId in tables }
                .map { it.name }
                .distinct()
                .take(4)
            if (archetypes.isNotEmpty()) {
                return "Pode dropar de monstros como: ${archetypes.joinToString(", ")}."
            }
            return "Pode dropar em tabelas: ${tables.joinToString(", ")}."
        }

        return "Consulte as fontes desbloqueadas no seu progresso atual."
    }
}
