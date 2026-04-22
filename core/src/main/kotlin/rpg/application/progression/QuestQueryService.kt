package rpg.application.progression

import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.quest.QuestStatus

class QuestQueryService(
    private val engine: GameEngine,
    private val support: QuestRulesSupport
) {
    fun hasQuestAlert(state: GameState): Boolean {
        return support.classQuestEntry(state.player) != null || support.hasReadyToClaim(state.questBoard)
    }

    fun questBoardOverview(state: GameState): QuestBoardOverviewView {
        return support.overview(state.questBoard, state.player)
    }

    fun questList(state: GameState, section: QuestSection): QuestListView {
        val quests = support.sectionQuests(state.questBoard, section)
        return QuestListView(
            section = section,
            title = section.label,
            emptyMessage = when (section) {
                QuestSection.ACCEPTABLE_POOL -> "Pool vazia. Aguarde o proximo ciclo de 20 minutos."
                QuestSection.ACCEPTED -> "Nenhuma quest aceita."
                QuestSection.DAILY,
                QuestSection.WEEKLY,
                QuestSection.MONTHLY -> "Nenhuma quest ativa nesta categoria."
                QuestSection.CLASS_QUEST -> "Quest de classe indisponivel."
            },
            quests = quests.map { quest ->
                QuestListItemView(
                    instanceId = quest.instanceId,
                    title = quest.title,
                    progressLabel = "${quest.currentProgress}/${quest.requiredAmount}",
                    statusLabel = support.questStatusLabel(quest.status)
                )
            }
        )
    }

    fun questDetail(state: GameState, section: QuestSection, instanceId: String): QuestDetailView? {
        val quest = support.findQuest(state.questBoard, section, instanceId) ?: return null
        return QuestDetailView(
            section = section,
            instanceId = quest.instanceId,
            title = quest.title,
            detailLines = buildList {
                add("Tipo: ${support.objectiveTypeLabel(quest.objectiveType)}")
                add("Descricao: ${quest.description}")
                if (quest.hint.isNotBlank()) add("Dica: ${quest.hint}")
                add("Progresso: ${quest.currentProgress}/${quest.requiredAmount}")
                add("Status: ${support.questStatusLabel(quest.status)}")
                add("Categoria: ${section.label}")
                add("Prazo: ${support.formatQuestDeadline(quest.expiresAt)}")
                add("Recompensa: ${support.formatQuestRewards(quest) { itemId -> itemName(itemId) }}")
            },
            canAccept = section == QuestSection.ACCEPTABLE_POOL,
            canClaim = quest.status == QuestStatus.READY_TO_CLAIM,
            canCancel = section == QuestSection.ACCEPTED && quest.canCancel,
            canReplace = section in listOf(QuestSection.DAILY, QuestSection.WEEKLY, QuestSection.MONTHLY) &&
                support.remainingReplaces(state.questBoard, section) > 0 &&
                quest.status != QuestStatus.CLAIMED,
            replaceRemaining = support.remainingReplaces(state.questBoard, section)
        )
    }

    fun classQuestView(state: GameState): ClassQuestViewData? {
        val view = support.classQuestView(state.player) ?: return null
        return ClassQuestViewData(
            title = view.title,
            statusLabel = view.statusLabel,
            detailLines = view.lines,
            canChoosePath = view.canChoosePath,
            pathAId = view.context.definition.pathA,
            pathALabel = view.context.definition.pathAName,
            pathBId = view.context.definition.pathB,
            pathBLabel = view.context.definition.pathBName,
            canCancel = view.canCancel
        )
    }

    private fun itemName(itemId: String): String {
        return engine.itemResolver.resolve(itemId, emptyMap())?.name ?: itemId
    }
}
