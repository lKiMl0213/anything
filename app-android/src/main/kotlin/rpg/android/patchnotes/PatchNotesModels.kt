package rpg.android.patchnotes

import kotlinx.serialization.Serializable

@Serializable
data class PatchNotesDocument(
    val currentVersion: String = "",
    val baselineRef: String? = null,
    val entries: List<PatchNotesEntry> = emptyList()
)

@Serializable
data class PatchNotesEntry(
    val version: String,
    val date: String = "",
    val novidades: List<String> = emptyList(),
    val melhorias: List<String> = emptyList(),
    val correcoes: List<String> = emptyList()
)
