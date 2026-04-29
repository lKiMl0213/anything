// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.model.GameState

internal class LegacyCliRuntime(private val repo: rpg.io.DataRepository) {
    private val support by lazy { LegacyCliRuntimeSupportContext(repo) }
    private val inventoryProgressionFlows by lazy { LegacyCliRuntimeInventoryProgressionFlows(support) }
    private val dungeonFlows by lazy { LegacyCliRuntimeDungeonFlows(support, inventoryProgressionFlows) }
    private val sessionFlows by lazy {
        LegacyCliRuntimeSessionFlows(
            support = support,
            inventoryProgressionFlows = inventoryProgressionFlows,
            dungeonFlows = dungeonFlows
        )
    }

    fun run() {
        println("=== RPG TXT ===")
        try {
            while (true) {
                println("\n1. Novo jogo")
                println("2. Carregar jogo")
                println("x. Sair")
                when (support.ioSupport.readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> sessionFlows.newGame()
                    2 -> sessionFlows.loadGame()
                    null -> return
                }
            }
        } catch (_: LegacyCliRuntimeSupportContext.InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
        }
    }

    fun runNewGameFlow(): GameState? {
        return try {
            sessionFlows.newGame()
        } catch (_: LegacyCliRuntimeSupportContext.InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
            null
        }
    }

    fun runExplorationFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, dungeonFlows.explorationFlow::openExploreMenu)
    }

    fun runHubFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, sessionFlows.hubFlow::hubLoop)
    }

    fun runProductionFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, inventoryProgressionFlows.productionFlow::openProductionMenu)
    }

    fun runCityFromState(initialState: GameState): GameState? {
        return runLegacySection(initialState, inventoryProgressionFlows.cityServicesFlow::openCityMenu)
    }

    private fun runLegacySection(
        initialState: GameState,
        block: (GameState) -> GameState
    ): GameState? {
        return try {
            sessionFlows.runLegacySection(initialState, block)
        } catch (_: LegacyCliRuntimeSupportContext.InputClosedException) {
            println("\nEntrada encerrada. Encerrando jogo.")
            null
        }
    }
}
