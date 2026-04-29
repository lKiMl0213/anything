package rpg.cli

import kotlin.math.ceil
import kotlin.math.roundToInt
import rpg.application.production.ProductionTimedActionView
import rpg.cli.renderer.CliAnsiPalette

class ProductionTimedActionRunner(
    private val clearScreen: String = CliAnsiPalette.clearScreen
) {
    fun run(view: ProductionTimedActionView) {
        val totalSeconds = view.durationSeconds.coerceAtLeast(1.0)
        val stepMs = 125L
        val totalSteps = ((totalSeconds * 1000.0) / stepMs).roundToInt().coerceAtLeast(1)
        for (step in 0..totalSteps) {
            val progress = (step.toDouble() / totalSteps).coerceIn(0.0, 1.0)
            val filled = (progress * 20.0).roundToInt().coerceIn(0, 20)
            val remainingSeconds = ceil((1.0 - progress) * totalSeconds).toInt().coerceAtLeast(0)
            val bar = "#".repeat(filled) + "-".repeat(20 - filled)
            print(clearScreen)
            println("=== ${view.categoryLabel} ===")
            println("Acao: ${view.actionLabel}")
            println("Nivel: ${view.skillLevel} (${view.skillLabel})")
            println()
            println("Progresso [$bar] ${(progress * 100.0).roundToInt()}%")
            println("Tempo restante: ${remainingSeconds}s")
            if (step < totalSteps) {
                Thread.sleep(stepMs)
            }
        }
        println()
    }
}
