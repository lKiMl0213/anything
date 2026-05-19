package rpg.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rpg.android.audio.LocalGameAudioController
import rpg.android.audio.SoundEffect
import rpg.android.tutorial.TutorialTarget
import rpg.android.tutorial.tutorialAnchor

data class BottomNavItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val icon: String = label,
    val hasAlert: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun GameBottomNav(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier
) {
    val audioController = LocalGameAudioController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = GameUiTokens.panelMaxWidth)
            .height(GameUiTokens.bottomNavHeight)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(GameUiTokens.buttonCorner)
            )
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val itemWeight by animateFloatAsState(
                targetValue = if (item.selected) 1.22f else 1f,
                label = "bottomNavWeight"
            )
            val itemScale by animateFloatAsState(
                targetValue = if (item.selected) 1.06f else 1f,
                label = "bottomNavScale"
            )
            val background = when {
                item.selected && item.hasAlert -> MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                item.selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                item.hasAlert -> MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            }
            val anchoredModifier = bottomNavTutorialTarget(item.key)?.let { target ->
                Modifier.tutorialAnchor(target, extraPadding = 6.dp)
            } ?: Modifier
            Box(
                modifier = anchoredModifier
                    .weight(itemWeight)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(GameUiTokens.buttonCorner))
                    .clickable(
                        onClick = {
                            audioController.play(SoundEffect.CLICK)
                            item.onClick()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(itemScale)
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.icon,
                        color = if (item.selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = GameUiTokens.bottomNavIconSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    AnimatedVisibility(
                        modifier = Modifier.fillMaxWidth(),
                        visible = item.selected,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.label,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
                if (item.hasAlert) {
                    Text(
                        text = "\u2022",
                        color = if (item.selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 4.dp)
                            .size(10.dp)
                    )
                }
            }
        }
    }
}

private fun bottomNavTutorialTarget(key: String): TutorialTarget? {
    return when (key.lowercase()) {
        "character" -> TutorialTarget.BOTTOM_NAV_CHARACTER
        "production" -> TutorialTarget.BOTTOM_NAV_PRODUCTION
        "explore" -> TutorialTarget.BOTTOM_NAV_EXPLORE
        "city" -> TutorialTarget.BOTTOM_NAV_CITY
        "progress" -> TutorialTarget.BOTTOM_NAV_PROGRESSION
        else -> null
    }
}
