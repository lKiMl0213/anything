package rpg.android.patchnotes

import java.nio.file.Files
import java.nio.file.Path
import rpg.io.JsonStore

class PatchNotesService(
    dataRoot: Path,
    private val fallbackVersion: String
) {
    private val patchNotesPath = dataRoot.resolve("patchnotes").resolve("changelog.json")
    @Volatile
    private var cachedDocument: PatchNotesDocument? = null

    fun nextEntryToShow(lastSeenVersion: String?): PatchNotesEntry? {
        val document = loadDocument()
        if (document == null) {
            if (fallbackVersion.equals(lastSeenVersion?.trim(), ignoreCase = true)) return null
            return defaultEntry(fallbackVersion)
        }
        val currentVersion = document.currentVersion.takeIf { it.isNotBlank() } ?: fallbackVersion
        if (currentVersion.equals(lastSeenVersion?.trim(), ignoreCase = true)) {
            return null
        }
        val entry = document.entries.firstOrNull { it.version.equals(currentVersion, ignoreCase = true) }
            ?: document.entries.firstOrNull()
            ?: return null
        return entry.copy(version = currentVersion)
    }

    fun currentEntry(): PatchNotesEntry? {
        val document = loadDocument()
        if (document == null) {
            return defaultEntry(fallbackVersion)
        }
        val currentVersion = document.currentVersion.takeIf { it.isNotBlank() } ?: fallbackVersion
        val entry = document.entries.firstOrNull { it.version.equals(currentVersion, ignoreCase = true) }
            ?: document.entries.firstOrNull()
            ?: defaultEntry(currentVersion)
        return entry.copy(version = currentVersion)
    }

    private fun loadDocument(): PatchNotesDocument? {
        cachedDocument?.let { return it }
        if (!Files.exists(patchNotesPath)) return null
        val loaded = runCatching { JsonStore.load<PatchNotesDocument>(patchNotesPath) }.getOrNull() ?: return null
        cachedDocument = loaded
        return loaded
    }

    private fun defaultEntry(version: String): PatchNotesEntry {
        return PatchNotesEntry(
            version = version,
            date = "",
            novidades = listOf("Atualizacao de conteudo e estabilidade."),
            melhorias = listOf("Melhorias gerais de interface e usabilidade."),
            correcoes = listOf("Correcao de bugs reportados por testers.")
        )
    }

}
