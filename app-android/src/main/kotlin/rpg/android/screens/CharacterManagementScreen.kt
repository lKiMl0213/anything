package rpg.android.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rpg.android.R
import rpg.android.state.CharacterUiModel
import rpg.android.ui.itemEmoji
import rpg.android.ui.slotEmoji
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.EquipmentSlot
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GameIconActionButton
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.InventoryPanel
import rpg.android.ui.components.InventoryRowItem
import rpg.android.tutorial.TutorialTarget
import rpg.android.tutorial.tutorialAnchor
import rpg.application.inventory.EquippedSlotView
@Composable
fun CharacterManagementScreen(
    state: CharacterUiModel,
    hasProgressAlert: Boolean,
    onSlotClick: (String) -> Unit,
    onInventoryItemClick: (String) -> Unit,
    onOpenAttributes: () -> Unit,
    onOpenTalents: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit
) {
    val slotMap = state.equippedSlots.associateBy { it.slotKey.uppercase() }
    val topAccessorySlot = state.accessorySlots.firstOrNull()?.let(BodySlotUi::from)
        ?: BodySlotUi(slotKey = "ACCESSORY_1", displayLabel = "-")
    val remainingAccessorySlots = state.accessorySlots.drop(1)
    GameScreenRoot(
        backgroundRes = R.drawable.bg_character,
        bottomNav = {
            GameBottomNav(
                items = listOf(
                    BottomNavItem(
                        key = "character",
                        label = "Personagem",
                        icon = "\uD83E\uDDD9",
                        selected = true,
                        onClick = {}
                    ),
                    BottomNavItem(
                        key = "production",
                        label = "Producao",
                        icon = "\u2692",
                        selected = false,
                        onClick = onOpenProduction
                    ),
                    BottomNavItem(
                        key = "explore",
                        label = "Explorar",
                        icon = "\uD83E\uDDED",
                        selected = false,
                        onClick = onOpenHub
                    ),
                    BottomNavItem(
                        key = "city",
                        label = "Cidade",
                        icon = "\uD83C\uDFD9",
                        selected = false,
                        onClick = onOpenCity
                    ),
                    BottomNavItem(
                        key = "progress",
                        label = "Progresso",
                        icon = "\uD83D\uDCC8",
                        selected = false,
                        hasAlert = hasProgressAlert,
                        onClick = onOpenProgression
                    )
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamePanel(
                title = "Equipados",
                modifier = Modifier.tutorialAnchor(TutorialTarget.CHARACTER_EQUIPMENT_PANEL, extraPadding = 8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val slotSize = (maxWidth * 0.20f).coerceIn(52.dp, 74.dp)
                    val accessorySize = (slotSize * 0.80f).coerceIn(42.dp, 58.dp)
                    val spacing = (slotSize * 0.14f).coerceIn(6.dp, 12.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        BodySlotTripletRow(
                            left = slotMap["ALJAVA"].asBodySlot("ALJAVA"),
                            center = slotMap["HEAD"].asBodySlot("HEAD"),
                            right = topAccessorySlot,
                            slotSize = slotSize,
                            spacing = spacing,
                            onSlotClick = onSlotClick
                        )
                        BodySlotTripletRow(
                            left = slotMap["WEAPON_MAIN"].asBodySlot("WEAPON_MAIN"),
                            center = slotMap["CHEST"].asBodySlot("CHEST"),
                            right = slotMap["WEAPON_OFF"].asBodySlot("WEAPON_OFF"),
                            slotSize = slotSize,
                            spacing = spacing,
                            onSlotClick = onSlotClick
                        )
                        BodySlotPairRow(
                            left = slotMap["GLOVES"].asBodySlot("GLOVES"),
                            right = slotMap["LEGS"].asBodySlot("LEGS"),
                            slotSize = slotSize,
                            spacing = spacing,
                            onSlotClick = onSlotClick
                        )
                        BodySingleSlotRow(
                            slot = slotMap["BOOTS"].asBodySlot("BOOTS"),
                            slotSize = slotSize,
                            onSlotClick = onSlotClick
                        )
                        if (remainingAccessorySlots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                remainingAccessorySlots.forEach { slot ->
                                    IconSlotTile(
                                        slot = BodySlotUi.from(slot),
                                        slotSize = accessorySize,
                                        onSlotClick = onSlotClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .tutorialAnchor(TutorialTarget.CHARACTER_ACTION_PANEL, extraPadding = 10.dp)
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameIconActionButton(
                    icon = "\uD83D\uDCCA",
                    onClick = onOpenAttributes,
                    enabled = state.canOpenAttributes,
                    size = 44.dp
                )
                GameIconActionButton(
                    icon = "\u2728",
                    onClick = onOpenTalents,
                    enabled = state.canOpenTalents,
                    size = 44.dp
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.80f),
                shape = RoundedCornerShape(6.dp)
            ) {}
            InventoryPanel(
                title = "Inventário ${state.inventoryCapacityLabel}",
                items = state.inventoryStacks.map { stack ->
                    InventoryRowItem(
                        id = stack.sampleItemId,
                        label = "${itemEmoji(stack.item.name, stack.item.type, stack.item.tags)} ${stack.item.name} x${stack.quantity}",
                        onClick = { onInventoryItemClick(stack.sampleItemId) }
                    )
                }
            )
        }
    }
}
@Composable
private fun BodySlotTripletRow(
    left: BodySlotUi,
    center: BodySlotUi,
    right: BodySlotUi,
    slotSize: Dp,
    spacing: Dp,
    onSlotClick: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlotTile(left, slotSize, onSlotClick)
        IconSlotTile(center, slotSize, onSlotClick)
        IconSlotTile(right, slotSize, onSlotClick)
    }
}
@Composable
private fun BodySlotPairRow(
    left: BodySlotUi,
    right: BodySlotUi,
    slotSize: Dp,
    spacing: Dp,
    onSlotClick: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlotTile(left, slotSize, onSlotClick)
        IconSlotTile(right, slotSize, onSlotClick)
    }
}
@Composable
private fun BodySingleSlotRow(
    slot: BodySlotUi,
    slotSize: Dp,
    onSlotClick: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlotTile(slot, slotSize, onSlotClick)
    }
}
@Composable
private fun IconSlotTile(
    slot: BodySlotUi,
    slotSize: Dp,
    onSlotClick: (String) -> Unit
) {
    EquipmentSlot(
        title = slot.slotKey,
        value = compactEquippedLabel(slot.displayLabel),
        icon = slotEmoji(slot.slotKey),
        fillWidth = false,
        compact = true,
        iconOnly = true,
        slotSize = slotSize,
        onClick = { onSlotClick(slot.slotKey) }
    )
}
private data class BodySlotUi(
    val slotKey: String,
    val displayLabel: String
) {
    companion object {
        fun from(slot: EquippedSlotView): BodySlotUi = BodySlotUi(
            slotKey = slot.slotKey,
            displayLabel = slot.displayLabel
        )
    }
}
private fun EquippedSlotView?.asBodySlot(slotKeyFallback: String): BodySlotUi {
    return if (this == null) {
        BodySlotUi(slotKey = slotKeyFallback, displayLabel = "-")
    } else {
        BodySlotUi.from(this)
    }
}
private fun compactEquippedLabel(value: String): String {
    if (value == "-") return "-"
    val base = value
        .replace(Regex("\\[[^\\]]*\\]\\s*"), "")
        .substringBefore(" | ")
        .replace("Bloqueado por arma de duas maos", "Bloqueado")
        .trim()
    return if (base.length > 15) "${base.take(12)}..." else base
}
