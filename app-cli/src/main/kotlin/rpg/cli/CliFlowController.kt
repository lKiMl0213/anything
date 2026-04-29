package rpg.cli

import rpg.application.GameActionHandler
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.cli.input.CliInputHandler
import rpg.cli.renderer.TextScreenRenderer
import rpg.io.DataRepository
import rpg.presentation.GamePresenter

class CliFlowController(
    repo: DataRepository,
    private val actionHandler: GameActionHandler = GameActionHandler(repo),
    private val renderer: TextScreenRenderer = TextScreenRenderer(),
    private val inputHandler: CliInputHandler = CliInputHandler()
) {
    private val presenter = GamePresenter(
        engine = actionHandler.engine(),
        creationQueryService = actionHandler.creationQueryService(),
        inventoryQueryService = actionHandler.inventoryQueryService(),
        characterQueryService = actionHandler.characterQueryService(),
        questQueryService = actionHandler.questQueryService(),
        achievementQueryService = actionHandler.achievementQueryService(),
        cityQueryService = actionHandler.cityQueryService(),
        productionQueryService = actionHandler.productionQueryService(),
        shopQueryService = actionHandler.shopQueryService()
    )
    private val combatFlowController = CliCombatFlowController(
        engine = actionHandler.engine(),
        repo = repo,
        applyBattleResolvedAchievement = actionHandler::applyBattleResolvedAchievement,
        applyGoldEarnedAchievement = actionHandler::applyGoldEarnedAchievement,
        applyDeathAchievement = actionHandler::applyDeathAchievement
    )

    fun run() {
        var session = GameSession()
        try {
            while (!session.exitRequested) {
                val viewModel = presenter.present(session)
                renderer.render(viewModel)
                val action = inputHandler.readAction(viewModel)
                val result = actionHandler.handle(session, action)
                session = applyEffect(result.session, result.effect)
            }
        } catch (_: IllegalStateException) {
            return
        }
    }

    private fun applyEffect(session: GameSession, effect: GameEffect): GameSession {
        return when (effect) {
            GameEffect.None -> session
            is GameEffect.LaunchCombat -> {
                val state = session.gameState ?: return session
                val outcome = combatFlowController.run(state, effect.encounter)
                actionHandler.applyCombatResult(session, outcome)
            }
        }
    }
}
