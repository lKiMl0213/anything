package rpg.application.session

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.navigation.NavigationState

internal class AutoSavePolicyService {
    private val globalBossScreens = setOf(
        NavigationState.GlobalBossMenu,
        NavigationState.GlobalBossEventDetail,
        NavigationState.GlobalBossMilestones
    )

    fun shouldAutosave(before: GameSession, action: GameAction, after: GameSession): Boolean {
        if (after.gameState == null) return false
        if (action == GameAction.SaveAutosave || action == GameAction.SaveCurrentGame) return false

        if (action is GameAction.EnterDungeon || action == GameAction.ExitDungeonRun) {
            return true
        }
        if (action is GameAction.AllocateAttributePoint || action is GameAction.AllocateAttributePoints) {
            return true
        }

        val enteredDungeonEvent = before.navigation != NavigationState.DungeonEvent &&
            after.navigation == NavigationState.DungeonEvent
        val exitedDungeonEvent = before.navigation == NavigationState.DungeonEvent &&
            after.navigation != NavigationState.DungeonEvent
        if (enteredDungeonEvent || exitedDungeonEvent) {
            return true
        }

        val enteredGlobalBoss = before.navigation !in globalBossScreens &&
            after.navigation in globalBossScreens
        val exitedGlobalBoss = before.navigation in globalBossScreens &&
            after.navigation !in globalBossScreens
        if (enteredGlobalBoss || exitedGlobalBoss) {
            return true
        }

        return before.navigation != NavigationState.MainMenu && after.navigation == NavigationState.MainMenu
    }

    fun shouldAutosaveAfterCombat(before: GameSession, after: GameSession): Boolean {
        if (after.gameState == null) return false
        if (before.navigation == NavigationState.Combat) return true
        return before.pendingEncounter != null && after.pendingEncounter == null
    }
}
