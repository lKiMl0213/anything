package rpg.cli.input

import rpg.application.actions.GameAction
import rpg.presentation.model.CombatScreenViewModel
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenViewModel

class CliInputHandler(
    private val reader: CliInputReader = CliInputReader()
) {
    fun readAction(viewModel: ScreenViewModel): GameAction {
        return when (viewModel) {
            is MenuScreenViewModel -> readFromMenu(viewModel)
            is CombatScreenViewModel -> readFromCombat(viewModel)
        }
    }

    private fun readFromMenu(viewModel: MenuScreenViewModel): GameAction {
        while (true) {
            val input = reader.readLineOrThrow().lowercase()
            viewModel.options.firstOrNull { it.key.lowercase() == input }?.let { return it.action }
        }
    }

    private fun readFromCombat(viewModel: CombatScreenViewModel): GameAction {
        while (true) {
            val input = reader.readLineOrThrow().lowercase()
            viewModel.options.firstOrNull { it.key.lowercase() == input }?.let { return it.action }
        }
    }
}
