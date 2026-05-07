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
    val date: String,
    val novidades: List<String>,
    val melhorias: List<String>,
    val correcoes: List<String>,
    val sistemas: List<String> = emptyList()
)
