package rpg.android.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rpg.android.state.MenuActionPreviewUiModel
import rpg.application.actions.GameAction

private val TalentGraphPadding = 16.dp
private val TalentNodeWidth = 116.dp
private val TalentNodeHeight = 86.dp
private val TalentNodeHorizontalGap = 22.dp
private val TalentNodeVerticalGap = 30.dp
private val TalentNodeIconSize = 30.dp

@Composable
internal fun TalentTreeGraph(
    cards: List<TalentNodeCardUi>,
    actionPreviews: Map<String, MenuActionPreviewUiModel>,
    onAction: (GameAction) -> Unit,
    onPreview: (MenuActionPreviewUiModel) -> Unit
) {
    val layout = remember(cards) { buildTalentTreeLayout(cards) }
    if (layout.nodes.isEmpty()) {
        Text("Nenhum talento disponivel para esta fase.")
        return
    }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val verticalState = rememberScrollState()
    val horizontalState = rememberScrollState()
    val contentWidth = (TalentGraphPadding * 2) +
        (TalentNodeWidth * layout.columnCount) +
        (TalentNodeHorizontalGap * (layout.columnCount - 1).coerceAtLeast(0))
    val contentHeight = (TalentGraphPadding * 2) +
        (TalentNodeHeight * layout.depthCount) +
        (TalentNodeVerticalGap * (layout.depthCount - 1).coerceAtLeast(0))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp, max = 560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(6.dp)
    ) {
        val canvasWidth = if (contentWidth < maxWidth) maxWidth else contentWidth
        val horizontalOffset = if (contentWidth < maxWidth) (maxWidth - contentWidth) / 2 else 0.dp
        val nodeById = remember(layout.nodes) { layout.nodes.associateBy { it.nodeId } }
        val scheme = MaterialTheme.colorScheme
        val edgeAvailableColor = scheme.primary.copy(alpha = 0.78f)
        val edgeLockedColor = scheme.error.copy(alpha = 0.64f)
        val edgeMaxedColor = scheme.tertiary.copy(alpha = 0.78f)
        val edgeUnknownColor = scheme.outline.copy(alpha = 0.70f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalState)
                .verticalScroll(verticalState)
        ) {
            Box(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(contentHeight)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun nodeLeft(column: Int): Float {
                        return (horizontalOffset + TalentGraphPadding + (TalentNodeWidth + TalentNodeHorizontalGap) * column).toPx()
                    }

                    fun nodeTop(depth: Int): Float {
                        return (TalentGraphPadding + (TalentNodeHeight + TalentNodeVerticalGap) * depth).toPx()
                    }

                    layout.edges.forEach { edge ->
                        val parent = nodeById[edge.fromNodeId] ?: return@forEach
                        val child = nodeById[edge.toNodeId] ?: return@forEach
                        val fromX = nodeLeft(parent.column) + TalentNodeWidth.toPx() / 2f
                        val fromY = nodeTop(parent.depth) + TalentNodeHeight.toPx()
                        val toX = nodeLeft(child.column) + TalentNodeWidth.toPx() / 2f
                        val toY = nodeTop(child.depth)
                        val midY = (fromY + toY) / 2f
                        val edgeColor = when (child.card.state) {
                            TalentNodeVisualState.LOCKED -> edgeLockedColor
                            TalentNodeVisualState.AVAILABLE -> edgeAvailableColor
                            TalentNodeVisualState.MAXED -> edgeMaxedColor
                            TalentNodeVisualState.UNKNOWN -> edgeUnknownColor
                        }
                        drawLine(
                            color = edgeColor,
                            start = androidx.compose.ui.geometry.Offset(fromX, fromY),
                            end = androidx.compose.ui.geometry.Offset(fromX, midY),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = edgeColor,
                            start = androidx.compose.ui.geometry.Offset(fromX, midY),
                            end = androidx.compose.ui.geometry.Offset(toX, midY),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = edgeColor,
                            start = androidx.compose.ui.geometry.Offset(toX, midY),
                            end = androidx.compose.ui.geometry.Offset(toX, toY),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                layout.nodes.forEach { node ->
                    val preview = actionPreviews[node.card.option.key]
                    val left = horizontalOffset + TalentGraphPadding + (TalentNodeWidth + TalentNodeHorizontalGap) * node.column
                    val top = TalentGraphPadding + (TalentNodeHeight + TalentNodeVerticalGap) * node.depth
                    TalentTreeNodeCard(
                        node = node,
                        selected = selectedNodeId == node.nodeId,
                        modifier = Modifier
                            .absoluteOffset(x = left, y = top)
                            .size(width = TalentNodeWidth, height = TalentNodeHeight),
                        onClick = {
                            selectedNodeId = node.nodeId
                            if (preview != null) {
                                onPreview(preview.withRequirementHint(node.card.blockedReason))
                            } else {
                                onAction(node.card.option.action)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TalentTreeNodeCard(
    node: TalentPositionedNodeUi,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = nodeColors(node.card.state, selected)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = colors.border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(TalentNodeIconSize)
                    .clip(CircleShape)
                    .background(colors.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = node.card.badgeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = shortTalentName(node.card.displayLabel),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${node.card.currentRank}/${node.card.maxRank}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.content.copy(alpha = 0.85f)
            )
        }
    }
}

private fun shortTalentName(name: String): String {
    val clean = name.trim()
    if (clean.length <= 22) return clean
    val words = clean.split(' ').filter { it.isNotBlank() }
    if (words.size >= 2) {
        val compressed = "${words[0]} ${words[1]}"
        if (compressed.length <= 22) return compressed
    }
    return "${clean.take(19)}..."
}

private data class NodeColorPack(
    val container: Color,
    val content: Color,
    val border: Color,
    val iconBackground: Color
)

@Composable
private fun nodeColors(state: TalentNodeVisualState, selected: Boolean): NodeColorPack {
    val scheme = MaterialTheme.colorScheme
    val base = when (state) {
        TalentNodeVisualState.AVAILABLE -> NodeColorPack(
            container = scheme.primaryContainer.copy(alpha = 0.90f),
            content = scheme.onPrimaryContainer,
            border = scheme.primary.copy(alpha = 0.80f),
            iconBackground = scheme.primary.copy(alpha = 0.25f)
        )

        TalentNodeVisualState.LOCKED -> NodeColorPack(
            container = scheme.error.copy(alpha = 0.22f),
            content = scheme.onSurface,
            border = scheme.error.copy(alpha = 0.78f),
            iconBackground = scheme.error.copy(alpha = 0.25f)
        )

        TalentNodeVisualState.MAXED -> NodeColorPack(
            container = scheme.tertiaryContainer.copy(alpha = 0.90f),
            content = scheme.onTertiaryContainer,
            border = scheme.tertiary.copy(alpha = 0.80f),
            iconBackground = scheme.tertiary.copy(alpha = 0.24f)
        )

        TalentNodeVisualState.UNKNOWN -> NodeColorPack(
            container = scheme.surfaceVariant.copy(alpha = 0.88f),
            content = scheme.onSurfaceVariant,
            border = scheme.outline.copy(alpha = 0.72f),
            iconBackground = scheme.outline.copy(alpha = 0.20f)
        )
    }
    return if (selected) {
        base.copy(border = MaterialTheme.colorScheme.primary)
    } else {
        base
    }
}

private fun MenuActionPreviewUiModel.withRequirementHint(blockedReason: String?): MenuActionPreviewUiModel {
    if (blockedReason.isNullOrBlank()) return this
    if (lines.any { it.contains(blockedReason, ignoreCase = true) }) return this
    val hasReqLine = lines.any { it.contains("req", ignoreCase = true) || it.contains("requis", ignoreCase = true) }
    if (hasReqLine) return this
    return copy(lines = lines + "Requisitos: $blockedReason")
}
