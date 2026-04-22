package rpg.presentation

import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.application.actions.GameAction
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.PlayerSummaryViewModel
import rpg.presentation.model.ProgressBarViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class PresentationSupport(
    private val engine: GameEngine
) {
    fun presentMissingState(title: String): ScreenViewModel {
        return MenuScreenViewModel(
            title = title,
            bodyLines = listOf("Nenhum jogo carregado."),
            options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back))
        )
    }

    fun playerSummary(state: GameState): PlayerSummaryViewModel {
        val stats = engine.computePlayerStats(state.player, state.itemInstances)
        val classDef = engine.classSystem.classDef(state.player.classId)
        val secondClass = state.player.subclassId?.let(engine.classSystem::subclassDef)?.name ?: "-"
        val specialization = state.player.specializationId?.let(engine.classSystem::specializationDef)?.name ?: "-"
        val classLabel = "${classDef.name} | 2a: $secondClass | Esp: $specialization"
        return PlayerSummaryViewModel(
            name = state.player.name,
            level = state.player.level,
            classLabel = classLabel,
            gold = state.player.gold,
            hp = ProgressBarViewModel("HP", state.player.currentHp, stats.derived.hpMax),
            mp = ProgressBarViewModel("MP", state.player.currentMp, stats.derived.mpMax)
        )
    }

    fun formatSignedInt(value: Int): String = if (value >= 0) "+$value" else value.toString()
}
