package rpg.application.character

data class AttributeRowView(
    val code: String,
    val label: String,
    val baseValue: Int,
    val equipmentBonus: Int,
    val classTalentBonus: Int,
    val temporaryBonus: Int,
    val finalValue: Int
)

data class AttributeDetailView(
    val code: String,
    val label: String,
    val detailLines: List<String>,
    val canAllocate: Boolean,
    val availablePoints: Int
)

data class TalentStageView(
    val stage: Int,
    val label: String,
    val treeId: String? = null,
    val spentPoints: Int = 0
)

data class TalentOverviewView(
    val totalSpent: Int,
    val totalEarned: Int,
    val totalAvailable: Int,
    val stages: List<TalentStageView>
)

data class TalentNodeListItemView(
    val nodeId: String,
    val name: String,
    val typeLabel: String,
    val rankLabel: String,
    val stateLabel: String,
    val prerequisitesLabel: String,
    val exclusiveLabel: String,
    val effectLabel: String
)

data class TalentTreeDetailView(
    val treeId: String,
    val title: String,
    val stageLabel: String,
    val pointsAvailable: Int,
    val unlockedSkillsLabel: String,
    val blockedSkillsLabel: String,
    val availableNodesLabel: String,
    val nodes: List<TalentNodeListItemView>
)

data class TalentNodeDetailView(
    val treeId: String,
    val nodeId: String,
    val title: String,
    val detailLines: List<String>,
    val canRankUp: Boolean,
    val blockedReason: String? = null,
    val nextCost: Int = 0
)
