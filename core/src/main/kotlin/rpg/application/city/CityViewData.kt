package rpg.application.city

data class TavernViewData(
    val restCost: Int,
    val sleepCost: Int,
    val purifyOneCost: Int,
    val purifyAllCost: Int,
    val restHealPct: Int,
    val hpCurrent: Double,
    val hpMax: Double,
    val mpCurrent: Double,
    val mpMax: Double,
    val hasRecoverableResources: Boolean,
    val debuffStacks: Int,
    val debuffMinutes: Double,
    val detailLines: List<String>,
    val canPurify: Boolean
)
