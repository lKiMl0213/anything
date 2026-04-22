package rpg.quest

import rpg.io.DataRepository
import rpg.model.PlayerState

internal class QuestGenerationContextBuilder(
    private val repo: DataRepository,
    private val catalogSupport: QuestGenerationCatalogSupport
) {
    fun buildContext(player: PlayerState): QuestGenerationContext {
        val unlockedTiers = repo.mapTiers.values
            .filter { player.level >= it.minLevel }
            .mapTo(mutableSetOf()) { it.id }
        val allowedMonsterIds = repo.mapTiers.values
            .filter { it.id in unlockedTiers }
            .flatMap { it.allowedMonsterTemplates }
            .toMutableSet()
        if (allowedMonsterIds.isEmpty()) {
            allowedMonsterIds.addAll(repo.monsterArchetypes.keys)
        }
        val accessibleArchetypes = allowedMonsterIds.mapNotNull { repo.monsterArchetypes[it] }
        val accessibleTags = mutableSetOf<String>()
        for (archetype in accessibleArchetypes) {
            accessibleTags.addAll(archetype.tags)
            if (archetype.archetype.isNotBlank()) accessibleTags.add(archetype.archetype.lowercase())
            val baseType = archetype.baseType.ifBlank { archetype.id.substringBefore('_') }.lowercase()
            val family = archetype.family.ifBlank { archetype.archetype }.lowercase()
            val monsterType = archetype.monsterTypeId.ifBlank { family }.lowercase()
            accessibleTags.add(baseType)
            if (family.isNotBlank()) accessibleTags.add(family)
            if (monsterType.isNotBlank()) {
                accessibleTags.add(monsterType)
                accessibleTags.add("type:$monsterType")
            }
            accessibleTags.addAll(archetype.questTags.map { it.lowercase() })
        }

        val availableItemIds = mutableSetOf<String>()
        availableItemIds.addAll(repo.items.keys)
        availableItemIds.addAll(repo.itemTemplates.keys)

        val skillLevels = player.skillProgress.mapValues { (_, progress) -> progress.level }
        val craftableRecipes = repo.craftRecipes.values
            .filter { recipe ->
                recipe.enabled &&
                    player.level >= recipe.minPlayerLevel &&
                    skillLevels.getOrDefault(catalogSupport.craftSkillTag(recipe).uppercase(), 1) >= recipe.minSkillLevel
            }
        val gatherableNodes = repo.gatherNodes.values
            .filter { node ->
                node.enabled &&
                    player.level >= node.minPlayerLevel &&
                    skillLevels.getOrDefault(catalogSupport.gatherSkillTag(node).uppercase(), 1) >= node.minSkillLevel
            }

        return QuestGenerationContext(
            player = player,
            unlockedTierIds = unlockedTiers,
            accessibleMonsterIds = allowedMonsterIds,
            accessibleMonsterTags = accessibleTags,
            availableItemIds = availableItemIds,
            craftableRecipes = craftableRecipes,
            gatherableNodes = gatherableNodes,
            craftingEnabled = repo.craftRecipes.isNotEmpty(),
            gatheringEnabled = repo.gatherNodes.isNotEmpty(),
            dungeonEnabled = repo.mapTiers.isNotEmpty()
        )
    }
}
