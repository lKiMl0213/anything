package rpg.android.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import rpg.android.R
import rpg.android.state.CharacterUiModel
import rpg.android.tutorial.TutorialTarget
import rpg.android.tutorial.tutorialAnchor
import rpg.android.ui.itemEmoji
import rpg.android.ui.slotEmoji
import rpg.android.ui.components.BackpackTierIndicator
import rpg.android.ui.components.BottomNavItem
import rpg.android.ui.components.CharacterSpriteImage
import rpg.android.ui.components.EquipmentSlot
import rpg.android.ui.components.GameBottomNav
import rpg.android.ui.components.GameIconActionButton
import rpg.android.ui.components.GamePanel
import rpg.android.ui.components.GamePopupMenu
import rpg.android.ui.components.GamePrimaryButton
import rpg.android.ui.components.GameScreenRoot
import rpg.android.ui.components.InventoryPanel
import rpg.android.ui.components.InventoryRowItem
import rpg.application.actions.GameAction
import rpg.application.inventory.EquippedSlotView
import rpg.application.inventory.InventorySortMode
import rpg.model.ItemType

@Composable
fun CharacterManagementScreen(
    state: CharacterUiModel,
    hasProgressAlert: Boolean,
    onSlotClick: (String) -> Unit,
    onInventoryItemClick: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onAutoEquip: () -> Unit,
    onUpgradeAction: (GameAction) -> Unit,
    onOpenAttributes: () -> Unit,
    onOpenTalents: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenCity: () -> Unit,
    onOpenProgression: () -> Unit
) {
    var showUpgradesPopup by remember { mutableStateOf(false) }
    val slotMap = (state.equippedSlots + state.accessorySlots).associateBy { it.slotKey.uppercase() }

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
                        label = "Produção",
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
                title = "Equipamentos",
                modifier = Modifier.tutorialAnchor(TutorialTarget.CHARACTER_EQUIPMENT_PANEL, extraPadding = 8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val slotSize = (maxWidth * 0.13f).coerceIn(44.dp, 58.dp)
                    val spacing = (slotSize * 0.10f).coerceIn(3.dp, 6.dp)
                    val slotTopPadding = (maxWidth * 0.07f).coerceIn(22.dp, 34.dp)
                    val spriteMaxWidth = (maxWidth * 0.82f).coerceIn(260.dp, 520.dp)
                    val spriteMaxHeight = (spriteMaxWidth * 1.72f).coerceIn(420.dp, 860.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EquipmentSlotColumn(
                                slots = listOf(
                                    slotMap["CAPE"].asBodySlot("CAPE"),
                                    slotMap["HEAD"].asBodySlot("HEAD"),
                                    slotMap["CHEST"].asBodySlot("CHEST"),
                                    slotMap["WEAPON_MAIN"].asBodySlot("WEAPON_MAIN"),
                                    slotMap["LEGS"].asBodySlot("LEGS"),
                                    slotMap["BOOTS"].asBodySlot("BOOTS")
                                ),
                                slotSize = slotSize,
                                onSlotClick = onSlotClick,
                                modifier = Modifier
                                    .width(slotSize)
                                    .padding(top = slotTopPadding)
                                    .zIndex(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 420.dp)
                                    .zIndex(0f),
                                contentAlignment = Alignment.Center
                            ) {
                                CharacterSpriteImage(
                                    assetPath = state.spriteAssetPath,
                                    imageWidthFraction = 1f,
                                    maxWidth = spriteMaxWidth,
                                    maxHeight = spriteMaxHeight,
                                    visualScale = 3f
                                )
                            }
                            EquipmentSlotColumn(
                                slots = listOf(
                                    slotMap["WEAPON_OFF"].asBodySlot("WEAPON_OFF"),
                                    slotMap["ACCESSORY1"].asBodySlot("ACCESSORY1"),
                                    slotMap["ACCESSORY2"].asBodySlot("ACCESSORY2"),
                                    slotMap["ACCESSORY3"].asBodySlot("ACCESSORY3"),
                                    slotMap["ACCESSORY4"].asBodySlot("ACCESSORY4"),
                                    slotMap["ACCESSORY5"].asBodySlot("ACCESSORY5")
                                ),
                                slotSize = slotSize,
                                onSlotClick = onSlotClick,
                                modifier = Modifier
                                    .width(slotSize)
                                    .padding(top = slotTopPadding)
                                    .zIndex(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        GamePrimaryButton(
                            label = "Auto Equipar",
                            onClick = onAutoEquip,
                            modifier = Modifier.fillMaxWidth(0.56f)
                        )
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
                GameIconActionButton(
                    icon = "\uD83D\uDEE0\uFE0F",
                    onClick = { showUpgradesPopup = true },
                    enabled = state.canOpenUpgrades,
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
                capacityLabel = state.inventoryCapacityLabel,
                sortModeLabel = inventorySortLabel(state.inventorySortMode),
                onSortSelected = onSortSelected,
                backpackIndicators = state.backpackTiers.map {
                    BackpackTierIndicator(tier = it.tier, equipped = it.equipped)
                },
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

    if (showUpgradesPopup) {
        GamePopupMenu(
            title = "Melhorias",
            onDismiss = { showUpgradesPopup = false },
            showCloseButton = false
        ) {
            if (state.acquiredUpgrades.isEmpty()) {
                Text(
                    text = "Nenhuma melhoria adquirida.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = state.acquiredUpgrades,
                        key = { _, upgrade -> upgrade.id }
                    ) { index, upgrade ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = upgrade.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Nv ${upgrade.level}/${upgrade.maxLevel}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Efeito: ${upgrade.effectLabel}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                if (upgrade.upgradeAction != null) {
                                    GamePrimaryButton(
                                        label = "Upar",
                                        onClick = { onUpgradeAction(upgrade.upgradeAction) },
                                        modifier = Modifier.fillMaxWidth(0.55f)
                                    )
                                }
                            }
                        }
                        if (index < state.acquiredUpgrades.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentSlotColumn(
    slots: List<BodySlotUi>,
    slotSize: Dp,
    onSlotClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        slots.forEach { slot ->
            IconSlotTile(
                slot = slot,
                slotSize = slotSize,
                onSlotClick = onSlotClick
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
    val compactLabel = compactEquippedLabel(slot.displayLabel)
    val icon = if (compactLabel == "-" || compactLabel.contains("Bloqueado", ignoreCase = true)) {
        slotEmoji(slot.slotKey)
    } else {
        itemEmoji(compactLabel, ItemType.EQUIPMENT, emptyList())
    }
    EquipmentSlot(
        title = slot.slotKey,
        value = compactLabel,
        icon = icon,
        fillWidth = false,
        compact = true,
        iconOnly = true,
        slotSize = slotSize,
        onClick = { onSlotClick(slot.slotKey) }
    )
}

private fun inventorySortLabel(mode: InventorySortMode): String {
    return when (mode) {
        InventorySortMode.TYPE -> "Tipo"
        InventorySortMode.RARITY -> "Raridade"
        InventorySortMode.VALUE -> "Valor"
    }
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
        .replace("Bloqueado por arma de duas mãos", "Bloqueado")
        .trim()
    return if (base.length > 15) "${base.take(12)}..." else base
}
