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
        val document = loadDocument() ?: return null
        val currentVersion = document.currentVersion.takeIf { it.isNotBlank() } ?: fallbackVersion
        if (!isVersionNewer(currentVersion, lastSeenVersion)) {
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
        if (!Files.exists(patchNotesPath)) return null
        val loaded = runCatching { JsonStore.load<PatchNotesDocument>(patchNotesPath) }.getOrNull() ?: return null
        cachedDocument = loaded
        return loaded
    }

    private fun isVersionNewer(current: String, seen: String?): Boolean {
        val normalizedCurrent = current.trim()
        if (normalizedCurrent.isBlank()) return false
        val normalizedSeen = seen?.trim().orEmpty()
        if (normalizedSeen.isBlank()) return true
        if (normalizedCurrent.equals(normalizedSeen, ignoreCase = true)) return false
        val diff = compareVersionTokens(normalizedCurrent, normalizedSeen)
        return if (diff == 0) {
            !normalizedCurrent.equals(normalizedSeen, ignoreCase = true)
        } else {
            diff > 0
        }
    }

    private fun compareVersionTokens(a: String, b: String): Int {
        val tokensA = a.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
        val tokensB = b.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
        val max = maxOf(tokensA.size, tokensB.size)
        for (index in 0 until max) {
            val left = tokensA.getOrNull(index) ?: "0"
            val right = tokensB.getOrNull(index) ?: "0"
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }
}
