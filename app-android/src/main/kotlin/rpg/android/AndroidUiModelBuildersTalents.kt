package rpg.android

import rpg.android.state.TalentTreeGraphUiModel
import rpg.android.state.TalentTreeNodeUiModel
import rpg.android.state.TalentTreePrerequisiteUiModel
import rpg.application.GameSession
import rpg.model.GameState

internal fun buildTalentTreeGraphUi(
    session: GameSession,
    state: GameState,
    deps: RuntimeDeps
): TalentTreeGraphUiModel? {
    val treeId = session.selectedTalentTreeId ?: return null
    val detail = deps.characterQueryService.talentTreeDetail(state, treeId) ?: return null
    return TalentTreeGraphUiModel(
        title = detail.title,
        stageLabel = detail.stageLabel,
        pointsAvailable = detail.pointsAvailable,
        nodes = detail.nodes.map { node ->
            TalentTreeNodeUiModel(
                nodeId = node.nodeId,
                name = node.name,
                currentRank = node.currentRank,
                maxRank = node.maxRank,
                canRankUp = node.canRankUp,
                blockedReason = node.blockedReason,
                prerequisites = node.prerequisites.map { prerequisite ->
                    TalentTreePrerequisiteUiModel(
                        nodeId = prerequisite.nodeId,
                        nodeName = prerequisite.nodeName,
                        minRank = prerequisite.minRank
                    )
                }
            )
        }
    )
}
