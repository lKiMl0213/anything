package rpg.quest

import rpg.model.QuestObjectiveType
import rpg.model.QuestTemplateDef
import rpg.model.QuestTier

internal class QuestTextGenerator(
    private val catalogSupport: QuestGenerationCatalogSupport
) {
    fun renderTitle(
        template: QuestTemplateDef,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        if (template.titleTemplate.isNotBlank()) {
            return applyTemplate(template.titleTemplate, target, amount, tier)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> "Elimine $amount ${target.targetName}"
            QuestObjectiveType.KILL_TAG -> "Elimine $amount inimigos ${target.targetName}"
            QuestObjectiveType.COLLECT_ITEM -> "Colete $amount ${target.targetName}"
            QuestObjectiveType.CRAFT_ITEM -> "Crie $amount ${target.targetName}"
            QuestObjectiveType.GATHER_RESOURCE -> "Obtenha $amount ${target.targetName}"
            QuestObjectiveType.REACH_FLOOR -> "Alcance o andar $amount"
            QuestObjectiveType.COMPLETE_RUN -> "Conclua $amount exploracoes"
        }
    }

    fun renderDescription(
        template: QuestTemplateDef,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        if (template.descriptionTemplate.isNotBlank()) {
            return applyTemplate(template.descriptionTemplate, target, amount, tier)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER ->
                "Derrote $amount ${target.targetName} nas areas desbloqueadas da dungeon."
            QuestObjectiveType.KILL_TAG ->
                "Derrote $amount monstros da categoria ${target.targetName}."
            QuestObjectiveType.COLLECT_ITEM ->
                "Colete $amount ${target.targetName}. A entrega sera validada no inventario."
            QuestObjectiveType.CRAFT_ITEM ->
                "Crie $amount ${target.targetName} em uma disciplina de craft valida."
            QuestObjectiveType.GATHER_RESOURCE ->
                "Reuna $amount ${target.targetName} em pontos de coleta disponiveis."
            QuestObjectiveType.REACH_FLOOR ->
                "Alcance pelo menos o andar $amount da dungeon infinita."
            QuestObjectiveType.COMPLETE_RUN ->
                "Finalize $amount exploracoes da dungeon."
        }
    }

    fun renderHint(template: QuestTemplateDef, target: TargetResolution): String {
        if (template.hintTemplate.isNotBlank()) {
            return applyTemplate(template.hintTemplate, target, 0, QuestTier.ACCEPTED)
        }
        val itemId = target.targetId
        if (itemId != null && template.objectiveType in setOf(
                QuestObjectiveType.COLLECT_ITEM,
                QuestObjectiveType.CRAFT_ITEM,
                QuestObjectiveType.GATHER_RESOURCE
            )
        ) {
            return catalogSupport.itemSourceHint(itemId)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER,
            QuestObjectiveType.KILL_TAG -> "Procure inimigos em tiers desbloqueados."
            QuestObjectiveType.REACH_FLOOR -> "Avance de forma consistente; fugir encerra o progresso da run."
            QuestObjectiveType.COMPLETE_RUN -> "Concluir uma run conta ao sair com sucesso."
            else -> ""
        }
    }

    private fun applyTemplate(
        template: String,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        return template
            .replace("{target}", target.targetName)
            .replace("{targetId}", target.targetId ?: "")
            .replace("{targetTag}", target.targetTag ?: "")
            .replace("{amount}", amount.toString())
            .replace("{tier}", tier.name.lowercase())
    }
}
