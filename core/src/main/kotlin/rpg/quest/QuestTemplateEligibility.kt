package rpg.quest

import rpg.model.QuestObjectiveType
import rpg.model.QuestTemplateDef

internal class QuestTemplateEligibility(
    private val catalogSupport: QuestGenerationCatalogSupport
) {
    fun isTemplateAllowed(template: QuestTemplateDef, context: QuestGenerationContext): Boolean {
        val constraints = template.constraints
        val level = context.player.level
        if (level < constraints.minPlayerLevel || level > constraints.maxPlayerLevel) return false
        if (constraints.requiresCrafting && !context.craftingEnabled) return false
        if (constraints.requiresGathering && !context.gatheringEnabled) return false
        if (constraints.requiresDungeon && !context.dungeonEnabled) return false
        if (constraints.requiresUnlockedTierId != null && constraints.requiresUnlockedTierId !in context.unlockedTierIds) return false
        if (constraints.requiresMonsterTag != null && constraints.requiresMonsterTag !in context.accessibleMonsterTags) return false

        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> {
                val targetId = template.targetId
                targetId == null || targetId in context.accessibleMonsterIds
            }
            QuestObjectiveType.KILL_TAG -> {
                val tag = template.targetTag ?: template.targetId
                tag == null ||
                    tag in context.accessibleMonsterTags ||
                    tag in setOf("elite", "boss", "common", "rare", "epic", "legendary")
            }
            QuestObjectiveType.COLLECT_ITEM -> {
                val itemId = template.targetId
                if (itemId == null) {
                    catalogSupport.pickCollectItem(context) != null
                } else {
                    itemId in context.availableItemIds
                }
            }
            QuestObjectiveType.CRAFT_ITEM -> {
                if (!context.craftingEnabled) return false
                val targetId = template.targetId
                val targetTag = template.targetTag?.lowercase()
                context.craftableRecipes.any { recipe ->
                    (targetId == null || recipe.outputItemId == targetId) &&
                        (targetTag == null || catalogSupport.craftDisciplineTag(recipe) == targetTag)
                }
            }
            QuestObjectiveType.GATHER_RESOURCE -> {
                if (!context.gatheringEnabled) return false
                val targetId = template.targetId
                val targetTag = template.targetTag?.lowercase()
                context.gatherableNodes.any { node ->
                    (targetId == null || node.resourceItemId == targetId || node.id == targetId) &&
                        (targetTag == null || catalogSupport.gatherTag(node) == targetTag)
                }
            }
            QuestObjectiveType.REACH_FLOOR,
            QuestObjectiveType.COMPLETE_RUN -> context.dungeonEnabled
        }
    }
}
