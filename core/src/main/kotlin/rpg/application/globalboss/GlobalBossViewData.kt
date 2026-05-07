package rpg.application.globalboss

import rpg.globalboss.config.GlobalBossCadence

data class GlobalBossMenuItemView(
    val eventId: String,
    val cadence: GlobalBossCadence,
    val title: String,
    val runsLabel: String,
    val timeRemainingLabel: String = "-",
    val alert: Boolean
)

data class GlobalBossMenuView(
    val items: List<GlobalBossMenuItemView>,
    val hasAlert: Boolean
)

data class GlobalBossMilestoneView(
    val id: String,
    val label: String,
    val rewardLabel: String,
    val statusLabel: String,
    val claimable: Boolean,
    val claimed: Boolean,
    val claimedAtLabel: String? = null
)

data class GlobalBossQuestView(
    val label: String,
    val completed: Boolean
)

data class GlobalBossEventDetailView(
    val eventId: String,
    val cadence: GlobalBossCadence,
    val title: String,
    val description: String,
    val bossName: String,
    val totalDamageLabel: String,
    val totalPointsLabel: String,
    val bestRunLabel: String,
    val runsLabel: String,
    val cycleRemainingLabel: String = "-",
    val runsRemaining: Int,
    val claimableMilestonesCount: Int,
    val canStartRun: Boolean,
    val canAutoClear: Boolean,
    val canBuyAttempt: Boolean,
    val buyCostCash: Int,
    val milestones: List<GlobalBossMilestoneView>,
    val quests: List<GlobalBossQuestView>,
    val rankingLabel: String? = null,
    val alert: Boolean
)

data class GlobalBossMilestoneMenuView(
    val eventId: String,
    val title: String,
    val summaryLines: List<String>,
    val milestones: List<GlobalBossMilestoneView>,
    val claimableMilestonesCount: Int,
    val alert: Boolean
)
