package rpg.application.progression

import rpg.achievement.AchievementCategory
import rpg.achievement.AchievementMenu
import rpg.achievement.AchievementService
import rpg.model.GameState

class AchievementQueryService(
    private val achievementService: AchievementService,
    private val achievementMenu: AchievementMenu,
    private val knownBaseTypes: Set<String>
) {
    fun hasClaimableRewards(state: GameState): Boolean {
        return achievementService.hasClaimableRewards(state.player)
    }

    fun overview(state: GameState): AchievementOverviewView {
        val list = achievementMenu.buildAchievementList(state.player)
        val grouped = list.views.groupBy { it.category }
        val categories = AchievementCategory.entries
            .filter { grouped.containsKey(it) }
            .map { category ->
                val views = grouped[category].orEmpty()
                AchievementCategorySummaryView(
                    category = category,
                    label = category.label,
                    totalCount = views.size,
                    readyRewards = views.count { it.rewardAvailable },
                    maxedCount = views.count { it.maxTierReached }
                )
            }
        return AchievementOverviewView(
            hasClaimableRewards = categories.any { it.readyRewards > 0 },
            categories = categories
        )
    }

    fun category(state: GameState, category: AchievementCategory): AchievementCategoryViewData {
        val list = achievementMenu.buildAchievementList(state.player)
        val items = list.views
            .filter { it.category == category }
            .map { view ->
                val target = view.currentTierTarget?.toString() ?: "MAX"
                val reward = view.nextRewardGold?.let { "$it ouro" } ?: "MAX"
                AchievementListItemView(
                    achievementId = view.id,
                    title = view.displayName,
                    description = view.displayDescription,
                    progressLabel = "${view.currentValue}/$target",
                    completionLabel = "${view.timesCompleted}x",
                    rewardLabel = reward,
                    statusLabel = view.status.label,
                    rewardAvailable = view.rewardAvailable
                )
            }
        return AchievementCategoryViewData(
            category = category,
            title = category.label,
            items = items
        )
    }

    fun detail(state: GameState, category: AchievementCategory, achievementId: String): AchievementDetailViewData? {
        val view = achievementMenu.buildAchievementList(state.player).views
            .firstOrNull { it.category == category && it.id == achievementId }
            ?: return null
        val target = view.currentTierTarget?.toString() ?: "MAX"
        val reward = view.nextRewardGold?.let { "$it ouro" } ?: "MAX"
        return AchievementDetailViewData(
            category = category,
            achievementId = view.id,
            title = view.displayName,
            detailLines = listOf(
                view.displayDescription,
                "Progresso atual: ${view.currentValue}/$target",
                "Concluida(s): ${view.timesCompleted}",
                "Recompensa do proximo tier: $reward",
                "Status: ${view.status.label}"
            ),
            canClaim = view.rewardAvailable
        )
    }

    fun statistics(state: GameState): AchievementStatisticsViewData {
        val statsView = achievementMenu.buildStatistics(state.player, knownBaseTypes)
        return AchievementStatisticsViewData(
            generalLines = statsView.generalLines,
            starLines = statsView.killsByStarLines,
            bestiaryLines = statsView.bestiaryLines,
            totalMonstersKilled = statsView.player.lifetimeStats.totalMonstersKilled
        )
    }
}
