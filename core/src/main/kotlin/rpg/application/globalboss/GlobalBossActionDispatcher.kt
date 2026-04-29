package rpg.application.globalboss

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.actions.GameAction
import rpg.engine.GameEngine
import rpg.globalboss.config.GlobalBossCadence
import rpg.navigation.NavigationState

class GlobalBossActionDispatcher(
    private val engine: GameEngine,
    private val queryService: GlobalBossQueryService,
    private val commandService: GlobalBossCommandService,
    private val stateSupport: GameStateSupport
) {
    fun handle(session: GameSession, action: GameAction): GameActionResult? {
        return when (action) {
            GameAction.OpenGlobalBossMenu -> move(session, NavigationState.GlobalBossMenu)
            GameAction.OpenGlobalBossWeekly -> openByCadence(session, GlobalBossCadence.WEEKLY)
            GameAction.OpenGlobalBossMonthly -> openByCadence(session, GlobalBossCadence.MONTHLY)
            is GameAction.OpenGlobalBossMilestones -> openMilestones(session, action.eventId)
            is GameAction.StartGlobalBossRun -> startRun(session, action.eventId)
            is GameAction.AutoClearGlobalBossRun -> autoClear(session, action.eventId)
            is GameAction.BuyGlobalBossRunAttempt -> buyAttempt(session, action.eventId)
            is GameAction.ClaimGlobalBossMilestone -> claimMilestone(session, action.eventId, action.milestoneId)
            else -> null
        }
    }

    private fun move(session: GameSession, destination: NavigationState): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        return GameActionResult(
            session = session.copy(
                gameState = stateSupport.normalize(state),
                navigation = destination,
                selectedGlobalBossEventId = if (destination == NavigationState.GlobalBossEventDetail) {
                    session.selectedGlobalBossEventId
                } else {
                    null
                },
                messages = emptyList()
            )
        )
    }

    private fun openByCadence(session: GameSession, cadence: GlobalBossCadence): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val eventId = queryService.eventIdByCadence(cadence)
            ?: return GameActionResult(
                session.copy(
                    gameState = normalized,
                    navigation = NavigationState.GlobalBossMenu,
                    messages = listOf("Nenhum evento ${cadence.name.lowercase()} configurado.")
                )
            )
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.GlobalBossEventDetail,
                selectedGlobalBossEventId = eventId,
                messages = emptyList()
            )
        )
    }

    private fun openMilestones(session: GameSession, eventId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        return GameActionResult(
            session = session.copy(
                gameState = normalized,
                navigation = NavigationState.GlobalBossMilestones,
                selectedGlobalBossEventId = eventId,
                messages = emptyList()
            )
        )
    }

    private fun startRun(session: GameSession, eventId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val result = commandService.startRun(normalized, eventId) { encounter ->
            val stats = engine.computePlayerStats(encounter.player, encounter.itemInstances)
            engine.encounterText(encounter.monster, encounter.tier, stats)
        }
        val encounter = result.encounter
        if (!result.success || encounter == null) {
            return GameActionResult(
                session = session.copy(
                    gameState = result.state,
                    navigation = NavigationState.GlobalBossEventDetail,
                    selectedGlobalBossEventId = eventId,
                    messages = result.messages
                )
            )
        }
        return GameActionResult(
            session = session.copy(
                gameState = result.state,
                navigation = NavigationState.Combat,
                pendingEncounter = encounter,
                selectedGlobalBossEventId = eventId,
                messages = result.messages
            ),
            effect = GameEffect.LaunchCombat(encounter)
        )
    }

    private fun autoClear(session: GameSession, eventId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val result = commandService.autoClear(normalized, eventId)
        return GameActionResult(
            session = session.copy(
                gameState = result.state,
                navigation = NavigationState.GlobalBossEventDetail,
                selectedGlobalBossEventId = eventId,
                messages = result.messages
            )
        )
    }

    private fun buyAttempt(session: GameSession, eventId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val result = commandService.buyRunAttempt(normalized, eventId)
        return GameActionResult(
            session = session.copy(
                gameState = result.state,
                navigation = NavigationState.GlobalBossEventDetail,
                selectedGlobalBossEventId = eventId,
                messages = result.messages
            )
        )
    }

    private fun claimMilestone(session: GameSession, eventId: String, milestoneId: String): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val normalized = stateSupport.normalize(state)
        val result = commandService.claimMilestone(normalized, eventId, milestoneId)
        return GameActionResult(
            session = session.copy(
                gameState = result.state,
                navigation = NavigationState.GlobalBossMilestones,
                selectedGlobalBossEventId = eventId,
                messages = result.messages
            )
        )
    }
}
