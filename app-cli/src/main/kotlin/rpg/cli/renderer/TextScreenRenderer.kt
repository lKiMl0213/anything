package rpg.cli.renderer

import kotlin.math.roundToInt
import rpg.presentation.model.CombatScreenViewModel
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ProgressBarViewModel
import rpg.presentation.model.ScreenViewModel

class TextScreenRenderer {
    private val colorEnabled: Boolean = System.getenv("NO_COLOR").isNullOrBlank()

    fun render(viewModel: ScreenViewModel) {
        when (viewModel) {
            is MenuScreenViewModel -> renderMenu(viewModel)
            is CombatScreenViewModel -> renderCombat(viewModel)
        }
    }

    private fun renderMenu(viewModel: MenuScreenViewModel) {
        clearTerminalForMenu()
        println("\n=== ${colorize(viewModel.title, CliAnsiPalette.header)} ===")
        viewModel.subtitle?.takeIf { it.isNotBlank() }?.let { println(colorize(it, CliAnsiPalette.subtitle)) }
        viewModel.summary?.let { summary ->
            println("${colorize(summary.name, CliAnsiPalette.name)} | Nivel ${colorize(summary.level.toString(), CliAnsiPalette.level)}")
            println(summary.classLabel)
            println(
                "${bar(summary.hp, CliAnsiPalette.hp)} | " +
                    "${bar(summary.mp, CliAnsiPalette.mp)} | " +
                    "Ouro ${colorize(summary.gold.toString(), CliAnsiPalette.gold)}"
            )
        }
        if (viewModel.messages.isNotEmpty()) {
            println()
            viewModel.messages.forEach { println(colorizeMessage(it)) }
        }
        if (viewModel.bodyLines.isNotEmpty()) {
            println()
            viewModel.bodyLines.forEach { println(colorizeBodyLine(it)) }
        }
        if (viewModel.options.isNotEmpty()) {
            println()
            viewModel.options.forEach { option ->
                val keyColor = if (option.key.equals("x", ignoreCase = true)) CliAnsiPalette.muted else CliAnsiPalette.info
                println("${colorize(option.key, keyColor)}. ${colorizeBodyLine(option.label)}")
            }
        }
    }

    private fun renderCombat(viewModel: CombatScreenViewModel) {
        println("\n=== ${colorize(viewModel.title, CliAnsiPalette.header)} ===")
        viewModel.introLines.forEach { println(it) }
        println("${viewModel.playerName} -> ${bar(viewModel.playerHp, CliAnsiPalette.hp)} | ${bar(viewModel.playerMp, CliAnsiPalette.mp)}")
        println("${viewModel.enemyName} -> ${bar(viewModel.enemyHp, CliAnsiPalette.danger)}")
        if (viewModel.messages.isNotEmpty()) {
            println()
            viewModel.messages.forEach { println(colorizeMessage(it)) }
        }
        println()
        println("Log:")
        if (viewModel.logLines.isEmpty()) {
            println("-")
        } else {
            viewModel.logLines.forEach { println("- $it") }
        }
        println()
        viewModel.options.forEach { println("${it.key}. ${it.label}") }
    }

    private fun bar(progress: ProgressBarViewModel, color: String): String {
        val ratio = if (progress.max <= 0.0) 0.0 else (progress.current / progress.max).coerceIn(0.0, 1.0)
        val filled = (ratio * 12.0).roundToInt().coerceIn(0, 12)
        val empty = 12 - filled
        return "${progress.label} ${colorize("[${"#".repeat(filled)}${"-".repeat(empty)}]", color)} ${progress.current.roundToInt()}/${progress.max.roundToInt()}"
    }

    private fun clearTerminalForMenu() {
        print(CliAnsiPalette.clearScreen)
    }

    private fun colorizeMessage(message: String): String {
        val lower = message.lowercase()
        val color = when {
            lower.contains("insuficiente") ||
                lower.contains("invalido") ||
                lower.contains("indispon") ||
                lower.contains("falha") ||
                lower.contains("erro") ||
                lower.contains("bloquead") -> CliAnsiPalette.danger

            lower.contains("sucesso") ||
                lower.contains("conclu") ||
                lower.contains("ganhou") ||
                lower.contains("salv") ||
                lower.contains("liberad") ||
                lower.contains("level up") -> CliAnsiPalette.success

            else -> CliAnsiPalette.warning
        }
        return colorize(withAlertColor(message), color)
    }

    private fun colorizeBodyLine(line: String): String {
        if (line.isBlank()) return line
        val normalized = withAlertColor(line)
        val ingredientRegex = Regex("""(.*?possui\s+)(\d+)(\s*/\s*precisa\s+)(\d+)(.*)""", RegexOption.IGNORE_CASE)
        val ingredientMatch = ingredientRegex.find(normalized)
        if (ingredientMatch != null) {
            val owned = ingredientMatch.groupValues[2].toIntOrNull() ?: 0
            val needed = ingredientMatch.groupValues[4].toIntOrNull() ?: 0
            return if (owned < needed) colorize(normalized, CliAnsiPalette.danger) else normalized
        }
        val lower = normalized.lowercase()
        return when {
            lower.contains("indisponivel") -> colorize(normalized, CliAnsiPalette.danger)
            lower.startsWith("clock sistema:") -> colorize(normalized, CliAnsiPalette.info)
            lower.startsWith("xp:") -> colorize(normalized, CliAnsiPalette.level)
            lower.startsWith("cash:") -> colorize(normalized, CliAnsiPalette.cash)
            lower == "regen natural:" -> colorize(normalized, CliAnsiPalette.info)
            lower.startsWith("hp cheio em") -> colorize(normalized, CliAnsiPalette.danger)
            lower.startsWith("mp cheio em") -> colorize(normalized, CliAnsiPalette.mp)
            lower.startsWith("seu hp esta cheio") -> colorize(normalized, CliAnsiPalette.success)
            lower.startsWith("seu mp esta cheio") -> colorize(normalized, CliAnsiPalette.success)
            lower.contains("disponivel") -> colorize(normalized, CliAnsiPalette.success)
            lower.startsWith("regen:") -> colorize(normalized, CliAnsiPalette.info)
            else -> normalized
        }
    }

    private fun withAlertColor(text: String): String {
        return text.replace("(!)", colorize("(!)", CliAnsiPalette.warning))
    }

    private fun colorize(text: String, color: String): String {
        if (!colorEnabled || text.isBlank()) return text
        return "$color$text${CliAnsiPalette.reset}"
    }
}
