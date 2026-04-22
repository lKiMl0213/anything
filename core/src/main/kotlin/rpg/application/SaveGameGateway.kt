package rpg.application

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import rpg.io.JsonStore
import rpg.model.GameState

class SaveGameGateway(
    private val saveDir: Path = Paths.get("data", "saves")
) {
    fun listSaves(): List<Path> {
        if (!Files.exists(saveDir)) return emptyList()
        return Files.list(saveDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().endsWith(".json") }
                .sorted(compareBy<Path> { it.fileName.toString().lowercase() })
                .toList()
        }
    }

    fun load(path: Path): GameState = JsonStore.load(path)

    fun save(path: Path, state: GameState): Path {
        JsonStore.save(path, state.copy(currentRun = null))
        return path
    }

    fun saveAutosave(state: GameState): Path {
        val path = saveDir.resolve("autosave.json")
        return save(path, state)
    }
}
