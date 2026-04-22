package rpg.application.progression

import rpg.achievement.AchievementCategory

data class AchievementCategorySummaryView(
    val category: AchievementCategory,
    val label: String,
    val totalCount: Int,
    val readyRewards: Int,
    val maxedCount: Int
)

data class AchievementOverviewView(
    val hasClaimableRewards: Boolean,
    val categories: List<AchievementCategorySummaryView>
)

data class AchievementListItemView(
    val achievementId: String,
    val title: String,
    val description: String,
    val progressLabel: String,
    val completionLabel: String,
    val rewardLabel: String,
    val statusLabel: String,
    val rewardAvailable: Boolean
)

data class AchievementCategoryViewData(
    val category: AchievementCategory,
    val title: String,
    val items: List<AchievementListItemView>
)

data class AchievementDetailViewData(
    val category: AchievementCategory,
    val achievementId: String,
    val title: String,
    val detailLines: List<String>,
    val canClaim: Boolean
)

data class AchievementStatisticsViewData(
    val generalLines: List<String>,
    val starLines: List<String>,
    val bestiaryLines: List<String>,
    val totalMonstersKilled: Long
)
