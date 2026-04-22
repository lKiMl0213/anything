package rpg.application.progression

import rpg.achievement.AchievementService
import rpg.model.GameState

class AchievementCommandService(
    private val achievementService: AchievementService,
    private val support: QuestRulesSupport
) {
    fun claimReward(state: GameState, achievementId: String): ProgressionMutationResult {
        val result = achievementService.claimReward(state.player, achievementId)
        val messages = buildList {
            add(result.message)
            addAll(support.achievementNotificationLines(result.unlockedTiers))
        }
        return ProgressionMutationResult(
            state = state.copy(player = result.player),
            messages = messages
        )
    }
}
