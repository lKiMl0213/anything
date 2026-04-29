// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.io.DataRepository
import rpg.model.GameState

class LegacyGameCli(repo: DataRepository) {
    private val runtime = LegacyCliRuntime(repo)

    fun run() = runtime.run()

    fun runNewGameFlow(): GameState? = runtime.runNewGameFlow()

    fun runHubFromState(initialState: GameState): GameState? {
        return runtime.runHubFromState(initialState)
    }

    fun runExplorationFromState(initialState: GameState): GameState? {
        return runtime.runExplorationFromState(initialState)
    }

    fun runProductionFromState(initialState: GameState): GameState? {
        return runtime.runProductionFromState(initialState)
    }

    fun runCityFromState(initialState: GameState): GameState? {
        return runtime.runCityFromState(initialState)
    }
}
