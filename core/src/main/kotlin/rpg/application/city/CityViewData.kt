package rpg.application.city

data class TavernViewData(
    val restCost: Int,
    val sleepCost: Int,
    val purifyOneCost: Int,
    val purifyAllCost: Int,
    val debuffStacks: Int,
    val detailLines: List<String>,
    val canPurify: Boolean
)
