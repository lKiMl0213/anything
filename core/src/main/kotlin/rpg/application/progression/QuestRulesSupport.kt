package rpg.application.progression

import java.time.Instant
import java.time.ZoneId
import rpg.achievement.AchievementTierUnlockedNotification
import rpg.classquest.ClassQuestDynamicEntry
import rpg.classquest.ClassQuestMenu
import rpg.classquest.ClassQuestMenuView
import rpg.model.PlayerState
import rpg.model.QuestObjectiveType
import rpg.model.QuestTier
import rpg.quest.QuestBoardEngine
import rpg.quest.QuestBoardState
import rpg.quest.QuestInstance
import rpg.quest.QuestStatus

class QuestRulesSupport(
    private val classQuestMenu: ClassQuestMenu,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun classQuestEntry(player: PlayerState): ClassQuestDynamicEntry? = classQuestMenu.dynamicEntry(player)

    fun classQuestView(player: PlayerState): ClassQuestMenuView? = classQuestMenu.view(player)

    fun overview(board: QuestBoardState, player: PlayerState): QuestBoardOverviewView {
        return QuestBoardOverviewView(
            replaceSummary = "Replaces: diaria ${remainingReplaces(board, QuestSection.DAILY)} | " +
                "semanal ${remainingReplaces(board, QuestSection.WEEKLY)} | " +
                "mensal ${remainingReplaces(board, QuestSection.MONTHLY)}",
            acceptedCountLabel = "${sectionQuests(board, QuestSection.ACCEPTED).size}/${QuestBoardEngine.MAX_ACCEPTED_ACTIVE}",
            poolCountLabel = board.availableAcceptableQuestPool.size.toString(),
            classQuestLabel = classQuestEntry(player)?.label,
            sections = listOf(
                sectionSummary(board, QuestSection.ACCEPTABLE_POOL),
                sectionSummary(board, QuestSection.ACCEPTED),
                sectionSummary(board, QuestSection.DAILY),
                sectionSummary(board, QuestSection.WEEKLY),
                sectionSummary(board, QuestSection.MONTHLY)
            )
        )
    }

    fun sectionSummary(board: QuestBoardState, section: QuestSection): QuestSectionSummaryView {
        val quests = when (section) {
            QuestSection.CLASS_QUEST -> emptyList()
            QuestSection.ACCEPTABLE_POOL -> board.availableAcceptableQuestPool
            else -> sectionQuests(board, section)
        }
        val countLabel = when (section) {
            QuestSection.ACCEPTABLE_POOL -> "${quests.size} disponivel/is"
            QuestSection.ACCEPTED -> "${quests.size}/${QuestBoardEngine.MAX_ACCEPTED_ACTIVE}"
            QuestSection.CLASS_QUEST -> "-"
            else -> "${quests.size} ativa(s)"
        }
        return QuestSectionSummaryView(
            section = section,
            label = section.label,
            countLabel = countLabel,
            hasAlert = hasReadyToClaim(quests)
        )
    }

    fun sectionQuests(board: QuestBoardState, section: QuestSection): List<QuestInstance> {
        val base = when (section) {
            QuestSection.ACCEPTABLE_POOL -> board.availableAcceptableQuestPool
            QuestSection.ACCEPTED -> board.acceptedQuests
            QuestSection.DAILY -> board.dailyQuests
            QuestSection.WEEKLY -> board.weeklyQuests
            QuestSection.MONTHLY -> board.monthlyQuests
            QuestSection.CLASS_QUEST -> emptyList()
        }
        return when (section) {
            QuestSection.ACCEPTABLE_POOL -> base.sortedBy { it.title }
            QuestSection.CLASS_QUEST -> emptyList()
            else -> base
                .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
                .sortedWith(compareByDescending<QuestInstance> { it.status == QuestStatus.READY_TO_CLAIM }.thenBy { it.title })
        }
    }

    fun findQuest(board: QuestBoardState, section: QuestSection, instanceId: String): QuestInstance? {
        return sectionQuests(board, section).firstOrNull { it.instanceId == instanceId }
    }

    fun hasReadyToClaim(board: QuestBoardState): Boolean {
        return hasReadyToClaim(board.dailyQuests) ||
            hasReadyToClaim(board.weeklyQuests) ||
            hasReadyToClaim(board.monthlyQuests) ||
            hasReadyToClaim(board.acceptedQuests)
    }

    fun hasReadyToClaim(quests: List<QuestInstance>): Boolean {
        return quests.any { it.status == QuestStatus.READY_TO_CLAIM }
    }

    fun remainingReplaces(board: QuestBoardState, section: QuestSection): Int {
        val limit = replaceLimit(section)
        val used = when (section) {
            QuestSection.DAILY -> board.dailyReplaceUsed
            QuestSection.WEEKLY -> board.weeklyReplaceUsed
            QuestSection.MONTHLY -> board.monthlyReplaceUsed
            else -> 0
        }
        return (limit - used).coerceAtLeast(0)
    }

    fun replaceLimit(section: QuestSection): Int {
        return when (section) {
            QuestSection.DAILY -> QuestBoardEngine.DAILY_REPLACE_LIMIT
            QuestSection.WEEKLY -> QuestBoardEngine.WEEKLY_REPLACE_LIMIT
            QuestSection.MONTHLY -> QuestBoardEngine.MONTHLY_REPLACE_LIMIT
            else -> 0
        }
    }

    fun questTier(section: QuestSection): QuestTier? {
        return when (section) {
            QuestSection.DAILY -> QuestTier.DAILY
            QuestSection.WEEKLY -> QuestTier.WEEKLY
            QuestSection.MONTHLY -> QuestTier.MONTHLY
            QuestSection.ACCEPTED -> QuestTier.ACCEPTED
            else -> null
        }
    }

    fun questStatusLabel(status: QuestStatus): String {
        return when (status) {
            QuestStatus.ACTIVE -> "Em andamento"
            QuestStatus.READY_TO_CLAIM -> "Pronta para concluir"
            QuestStatus.CLAIMED -> "Concluida"
            QuestStatus.CANCELLED -> "Cancelada"
        }
    }

    fun objectiveTypeLabel(type: QuestObjectiveType): String {
        return when (type) {
            QuestObjectiveType.KILL_MONSTER -> "Eliminar alvo"
            QuestObjectiveType.KILL_TAG -> "Eliminar categoria"
            QuestObjectiveType.COLLECT_ITEM -> "Coletar item"
            QuestObjectiveType.CRAFT_ITEM -> "Criar item"
            QuestObjectiveType.GATHER_RESOURCE -> "Coletar recurso"
            QuestObjectiveType.REACH_FLOOR -> "Alcancar andar"
            QuestObjectiveType.COMPLETE_RUN -> "Completar run"
        }
    }

    fun formatQuestRewards(quest: QuestInstance, itemName: (String) -> String): String {
        val itemLabel = if (quest.rewards.items.isEmpty()) {
            "sem itens"
        } else {
            quest.rewards.items.joinToString(", ") { reward -> "${itemName(reward.itemId)} x${reward.quantity}" }
        }
        return "XP ${quest.rewards.xp}, Ouro ${quest.rewards.gold}, Moeda ${quest.rewards.specialCurrency}, Itens: $itemLabel"
    }

    fun formatQuestDeadline(expiresAt: Long?): String {
        if (expiresAt == null) return "Sem prazo"
        val instant = Instant.ofEpochMilli(expiresAt).atZone(zoneId)
        return "${instant.toLocalDate()} ${instant.toLocalTime().withNano(0)}"
    }

    fun achievementNotificationLines(notifications: List<AchievementTierUnlockedNotification>): List<String> {
        if (notifications.isEmpty()) return emptyList()
        return notifications.flatMap { notification ->
            listOf(
                "(!) Conquista concluida (!)",
                notification.displayName,
                notification.displayDescription,
                "Recompensa disponivel: ${notification.rewardGold} ouro"
            )
        }
    }
}
