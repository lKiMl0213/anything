package rpg.presentation

import rpg.application.GameSession
import rpg.application.PendingEncounter
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.presentation.model.CombatScreenViewModel
import rpg.presentation.model.ProgressBarViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class CombatScreenPresenter(
    private val engine: GameEngine,
    private val support: PresentationSupport
) {
    fun presentCombat(
        encounter: PendingEncounter,
        logLines: List<String>,
        messages: List<String>,
        playerHp: Double,
        playerMp: Double,
        enemyHp: Double
    ): CombatScreenViewModel {
        val playerStats = engine.computePlayerStats(encounter.player, encounter.itemInstances)
        val enemyStats = engine.computeMonsterStats(encounter.monster)
        return CombatScreenViewModel(
            title = if (encounter.isBoss) "Combate | Boss" else "Combate",
            introLines = encounter.introLines,
            playerName = encounter.player.name,
            enemyName = engine.monsterDisplayName(encounter.monster),
            playerHp = ProgressBarViewModel("HP", playerHp, playerStats.derived.hpMax),
            playerMp = ProgressBarViewModel("MP", playerMp, playerStats.derived.mpMax),
            enemyHp = ProgressBarViewModel("HP", enemyHp, enemyStats.derived.hpMax),
            logLines = logLines,
            options = listOf(
                ScreenOptionViewModel("1", "Atacar", GameAction.Attack),
                ScreenOptionViewModel("2", "Tentar fugir", GameAction.EscapeCombat)
            ),
            messages = messages
        )
    }

    fun presentCombatFallback(session: GameSession): ScreenViewModel {
        val encounter = session.pendingEncounter ?: return support.presentMissingState("Combate")
        val playerStats = engine.computePlayerStats(encounter.player, encounter.itemInstances)
        val enemyStats = engine.computeMonsterStats(encounter.monster)
        return CombatScreenViewModel(
            title = "Combate",
            introLines = encounter.introLines,
            playerName = encounter.player.name,
            enemyName = engine.monsterDisplayName(encounter.monster),
            playerHp = ProgressBarViewModel("HP", encounter.player.currentHp, playerStats.derived.hpMax),
            playerMp = ProgressBarViewModel("MP", encounter.player.currentMp, playerStats.derived.mpMax),
            enemyHp = ProgressBarViewModel("HP", enemyStats.derived.hpMax, enemyStats.derived.hpMax),
            logLines = listOf("Preparando combate..."),
            options = listOf(
                ScreenOptionViewModel("1", "Atacar", GameAction.Attack),
                ScreenOptionViewModel("2", "Tentar fugir", GameAction.EscapeCombat)
            ),
            messages = session.messages
        )
    }
}
