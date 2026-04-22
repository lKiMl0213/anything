package rpg.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import rpg.cli.LegacyAttributeAllocationFlow
import rpg.cli.LegacyCharacterCreationFlow
import rpg.cli.model.AttrMeta
import rpg.creation.CharacterCreationPreviewService
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.io.JsonStore
import rpg.model.GameState

internal class SessionBridge(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val characterDef: rpg.model.CharacterDef,
    private val characterCreationPreview: CharacterCreationPreviewService,
    private val attributeMeta: List<AttrMeta>,
    private val readNonEmpty: (prompt: String) -> String,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val chooseSave: (label: String, options: List<Path>, nameOf: (Path) -> String) -> Path,
    private val format: (Double) -> String,
    private val applyTwoHandedLoadout: (equipped: MutableMap<String, String>) -> Unit,
    private val computePlayerStats: (
        player: rpg.model.PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> rpg.engine.ComputedStats,
    private val ensureSkillProgress: (player: rpg.model.PlayerState) -> rpg.model.PlayerState,
    private val synchronizeAchievements: (player: rpg.model.PlayerState) -> rpg.model.PlayerState,
    private val synchronizeClock: (GameState) -> GameState,
    private val normalizeLoadedState: (GameState) -> GameState,
    private val hubLoop: (state: GameState) -> GameState,
    private val notify: (String) -> Unit
) {
    fun runLegacySection(
        initialState: GameState,
        block: (GameState) -> GameState
    ): GameState? {
        return block(synchronizeClock(normalizeLoadedState(initialState)))
    }

    fun newGame(): GameState? {
        val allocationFlow = LegacyAttributeAllocationFlow(attributeMeta)
        return LegacyCharacterCreationFlow(
            repo = repo,
            engine = engine,
            characterDef = characterDef,
            characterCreationPreview = characterCreationPreview,
            readNonEmpty = readNonEmpty,
            readMenuChoice = readMenuChoice,
            format = format,
            allocateAttributesWithBonuses = allocationFlow::allocateAttributesWithBonuses,
            applyTwoHandedLoadout = applyTwoHandedLoadout,
            computePlayerStats = computePlayerStats,
            ensureSkillProgress = ensureSkillProgress,
            synchronizeAchievements = synchronizeAchievements,
            hubLoop = hubLoop
        ).newGame()
    }

    fun loadGame() {
        val saveDir = Paths.get("data", "saves")
        if (!Files.exists(saveDir)) {
            notify("Nenhum save encontrado.")
            return
        }

        val saves = Files.list(saveDir).use { stream ->
            val results = mutableListOf<Path>()
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().endsWith(".json") }
                .forEach { results.add(it) }
            results
        }

        if (saves.isEmpty()) {
            notify("Nenhum save encontrado.")
            return
        }

        val chosen = chooseSave("Save", saves) { it.fileName.toString() }
        val rawState = JsonStore.load<GameState>(chosen)
        val state = synchronizeClock(normalizeLoadedState(rawState))
        notify("Save carregado: ${chosen.fileName}")
        hubLoop(state)
    }
}
