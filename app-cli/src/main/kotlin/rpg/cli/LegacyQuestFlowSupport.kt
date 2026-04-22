package rpg.cli

import java.time.Instant
import java.time.ZoneId
import rpg.model.QuestTier
import rpg.quest.QuestInstance
import rpg.quest.QuestStatus

internal class LegacyQuestFlowSupport(
    private val itemName: (itemId: String) -> String,
    private val uiColor: (text: String, colorCode: String) -> String,
    private val ansiQuestActive: String,
    private val ansiQuestReady: String,
    private val questZoneId: ZoneId
) {
    fun hasReadyToClaim(quests: List<QuestInstance>): Boolean {
        return quests.any { it.status == QuestStatus.READY_TO_CLAIM }
    }

    fun questsForTier(board: rpg.quest.QuestBoardState, tier: QuestTier): List<QuestInstance> {
        val base = when (tier) {
            QuestTier.DAILY -> board.dailyQuests
            QuestTier.WEEKLY -> board.weeklyQuests
            QuestTier.MONTHLY -> board.monthlyQuests
            QuestTier.ACCEPTED -> board.acceptedQuests
        }
        return base
            .filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.READY_TO_CLAIM }
            .sortedWith(compareByDescending<QuestInstance> { it.status == QuestStatus.READY_TO_CLAIM }.thenBy { it.title })
    }

    fun remainingReplaces(used: Int, tier: QuestTier): Int {
        val limit = replaceLimitFor(tier)
        return (limit - used).coerceAtLeast(0)
    }

    fun replaceLimitFor(tier: QuestTier): Int {
        return when (tier) {
            QuestTier.DAILY -> rpg.quest.QuestBoardEngine.DAILY_REPLACE_LIMIT
            QuestTier.WEEKLY -> rpg.quest.QuestBoardEngine.WEEKLY_REPLACE_LIMIT
            QuestTier.MONTHLY -> rpg.quest.QuestBoardEngine.MONTHLY_REPLACE_LIMIT
            QuestTier.ACCEPTED -> 0
        }
    }

    fun tierLabel(tier: QuestTier): String {
        return when (tier) {
            QuestTier.DAILY -> "Quests Diarias"
            QuestTier.WEEKLY -> "Quests Semanais"
            QuestTier.MONTHLY -> "Quests Mensais"
            QuestTier.ACCEPTED -> "Quests Aceitas"
        }
    }

    fun showQuestDetails(quest: QuestInstance) {
        println("\n--- ${quest.title} ---")
        println("Tipo: ${quest.objectiveType.name}")
        println("Descricao: ${quest.description}")
        if (quest.hint.isNotBlank()) {
            println("Dica: ${quest.hint}")
        }
        println("Progresso: ${quest.currentProgress}/${quest.requiredAmount}")
        println("Status: ${questStatusLabelColored(quest.status)}")
        println("Categoria: ${tierLabel(quest.tier)}")
        println("Prazo: ${formatQuestDeadline(quest.expiresAt)}")
        println("Recompensa: ${formatQuestRewards(quest)}")
    }

    private fun formatQuestRewards(quest: QuestInstance): String {
        val items = if (quest.rewards.items.isEmpty()) {
            "sem itens"
        } else {
            quest.rewards.items.joinToString(", ") { "${itemName(it.itemId)} x${it.quantity}" }
        }
        return "XP ${quest.rewards.xp}, Ouro ${quest.rewards.gold}, Moeda ${quest.rewards.specialCurrency}, Itens: $items"
    }

    private fun formatQuestDeadline(expiresAt: Long?): String {
        if (expiresAt == null) return "Sem prazo"
        val instant = Instant.ofEpochMilli(expiresAt).atZone(questZoneId)
        return "${instant.toLocalDate()} ${instant.toLocalTime().withNano(0)}"
    }

    private fun questStatusLabel(status: QuestStatus): String {
        return when (status) {
            QuestStatus.ACTIVE -> "Ativa"
            QuestStatus.READY_TO_CLAIM -> "Pronta"
            QuestStatus.CLAIMED -> "Concluida"
            QuestStatus.CANCELLED -> "Cancelada"
        }
    }

    fun questStatusLabelColored(status: QuestStatus): String {
        val base = questStatusLabel(status)
        return when (status) {
            QuestStatus.ACTIVE -> uiColor(base, ansiQuestActive)
            QuestStatus.READY_TO_CLAIM -> uiColor(base, ansiQuestReady)
            else -> base
        }
    }
}
