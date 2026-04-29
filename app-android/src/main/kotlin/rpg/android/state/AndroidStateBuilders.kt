package rpg.android.state

import rpg.application.character.AttributeRowView
import rpg.io.DataRepository
import rpg.model.Attributes

internal data class CharacterCreationDraft(
    val name: String,
    val selectedRaceId: String?,
    val selectedClassId: String?,
    val totalPoints: Int,
    val allocated: Map<String, Int>
) {
    val remainingPoints: Int
        get() = (totalPoints - allocated.values.sum()).coerceAtLeast(0)
}

internal object AndroidStateBuilders {
    private val attributeCodes = listOf("STR", "AGI", "DEX", "VIT", "INT", "SPR", "LUK")
    private val attributeLabels = mapOf(
        "STR" to "Forca",
        "AGI" to "Agilidade",
        "DEX" to "Destreza",
        "VIT" to "Vitalidade",
        "INT" to "Inteligencia",
        "SPR" to "Espirito",
        "LUK" to "Sorte"
    )

    fun initialCreationDraft(repo: DataRepository): CharacterCreationDraft {
        return CharacterCreationDraft(
            name = "",
            selectedRaceId = repo.races.values.firstOrNull()?.id,
            selectedClassId = repo.classes.values.firstOrNull()?.id,
            totalPoints = repo.character.baseAttributePoints,
            allocated = attributeCodes.associateWith { 0 }
        )
    }

    fun buildCreationUiState(
        repo: DataRepository,
        draft: CharacterCreationDraft,
        message: String? = null
    ): CharacterCreationUiState {
        val race = draft.selectedRaceId?.let(repo.races::get)
        val clazz = draft.selectedClassId?.let(repo.classes::get)
        val rows = attributeCodes.map { code ->
            CharacterCreationAttributeRowUi(
                code = code,
                label = attributeLabels[code] ?: code,
                raceBonus = readAttr(race?.bonuses?.attributes ?: Attributes(), code),
                classBonus = readAttr(clazz?.bonuses?.attributes ?: Attributes(), code),
                allocated = draft.allocated[code] ?: 0
            )
        }
        return CharacterCreationUiState(
            name = draft.name,
            races = repo.races.values.sortedBy { it.name }.map { SelectOption(it.id, it.name) },
            classes = repo.classes.values.sortedBy { it.name }.map { SelectOption(it.id, it.name) },
            selectedRaceId = draft.selectedRaceId,
            selectedClassId = draft.selectedClassId,
            pointsRemaining = draft.remainingPoints,
            attributes = rows,
            canConfirm = draft.selectedRaceId != null && draft.selectedClassId != null && draft.name.isNotBlank(),
            message = message
        )
    }

    fun toAttributes(values: Map<String, Int>): Attributes {
        return Attributes(
            str = (values["STR"] ?: 0).coerceAtLeast(0),
            agi = (values["AGI"] ?: 0).coerceAtLeast(0),
            dex = (values["DEX"] ?: 0).coerceAtLeast(0),
            vit = (values["VIT"] ?: 0).coerceAtLeast(0),
            `int` = (values["INT"] ?: 0).coerceAtLeast(0),
            spr = (values["SPR"] ?: 0).coerceAtLeast(0),
            luk = (values["LUK"] ?: 0).coerceAtLeast(0)
        )
    }

    fun ensurePending(rows: List<AttributeRowView>, pending: Map<String, Int>): Map<String, Int> {
        return if (pending.isEmpty()) rows.associate { it.code to 0 } else pending
    }

    fun buildAttributesUiState(
        rows: List<AttributeRowView>,
        pending: Map<String, Int>,
        unspentPoints: Int,
        messages: List<String>
    ): AttributeAllocationUiState {
        val remaining = (unspentPoints - pending.values.sum()).coerceAtLeast(0)
        val uiRows = rows.map { row ->
            AttributeAllocationRowUi(
                code = row.code,
                label = row.label,
                currentFinal = row.finalValue,
                currentBase = row.baseValue,
                equipmentBonus = row.equipmentBonus,
                classTalentBonus = row.classTalentBonus,
                temporaryBonus = row.temporaryBonus,
                pending = pending[row.code] ?: 0
            )
        }
        return AttributeAllocationUiState(
            pointsRemaining = remaining,
            rows = uiRows,
            canApply = pending.values.sum() > 0,
            messages = messages
        )
    }

    fun attributeCodes(): List<String> = attributeCodes

    private fun readAttr(attrs: Attributes, code: String): Int = when (code) {
        "STR" -> attrs.str
        "AGI" -> attrs.agi
        "DEX" -> attrs.dex
        "VIT" -> attrs.vit
        "INT" -> attrs.`int`
        "SPR" -> attrs.spr
        "LUK" -> attrs.luk
        else -> 0
    }
}
