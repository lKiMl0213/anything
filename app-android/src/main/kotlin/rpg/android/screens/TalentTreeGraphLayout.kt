package rpg.android.screens

import rpg.android.state.MenuActionPreviewUiModel
import rpg.android.state.TalentTreeGraphUiModel
import rpg.application.actions.GameAction
import rpg.presentation.model.ScreenOptionViewModel
import kotlin.math.roundToInt

internal enum class TalentNodeVisualState {
    AVAILABLE,
    LOCKED,
    MAXED,
    UNKNOWN
}

internal data class TalentNodeCardUi(
    val nodeId: String,
    val option: ScreenOptionViewModel,
    val displayLabel: String,
    val state: TalentNodeVisualState,
    val badgeLabel: String,
    val currentRank: Int,
    val maxRank: Int,
    val prerequisiteNodeIds: Set<String>,
    val blockedReason: String?
)

internal data class TalentTreeLayoutUi(
    val nodes: List<TalentPositionedNodeUi>,
    val edges: List<TalentDependencyEdgeUi>,
    val columnCount: Int,
    val depthCount: Int
)

internal data class TalentPositionedNodeUi(
    val nodeId: String,
    val card: TalentNodeCardUi,
    val depth: Int,
    val column: Int
)

internal data class TalentDependencyEdgeUi(
    val fromNodeId: String,
    val toNodeId: String
)

internal fun buildTalentNodeCards(
    options: List<ScreenOptionViewModel>,
    actionPreviews: Map<String, MenuActionPreviewUiModel>,
    graph: TalentTreeGraphUiModel?
): List<TalentNodeCardUi> {
    val inspectOptions = options.filter { it.action is GameAction.InspectTalentNode }
    if (inspectOptions.isEmpty()) return emptyList()

    val graphNodeById = graph?.nodes?.associateBy { it.nodeId }.orEmpty()
    return inspectOptions.map { option ->
        val nodeId = (option.action as? GameAction.InspectTalentNode)?.nodeId.orEmpty()
        val graphNode = graphNodeById[nodeId]
        val preview = actionPreviews[option.key]
        val fallbackState = inferTalentStateFromPreview(option, preview)
        val state = when {
            graphNode == null -> fallbackState
            graphNode.currentRank >= graphNode.maxRank -> TalentNodeVisualState.MAXED
            graphNode.canRankUp -> TalentNodeVisualState.AVAILABLE
            graphNode.blockedReason != null || graphNode.prerequisites.isNotEmpty() -> TalentNodeVisualState.LOCKED
            else -> fallbackState
        }
        val prereqs = graphNode?.prerequisites?.map { it.nodeId }?.toSet().orEmpty()
        val reqNames = graphNode?.prerequisites?.joinToString(", ") { "${it.nodeName} nv${it.minRank}" }.orEmpty()
        val blockedReason = when {
            graphNode == null -> null
            graphNode.currentRank >= graphNode.maxRank -> "No máximo"
            graphNode.canRankUp -> null
            reqNames.isNotBlank() && graphNode.blockedReason.isNullOrBlank() -> "Req: $reqNames"
            reqNames.isNotBlank() && !graphNode.blockedReason.isNullOrBlank() -> "Req: $reqNames | ${graphNode.blockedReason}"
            else -> graphNode.blockedReason
        }
        TalentNodeCardUi(
            nodeId = nodeId,
            option = option,
            displayLabel = stripAlertMarker(option.label),
            state = state,
            badgeLabel = badgeForState(state),
            currentRank = graphNode?.currentRank ?: 0,
            maxRank = graphNode?.maxRank ?: 1,
            prerequisiteNodeIds = prereqs,
            blockedReason = blockedReason
        )
    }
}

internal fun buildTalentTreeLayout(cards: List<TalentNodeCardUi>): TalentTreeLayoutUi {
    if (cards.isEmpty()) {
        return TalentTreeLayoutUi(
            nodes = emptyList(),
            edges = emptyList(),
            columnCount = 0,
            depthCount = 0
        )
    }
    val byNodeId = cards.associateBy { it.nodeId }
    val validNodeIds = byNodeId.keys
    val orderByNodeId = cards.mapIndexed { index, card -> card.nodeId to index }.toMap()
    val depthMemo = mutableMapOf<String, Int>()
    val visiting = mutableSetOf<String>()

    fun nodeDepth(nodeId: String): Int {
        if (nodeId.isBlank()) return 0
        depthMemo[nodeId]?.let { return it }
        if (!visiting.add(nodeId)) return 0
        val card = byNodeId[nodeId]
        val parents = card?.prerequisiteNodeIds?.filter { it in validNodeIds }.orEmpty()
        val depth = if (parents.isEmpty()) {
            0
        } else {
            parents.maxOf { parentId -> nodeDepth(parentId) + 1 }
        }
        visiting.remove(nodeId)
        depthMemo[nodeId] = depth
        return depth
    }

    cards.forEach { card -> nodeDepth(card.nodeId) }
    val cardsByDepth = cards.groupBy { card -> depthMemo[card.nodeId] ?: 0 }.toSortedMap()
    val columnByNodeId = mutableMapOf<String, Int>()

    cardsByDepth[0]
        ?.sortedBy { rootCard -> orderByNodeId[rootCard.nodeId] ?: Int.MAX_VALUE }
        ?.forEachIndexed { index, rootCard ->
            columnByNodeId[rootCard.nodeId] = index * 2
        }

    cardsByDepth.filterKeys { it > 0 }.forEach { (_, depthCards) ->
        val usedColumns = mutableSetOf<Int>()
        val sorted = depthCards.sortedWith(
            compareBy<TalentNodeCardUi> { card ->
                val parentColumns = card.prerequisiteNodeIds
                    .filter { parentId -> parentId in validNodeIds }
                    .mapNotNull { parentId -> columnByNodeId[parentId] }
                if (parentColumns.isEmpty()) {
                    Double.NEGATIVE_INFINITY
                } else {
                    parentColumns.average()
                }
            }.thenBy { card ->
                orderByNodeId[card.nodeId] ?: Int.MAX_VALUE
            }
        )
        sorted.forEachIndexed { index, card ->
            val parentColumns = card.prerequisiteNodeIds
                .filter { parentId -> parentId in validNodeIds }
                .mapNotNull { parentId -> columnByNodeId[parentId] }
            val desired = if (parentColumns.isEmpty()) index * 2 else parentColumns.average().roundToInt()
            val allocated = nearestFreeColumn(desired, usedColumns)
            usedColumns += allocated
            columnByNodeId[card.nodeId] = allocated
        }
    }

    val minColumn = columnByNodeId.values.minOrNull() ?: 0
    val maxColumn = columnByNodeId.values.maxOrNull() ?: 0
    val positionedNodes = cards.map { card ->
        TalentPositionedNodeUi(
            nodeId = card.nodeId,
            card = card,
            depth = depthMemo[card.nodeId] ?: 0,
            column = (columnByNodeId[card.nodeId] ?: 0) - minColumn
        )
    }.sortedWith(compareBy<TalentPositionedNodeUi> { it.depth }.thenBy { it.column })

    val edges = cards.flatMap { card ->
        card.prerequisiteNodeIds
            .filter { parentId -> parentId in validNodeIds }
            .map { parentId ->
                TalentDependencyEdgeUi(fromNodeId = parentId, toNodeId = card.nodeId)
            }
    }.distinct()

    return TalentTreeLayoutUi(
        nodes = positionedNodes,
        edges = edges,
        columnCount = (maxColumn - minColumn) + 1,
        depthCount = (positionedNodes.maxOfOrNull { it.depth } ?: 0) + 1
    )
}

private fun nearestFreeColumn(desired: Int, used: Set<Int>): Int {
    if (desired !in used) return desired
    var delta = 1
    while (true) {
        val left = desired - delta
        if (left !in used) return left
        val right = desired + delta
        if (right !in used) return right
        delta++
    }
}

private fun inferTalentStateFromPreview(
    option: ScreenOptionViewModel,
    preview: MenuActionPreviewUiModel?
): TalentNodeVisualState {
    val source = buildString {
        append(option.label.lowercase())
        preview?.let {
            append(' ')
            append(it.primaryLabel.lowercase())
            append(' ')
            append(it.lines.joinToString(" ").lowercase())
        }
    }
    return when {
        source.contains("bloque") || source.contains("requisit") || source.contains("indispon") -> TalentNodeVisualState.LOCKED
        source.contains("max") || source.contains("completo") -> TalentNodeVisualState.MAXED
        source.contains("aumentar nível") || source.contains("confirmar investir") || source.contains("custo") -> TalentNodeVisualState.AVAILABLE
        else -> TalentNodeVisualState.UNKNOWN
    }
}

private fun badgeForState(state: TalentNodeVisualState): String {
    return when (state) {
        TalentNodeVisualState.AVAILABLE -> "*"
        TalentNodeVisualState.LOCKED -> "x"
        TalentNodeVisualState.MAXED -> "+"
        TalentNodeVisualState.UNKNOWN -> "o"
    }
}

private fun stripAlertMarker(label: String): String {
    return label.replace("(!)", "").replace("( ! )", "").trim()
}




