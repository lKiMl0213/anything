package rpg.android.patchnotes

import android.util.Log
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import rpg.io.JsonStore

class PatchNotesService(
    dataRoot: Path,
    private val fallbackVersion: String
) {
    private companion object {
        private const val TAG = "PatchNotesService"
    }

    private val patchNotesPath = dataRoot.resolve("patchnotes").resolve("changelog.json")
    @Volatile
    private var cachedDocument: PatchNotesDocument? = null
    @Volatile
    private var lastLoadError: String? = null

    fun currentPath(): Path = patchNotesPath

    fun lastError(): String? = lastLoadError

    fun nextEntryToShow(lastSeenVersion: String?): PatchNotesEntry? {
        val document = loadDocument() ?: return null
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
        val document = loadDocument() ?: return null
        val currentVersion = document.currentVersion.takeIf { it.isNotBlank() } ?: fallbackVersion
        val entry = document.entries.firstOrNull { it.version.equals(currentVersion, ignoreCase = true) }
            ?: document.entries.firstOrNull()
            ?: return null
        return entry.copy(version = currentVersion)
    }

    private fun loadDocument(): PatchNotesDocument? {
        cachedDocument?.let { return it }
        lastLoadError = null
        if (!Files.exists(patchNotesPath)) {
            val message = "Arquivo de patchnotes não encontrado em: $patchNotesPath"
            lastLoadError = message
            Log.e(TAG, message)
            return null
        }

        val content = try {
            String(Files.readAllBytes(patchNotesPath), Charsets.UTF_8).removePrefix("\uFEFF")
        } catch (error: Exception) {
            val message = "Falha ao ler patchnotes em: $patchNotesPath"
            lastLoadError = "$message (${error.message ?: "erro desconhecido"})"
            Log.e(TAG, message, error)
            return null
        }

        val loaded = tryDecode(content)
        if (loaded == null) {
            if (lastLoadError.isNullOrBlank()) {
                lastLoadError = "Falha ao parsear JSON de patchnotes em: $patchNotesPath"
            }
            return null
        }
        if (loaded.entries.isEmpty()) {
            val message = "JSON de patchnotes carregado, mas sem entradas em: $patchNotesPath"
            lastLoadError = message
            Log.e(TAG, message)
            return null
        }

        Log.d(
            TAG,
            "Patchnotes carregado: path=$patchNotesPath currentVersion=${loaded.currentVersion} entries=${loaded.entries.size}"
        )
        cachedDocument = loaded
        return loaded
    }

    private fun tryDecode(content: String): PatchNotesDocument? {
        val modern = runCatching {
            JsonStore.json.decodeFromString<PatchNotesDocument>(content)
        }.getOrElse { modernError ->
            Log.w(TAG, "Falha ao parsear formato atual de patchnotes. Tentando legado.", modernError)
            null
        }
        if (modern != null) {
            return modern
        }

        val legacy = runCatching {
            JsonStore.json.decodeFromString<LegacyPatchNotesDocument>(content)
        }.getOrElse { legacyError ->
            val message = "Falha ao parsear patchnotes (formato atual e legado) em: $patchNotesPath"
            lastLoadError = "$message (${legacyError.message ?: "erro desconhecido"})"
            Log.e(TAG, message, legacyError)
            null
        } ?: return null

        return legacy.toModernDocument()
    }
}

@Serializable
private data class LegacyPatchNotesDocument(
    val currentVersion: String = "",
    val baselineRef: String? = null,
    val entries: List<LegacyPatchNotesEntry> = emptyList()
)

@Serializable
private data class LegacyPatchNotesEntry(
    val version: String = "",
    val date: String = "",
    val novidades: List<String>? = null,
    val melhorias: List<String>? = null,
    val correcoes: List<String>? = null
)

private fun LegacyPatchNotesDocument.toModernDocument(): PatchNotesDocument {
    val normalizedEntries = entries
        .mapNotNull { entry ->
            val version = entry.version.trim()
            if (version.isBlank()) {
                null
            } else {
                PatchNotesEntry(
                    version = version,
                    date = entry.date,
                    novidades = entry.novidades.orEmpty(),
                    melhorias = entry.melhorias.orEmpty(),
                    correcoes = entry.correcoes.orEmpty(),
                    sistemas = emptyList()
                )
            }
        }
    return PatchNotesDocument(
        currentVersion = currentVersion,
        baselineRef = baselineRef,
        entries = normalizedEntries
    )
}



