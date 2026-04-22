package rpg.application.progression

enum class QuestSection(val label: String) {
    CLASS_QUEST("Quest de Classe"),
    ACCEPTABLE_POOL("Pool Aceitavel"),
    ACCEPTED("Quests Aceitas"),
    DAILY("Quests Diarias"),
    WEEKLY("Quests Semanais"),
    MONTHLY("Quests Mensais")
}

data class QuestSectionSummaryView(
    val section: QuestSection,
    val label: String,
    val countLabel: String,
    val hasAlert: Boolean = false
)

data class QuestBoardOverviewView(
    val replaceSummary: String,
    val acceptedCountLabel: String,
    val poolCountLabel: String,
    val classQuestLabel: String? = null,
    val sections: List<QuestSectionSummaryView> = emptyList()
)

data class QuestListItemView(
    val instanceId: String,
    val title: String,
    val progressLabel: String,
    val statusLabel: String
)

data class QuestListView(
    val section: QuestSection,
    val title: String,
    val emptyMessage: String,
    val quests: List<QuestListItemView>
)

data class QuestDetailView(
    val section: QuestSection,
    val instanceId: String,
    val title: String,
    val detailLines: List<String>,
    val canAccept: Boolean = false,
    val canClaim: Boolean = false,
    val canCancel: Boolean = false,
    val canReplace: Boolean = false,
    val replaceRemaining: Int = 0
)

data class ClassQuestViewData(
    val title: String,
    val statusLabel: String,
    val detailLines: List<String>,
    val canChoosePath: Boolean,
    val pathAId: String? = null,
    val pathALabel: String? = null,
    val pathBId: String? = null,
    val pathBLabel: String? = null,
    val canCancel: Boolean = false
)
