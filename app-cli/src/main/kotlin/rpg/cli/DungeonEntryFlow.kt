package rpg.cli

import rpg.classquest.ClassQuestDungeonDefinition
import rpg.engine.GameEngine
import rpg.model.GameState

internal class DungeonEntryFlow(
    private val engine: GameEngine,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val clearRunEffects: (rpg.model.PlayerState) -> rpg.model.PlayerState,
    private val autoSave: (GameState) -> Unit,
    private val emit: (String) -> Unit
) {
    fun prepareRun(state: GameState, forceClassDungeon: Boolean): DungeonEntryResult? {
        val classDungeon = engine.classQuestService.activeDungeon(state.player)
        var classDungeonMode: ClassQuestDungeonDefinition? = null
        if (forceClassDungeon) {
            if (classDungeon == null) {
                emit("Mapa de classe indisponivel. Pegue e inicie a missao de classe primeiro.")
                return null
            }
            classDungeonMode = classDungeon
        } else if (classDungeon != null) {
            emit("\n=== DUNGEONS ===")
            emit("1. Dungeon Infinita")
            emit("2. Instancia de Classe (${classDungeon.pathName})")
            emit("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> Unit
                2 -> classDungeonMode = classDungeon
                null -> return null
            }
        }

        val tiers = engine.availableTiers(state.player)
        if (tiers.isEmpty()) {
            emit("Nivel insuficiente para entrar em qualquer tier.")
            return null
        }

        emit("\nEscolha o tier da dungeon:")
        tiers.forEachIndexed { index, tier ->
            emit("${index + 1}. ${tier.id} (min lvl ${tier.minLevel})")
        }
        emit("x. Voltar")
        val tierChoice = readMenuChoice("Escolha: ", 1, tiers.size) ?: return null
        val chosenTier = tiers[tierChoice - 1]
        if (!engine.canEnterTier(state.player, chosenTier)) {
            emit("Nivel insuficiente para este tier.")
            return null
        }

        if (classDungeonMode == null) {
            emit("\nVoce entrou na dungeon infinita (${chosenTier.id}).")
        } else {
            emit(
                "\nVoce entrou na instancia de classe de ${classDungeonMode.pathName} " +
                    "(tier ${chosenTier.id})."
            )
        }

        val clearedState = state.copy(player = clearRunEffects(state.player))
        autoSave(clearedState)
        return DungeonEntryResult(
            clearedState = clearedState,
            chosenTier = chosenTier,
            classDungeonMode = classDungeonMode
        )
    }
}

internal data class DungeonEntryResult(
    val clearedState: GameState,
    val chosenTier: rpg.model.MapTierDef,
    val classDungeonMode: ClassQuestDungeonDefinition?
)
