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
    private val legacyCli: LegacyGameCli = LegacyGameCli(repo),
    private val actionHandler: GameActionHandler = GameActionHandler(repo),
    private val renderer: TextScreenRenderer = TextScreenRenderer(),
    private val inputHandler: CliInputHandler = CliInputHandler()
) {
    private val presenter = GamePresenter(
        engine = actionHandler.engine(),
        inventoryQueryService = actionHandler.inventoryQueryService(),
        characterQueryService = actionHandler.characterQueryService(),
        questQueryService = actionHandler.questQueryService(),
        achievementQueryService = actionHandler.achievementQueryService(),
        cityQueryService = actionHandler.cityQueryService()
    )
    private val combatFlowController = CliCombatFlowController(
        engine = actionHandler.engine(),
        presenter = presenter,
        renderer = renderer,
        inputHandler = inputHandler
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
            GameEffect.LaunchLegacyNewGame -> {
                val updatedState = legacyCli.runNewGameFlow()
                actionHandler.applyLegacyNewGameReturn(session, updatedState)
            }
            is GameEffect.LaunchLegacyExploration -> {
                val updatedState = legacyCli.runExplorationFromState(effect.state)
                actionHandler.applyLegacyReturn(session, updatedState)
            }
            is GameEffect.LaunchLegacyProduction -> {
                val updatedState = legacyCli.runProductionFromState(effect.state)
                actionHandler.applyLegacyReturn(session, updatedState)
            }
            is GameEffect.LaunchLegacyCity -> {
                val updatedState = legacyCli.runCityFromState(effect.state)
                actionHandler.applyLegacyReturn(session, updatedState)
            }
            is GameEffect.LaunchCombat -> {
                val state = session.gameState ?: return session
                val outcome = combatFlowController.run(state, effect.encounter)
                actionHandler.applyCombatResult(session, outcome)
            }
        }
    }
}
