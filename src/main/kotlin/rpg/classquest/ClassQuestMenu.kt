package rpg.classquest

import rpg.model.PlayerState

data class ClassQuestDynamicEntry(
    val label: String,
    val context: ClassQuestContext
)

data class ClassQuestMenuView(
    val context: ClassQuestContext,
    val title: String,
    val statusLabel: String,
    val canChoosePath: Boolean,
    val canCancel: Boolean,
    val lines: List<String>
)

class ClassQuestMenu(
    private val service: ClassQuestService
) {
    fun dynamicEntry(player: PlayerState): ClassQuestDynamicEntry? {
        val context = service.currentContext(player) ?: return null
        val progress = context.progress
        if (progress.status == ClassQuestStatus.COMPLETED) return null

        val label = when {
            canChoosePath(progress) -> {
                "(!) CLASSE (!) > Escolha seu caminho: ${context.definition.pathAName} ou ${context.definition.pathBName}"
            }
            progress.status == ClassQuestStatus.IN_PROGRESS -> {
                val pathLabel = progress.chosenPath?.let { service.pathName(context.definition.unlockType, it) } ?: "-"
                "(!) CLASSE (!) > Em andamento: $pathLabel (Etapa ${progress.currentStage}/4)"
            }
            else -> {
                "(!) CLASSE (!) > ${service.statusLabel(progress.status)}"
            }
        }
        return ClassQuestDynamicEntry(label = label, context = context)
    }

    fun view(player: PlayerState): ClassQuestMenuView? {
        val context = service.currentContext(player) ?: return null
        val progress = context.progress
        val title = "Quest de Classe - ${context.definition.className}"
        val statusLabel = service.statusLabel(progress.status)
        return ClassQuestMenuView(
            context = context,
            title = title,
            statusLabel = statusLabel,
            canChoosePath = canChoosePath(progress),
            canCancel = progress.status == ClassQuestStatus.IN_PROGRESS,
            lines = service.stageProgressLines(context)
        )
    }

    private fun canChoosePath(progress: ClassQuestProgress): Boolean {
        return (progress.status == ClassQuestStatus.AVAILABLE || progress.status == ClassQuestStatus.CANCELED) &&
            progress.chosenPath.isNullOrBlank()
    }
}

