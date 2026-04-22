package rpg.quest

import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.QuestObjectiveType
import rpg.model.QuestTemplateDef

internal class QuestTargetResolver(
    private val repo: DataRepository,
    private val rng: Random,
    private val catalogSupport: QuestGenerationCatalogSupport
) {
    fun resolveTarget(template: QuestTemplateDef, context: QuestGenerationContext): TargetResolution? {
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> {
                val chosenId = template.targetId
                    ?: context.accessibleMonsterIds.toList().takeIf { it.isNotEmpty() }?.random(rng)
                    ?: return null
                val archetype = repo.monsterArchetypes[chosenId]
                val name = archetype?.displayName?.takeIf { it.isNotBlank() }
                    ?: archetype?.name
                    ?: chosenId
                val diff = 1.0 + ((archetype?.baseXp ?: 20) / 120.0)
                TargetResolution(
                    targetId = chosenId,
                    targetName = name,
                    difficultyFactor = diff.coerceAtMost(3.0)
                )
            }
            QuestObjectiveType.KILL_TAG -> {
                val chosenTag = template.targetTag
                    ?: template.targetId
                    ?: context.accessibleMonsterTags.toList().takeIf { it.isNotEmpty() }?.random(rng)
                    ?: return null
                TargetResolution(
                    targetTag = chosenTag,
                    targetName = chosenTag.replace('_', ' '),
                    difficultyFactor = if (chosenTag == "elite") 1.8 else 1.2
                )
            }
            QuestObjectiveType.COLLECT_ITEM -> {
                val chosenItemId = template.targetId
                    ?: catalogSupport.pickCollectItem(context)
                    ?: return null
                val name = catalogSupport.itemName(chosenItemId)
                TargetResolution(
                    targetId = chosenItemId,
                    targetName = name,
                    difficultyFactor = catalogSupport.itemDifficulty(chosenItemId)
                )
            }
            QuestObjectiveType.CRAFT_ITEM -> {
                val targetTag = template.targetTag?.lowercase()
                val pool = context.craftableRecipes.filter { recipe ->
                    (template.targetId == null || recipe.outputItemId == template.targetId) &&
                        (targetTag == null || catalogSupport.craftDisciplineTag(recipe) == targetTag)
                }
                val recipe = pool.randomOrNull(rng) ?: return null
                TargetResolution(
                    targetId = recipe.outputItemId,
                    targetTag = catalogSupport.craftDisciplineTag(recipe),
                    targetName = catalogSupport.itemName(recipe.outputItemId),
                    difficultyFactor = 1.2 + (recipe.ingredients.size * 0.25)
                )
            }
            QuestObjectiveType.GATHER_RESOURCE -> {
                val targetTag = template.targetTag?.lowercase()
                val pool = context.gatherableNodes.filter { node ->
                    (template.targetId == null || node.id == template.targetId || node.resourceItemId == template.targetId) &&
                        (targetTag == null || catalogSupport.gatherTag(node) == targetTag)
                }
                val node = pool.randomOrNull(rng) ?: return null
                TargetResolution(
                    targetId = node.resourceItemId,
                    targetTag = catalogSupport.gatherTag(node),
                    targetName = catalogSupport.itemName(node.resourceItemId),
                    difficultyFactor = 1.0 + (node.minPlayerLevel / 40.0)
                )
            }
            QuestObjectiveType.REACH_FLOOR -> {
                TargetResolution(
                    targetName = "andar da dungeon",
                    difficultyFactor = 1.0
                )
            }
            QuestObjectiveType.COMPLETE_RUN -> {
                TargetResolution(
                    targetName = "exploracao completa",
                    difficultyFactor = 1.0
                )
            }
        }
    }
}
