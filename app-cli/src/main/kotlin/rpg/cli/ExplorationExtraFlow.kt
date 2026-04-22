package rpg.cli

import rpg.model.GameState

internal class ExplorationExtraFlow(
    private val synchronizeClock: (GameState) -> GameState,
    private val synchronizeClassQuest: (GameState) -> GameState,
    private val synchronizeAchievements: (GameState) -> GameState,
    private val hasClassMapUnlocked: (rpg.model.PlayerState) -> Boolean,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val enterDungeon: (state: GameState, forceClassDungeon: Boolean) -> GameState,
    private val emit: (String) -> Unit
) {
    fun openExploreMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            updated = synchronizeClock(updated)
            updated = synchronizeClassQuest(updated)
            updated = synchronizeAchievements(updated)

            emit("\n=== Explorar ===")
            emit("1. Dungeons")
            emit("2. Mapa aberto (futuro)")

            var option = 3
            val classMapOption = if (hasClassMapUnlocked(updated.player)) option++ else null
            if (classMapOption != null) {
                emit("$classMapOption. Mapa de classe")
            }
            emit("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, option - 1) ?: return updated
            when {
                choice == 1 -> updated = enterDungeon(updated, false)
                choice == 2 -> emit("Mapa aberto ainda nao esta disponivel.")
                classMapOption != null && choice == classMapOption -> updated = enterDungeon(updated, true)
            }
        }
    }
}
