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
            viewModel.options.firstOrNull { it.key.lowercase() == input }?.let { option ->
                return adaptActionFromMenu(option.action, option.label, viewModel)
            }
        }
    }

    private fun readFromCombat(viewModel: CombatScreenViewModel): GameAction {
        while (true) {
            val input = reader.readLineOrThrow().lowercase()
            viewModel.options.firstOrNull { it.key.lowercase() == input }?.let { return it.action }
        }
    }

    private fun adaptActionFromMenu(
        action: GameAction,
        optionLabel: String,
        viewModel: MenuScreenViewModel
    ): GameAction {
        return when (action) {
            GameAction.CycleCharacterCreationName -> promptCharacterName()
            is GameAction.IncreaseCharacterCreationAttribute -> {
                if (viewModel.title.equals("Distribuicao de Atributos", ignoreCase = true)) {
                    action
                } else {
                    promptCreationAttributeAllocation(action.code, viewModel)
                }
            }
            is GameAction.AllocateAttributePoint -> promptAttributeAllocation(action.code, optionLabel)
            else -> action
        }
    }

    private fun promptCharacterName(): GameAction {
        while (true) {
            val input = reader.readLineOrThrow("Digite o nome do personagem: ").trim()
            if (input.isNotEmpty()) {
                return GameAction.SetCharacterCreationName(input)
            }
            println("Nome invalido. Digite pelo menos 1 caractere.")
        }
    }

    private fun promptCreationAttributeAllocation(
        code: String,
        viewModel: MenuScreenViewModel
    ): GameAction {
        val displayLabel = parseCreationAttributeDisplayLabel(code, viewModel)
        val remaining = parseCreationRemainingPoints(viewModel)
        val currentAllocated = parseCurrentAllocated(code, viewModel)
        val maxAllowed = (remaining + currentAllocated).coerceAtLeast(0)
        while (true) {
            val input = reader.readLineOrThrow("Digite o valor que deseja alocar em $displayLabel: ").trim()
            val amount = input.toIntOrNull()
            if (amount == null || amount < 0 || amount > maxAllowed) {
                println("Valor invalido. Informe um numero entre 0 e $maxAllowed.")
                continue
            }
            return GameAction.SetCharacterCreationAttribute(code, amount)
        }
    }

    private fun promptAttributeAllocation(code: String, optionLabel: String): GameAction {
        val maxPoints = parseAvailablePointsFromOption(optionLabel).coerceAtLeast(1)
        while (true) {
            val input = reader.readLineOrThrow("Digite quantos pontos deseja alocar: ").trim()
            val amount = input.toIntOrNull()
            if (amount == null || amount <= 0 || amount > maxPoints) {
                println("Valor invalido. Informe um numero entre 1 e $maxPoints.")
                continue
            }
            return GameAction.AllocateAttributePoints(code, amount)
        }
    }

    private fun parseCreationRemainingPoints(viewModel: MenuScreenViewModel): Int {
        val line = viewModel.bodyLines.firstOrNull { it.startsWith("Pontos restantes:", ignoreCase = true) }
            ?: return 0
        return line.substringAfter(":").trim().toIntOrNull() ?: 0
    }

    private fun parseCurrentAllocated(code: String, viewModel: MenuScreenViewModel): Int {
        val normalizedCode = code.uppercase()
        val regex = Regex("""^\d+\.\s*$normalizedCode\s*\([^)]+\)\s*->\s*[-\d]+\s*\[alocado\s*(\d+)\]$""", RegexOption.IGNORE_CASE)
        val numberedLine = viewModel.bodyLines.firstOrNull { regex.matches(it.trim()) }
        if (numberedLine != null) {
            return regex.find(numberedLine.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        }
        val detailRegex = Regex("""\b$normalizedCode\b.*\[\s*alocado\s*(\d+)\s*\]""", RegexOption.IGNORE_CASE)
        val detailLine = viewModel.bodyLines.firstOrNull { detailRegex.containsMatchIn(it.trim()) }
        if (detailLine != null) {
            return detailRegex.find(detailLine.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        }
        return 0
    }

    private fun parseAvailablePointsFromOption(optionLabel: String): Int {
        val regex = Regex("""\((\d+)\s+dispon""", RegexOption.IGNORE_CASE)
        return regex.find(optionLabel)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    private fun parseCreationAttributeDisplayLabel(code: String, viewModel: MenuScreenViewModel): String {
        val title = viewModel.title.trim()
        if (title.startsWith(code, ignoreCase = true) && title.contains("(") && title.contains(")")) {
            return title
        }
        val regex = Regex("""\b${code.uppercase()}\s*\([^)]+\)""", RegexOption.IGNORE_CASE)
        val match = viewModel.bodyLines.firstNotNullOfOrNull { regex.find(it)?.value }
        return match ?: code.uppercase()
    }
}
