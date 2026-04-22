package rpg.cli

import rpg.application.CombatFlowResult
import rpg.application.PendingEncounter
import rpg.application.actions.GameAction
import rpg.combat.CombatAction
import rpg.combat.CombatSnapshot
import rpg.combat.PlayerCombatController
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.navigation.NavigationState
import rpg.presentation.GamePresenter
import rpg.cli.input.CliInputHandler
import rpg.cli.renderer.TextScreenRenderer
import rpg.world.RunRoomType

class CliCombatFlowController(
    private val engine: GameEngine,
    private val presenter: GamePresenter,
    private val renderer: TextScreenRenderer,
    private val inputHandler: CliInputHandler
) {
    fun run(gameState: GameState, encounter: PendingEncounter): CombatFlowResult {
        val controller = ModularCombatController(encounter)
        val displayName = engine.monsterDisplayName(encounter.monster)
        val result = engine.combatEngine.runBattle(
            playerState = encounter.player,
            itemInstances = encounter.itemInstances,
            monster = encounter.monster,
            tier = encounter.tier,
            displayName = displayName,
            controller = controller,
            eventLogger = { message -> controller.onCombatEvent(message) }
        )

        var updatedState = gameState.copy(
            player = result.playerAfter,
            itemInstances = result.itemInstances
        )

        return when {
            result.escaped -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Exploration,
                messages = listOf("Voce fugiu do combate.") + controller.finalLogLines()
            )

            !result.victory -> CombatFlowResult(
                gameState = updatedState.copy(currentRun = null),
                navigation = NavigationState.Hub,
                messages = listOf(
                    "Voce foi derrotado.",
                    "O tratamento completo de derrota ainda permanece no fluxo legado nesta etapa."
                ) + controller.finalLogLines()
            )

            else -> {
                val levelBefore = result.playerAfter.level
                val victory = engine.resolveVictory(
                    player = result.playerAfter,
                    itemInstances = result.itemInstances,
                    monster = encounter.monster,
                    tier = encounter.tier,
                    collectToLoot = false
                )
                val advancedRun = engine.advanceRun(
                    run = encounter.run,
                    bossDefeated = encounter.isBoss,
                    clearedRoomType = if (encounter.isBoss) RunRoomType.BOSS else RunRoomType.MONSTER,
                    victoryInRoom = true
                )
                updatedState = updatedState.copy(
                    player = victory.player,
                    itemInstances = victory.itemInstances,
                    currentRun = advancedRun
                )
                val rewardLines = mutableListOf(
                    "$displayName foi derrotado!",
                    "Ganhou ${victory.xpGain} XP e ${victory.goldGain} ouro."
                )
                if (victory.player.level > levelBefore) {
                    rewardLines += "Level up! Agora voce esta no nivel ${victory.player.level}."
                }
                victory.dropOutcome.itemInstance?.let { rewardLines += "Drop: ${it.name}." }
                if (victory.dropOutcome.itemInstance == null && victory.dropOutcome.itemId != null) {
                    rewardLines += "Drop: ${victory.dropOutcome.itemId} x${victory.dropOutcome.quantity.coerceAtLeast(1)}."
                }
                CombatFlowResult(
                    gameState = updatedState,
                    navigation = NavigationState.Exploration,
                    messages = rewardLines + controller.finalLogLines()
                )
            }
        }
    }

    private inner class ModularCombatController(
        private val encounter: PendingEncounter
    ) : PlayerCombatController {
        private val history = ArrayDeque<String>()
        private val historyLimit = 8

        fun onCombatEvent(message: String) {
            if (message.isBlank()) return
            history += message
            while (history.size > historyLimit) {
                history.removeFirst()
            }
        }

        fun finalLogLines(): List<String> = history.toList()

        override fun pollAction(snapshot: CombatSnapshot): CombatAction? {
            val viewModel = presenter.presentCombat(
                encounter = encounter,
                logLines = history.toList(),
                messages = emptyList(),
                playerHp = snapshot.player.currentHp,
                playerMp = snapshot.player.currentMp,
                enemyHp = snapshot.monsterHp
            )
            renderer.render(viewModel)
            return when (inputHandler.readAction(viewModel)) {
                GameAction.Attack -> CombatAction.Attack()
                GameAction.EscapeCombat -> CombatAction.Escape
                else -> CombatAction.Attack()
            }
        }
    }
}
