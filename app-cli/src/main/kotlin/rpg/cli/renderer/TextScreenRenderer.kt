package rpg.cli.renderer

import kotlin.math.roundToInt
import rpg.presentation.model.CombatScreenViewModel
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ProgressBarViewModel
import rpg.presentation.model.ScreenViewModel

class TextScreenRenderer {
    fun render(viewModel: ScreenViewModel) {
        when (viewModel) {
            is MenuScreenViewModel -> renderMenu(viewModel)
            is CombatScreenViewModel -> renderCombat(viewModel)
        }
    }

    private fun renderMenu(viewModel: MenuScreenViewModel) {
        println("\n=== ${viewModel.title} ===")
        viewModel.subtitle?.takeIf { it.isNotBlank() }?.let { println(it) }
        viewModel.summary?.let { summary ->
            println("${summary.name} | Nivel ${summary.level}")
            println(summary.classLabel)
            println("${bar(summary.hp)} | ${bar(summary.mp)} | Ouro ${summary.gold}")
        }
        if (viewModel.messages.isNotEmpty()) {
            println()
            viewModel.messages.forEach { println(it) }
        }
        if (viewModel.bodyLines.isNotEmpty()) {
            println()
            viewModel.bodyLines.forEach { println(it) }
        }
        if (viewModel.options.isNotEmpty()) {
            println()
            viewModel.options.forEach { println("${it.key}. ${it.label}") }
        }
    }

    private fun renderCombat(viewModel: CombatScreenViewModel) {
        println("\n=== ${viewModel.title} ===")
        viewModel.introLines.forEach { println(it) }
        println("${viewModel.playerName} -> ${bar(viewModel.playerHp)} | ${bar(viewModel.playerMp)}")
        println("${viewModel.enemyName} -> ${bar(viewModel.enemyHp)}")
        if (viewModel.messages.isNotEmpty()) {
            println()
            viewModel.messages.forEach { println(it) }
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

    private fun bar(progress: ProgressBarViewModel): String {
        val ratio = if (progress.max <= 0.0) 0.0 else (progress.current / progress.max).coerceIn(0.0, 1.0)
        val filled = (ratio * 12.0).roundToInt().coerceIn(0, 12)
        val empty = 12 - filled
        return "${progress.label} [${"#".repeat(filled)}${"-".repeat(empty)}] ${progress.current.roundToInt()}/${progress.max.roundToInt()}"
    }
}
