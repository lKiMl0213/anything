package rpg.application.creation

import rpg.model.Attributes

data class CharacterCreationDraft(
    val name: String,
    val raceId: String?,
    val classId: String?,
    val totalPoints: Int,
    val allocated: Attributes = Attributes()
) {
    val spentPoints: Int
        get() = allocated.str + allocated.agi + allocated.dex + allocated.vit + allocated.`int` + allocated.spr + allocated.luk

    val remainingPoints: Int
        get() = (totalPoints - spentPoints).coerceAtLeast(0)
}

data class CharacterCreationAttributeRow(
    val code: String,
    val label: String,
    val raceBonus: Int,
    val classBonus: Int,
    val allocated: Int
) {
    val finalValue: Int
        get() = raceBonus + classBonus + allocated
}
