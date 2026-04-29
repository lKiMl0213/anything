package rpg.application.inventory

import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.item.ResolvedItem
import rpg.model.EquipSlot
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState

class InventoryRulesSupport(
    private val repo: DataRepository,
    private val engine: GameEngine
) {
    private val accessorySlots = repo.character.accessorySlots
    private val offhandBlockedId = "__offhand_blocked__"
    private val equipRules = InventoryEquipRulesSupport(
        repo = repo,
        engine = engine,
        accessorySlots = accessorySlots,
        offhandBlockedId = offhandBlockedId
    )
    private val detailSupport = InventoryItemDetailSupport(
        repo = repo,
        engine = engine,
        equipRules = equipRules,
        itemDisplayLabel = ::itemDisplayLabel,
        equippedSlotLabel = ::equippedSlotLabel
    )

    fun normalizeState(state: GameState): GameState {
        val normalizedPlayer = normalizePlayerStorage(state.player, state.itemInstances)
        val clamped = clampPlayerResources(normalizedPlayer, state.itemInstances)
        return if (clamped == state.player) state else state.copy(player = clamped)
    }

    fun normalizePlayerStorage(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): PlayerState {
        return rpg.inventory.InventorySystem.normalizeAmmoStorage(player, itemInstances, engine.itemRegistry)
    }

    fun clampPlayerResources(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): PlayerState {
        val stats = engine.computePlayerStats(player, itemInstances)
        return player.copy(
            currentHp = player.currentHp.coerceIn(0.0, stats.derived.hpMax),
            currentMp = player.currentMp.coerceIn(0.0, stats.derived.mpMax)
        )
    }

    fun buildInventoryStacks(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): List<InventoryStackView> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in player.inventory) {
            val key = rpg.inventory.InventorySystem.stackKey(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }
        return grouped.values.mapNotNull { ids ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            InventoryStackView(
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<InventoryStackView> { it.item.rarity.ordinal }
                .thenByDescending { it.item.powerScore }
                .thenBy { it.item.name.lowercase() }
        )
    }

    fun applyInventoryFilter(
        stacks: List<InventoryStackView>,
        filter: InventoryFilterState
    ): List<InventoryStackView> {
        return stacks.filter { stack ->
            val typeOk = filter.type == null || stack.item.type == filter.type
            val rarityOk = filter.minimumRarity == null || stack.item.rarity.ordinal >= filter.minimumRarity.ordinal
            typeOk && rarityOk
        }
    }

    fun inventoryFilterSummary(filter: InventoryFilterState): String {
        val typeLabel = when (filter.type) {
            null -> "todos"
            ItemType.EQUIPMENT -> "equipamentos"
            ItemType.CONSUMABLE -> "consumiveis"
            ItemType.MATERIAL -> "materiais"
        }
        val rarityLabel = filter.minimumRarity?.colorLabel ?: "qualquer raridade"
        return "tipo=$typeLabel | raridade min=$rarityLabel"
    }

    fun buildAmmoStacks(
        itemIds: List<String>,
        itemInstances: Map<String, ItemInstance>,
        selectedTemplateId: String?
    ): List<AmmoStackView> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in itemIds) {
            if (!rpg.inventory.InventorySystem.isArrowAmmo(itemId, itemInstances, engine.itemRegistry)) continue
            val templateId = rpg.inventory.InventorySystem.ammoTemplateId(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(templateId) { mutableListOf() }.add(itemId)
        }
        return grouped.mapNotNull { (templateId, ids) ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            AmmoStackView(
                templateId = templateId,
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<AmmoStackView> { it.templateId == selectedTemplateId?.trim()?.lowercase() }
                .thenBy { it.item.name.lowercase() }
        )
    }

    fun equippedSlotViews(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): List<EquippedSlotView> {
        return equippedSlotOrder().map { slotKey ->
            val equippedId = player.equipped[slotKey]
            val label = when {
                equippedId == null -> "-"
                equippedId == offhandBlockedId -> "Bloqueado por arma de duas maos"
                else -> engine.itemResolver.resolve(equippedId, itemInstances)?.let(::itemDisplayLabel) ?: equippedId
            }
            val slotLabel = if (slotKey == EquipSlot.ALJAVA.name && equippedId != null && equippedId != offhandBlockedId) {
                val current = rpg.inventory.InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
                val max = rpg.inventory.InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
                "$label | Aljava: $current/$max flechas"
            } else {
                label
            }
            EquippedSlotView(
                slotKey = slotKey,
                label = equippedSlotLabel(slotKey),
                equippedItemId = equippedId?.takeIf { it != offhandBlockedId },
                displayLabel = slotLabel
            )
        }
    }

    fun buildInventoryItemDetail(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        stack: InventoryStackView
    ): InventoryItemDetailView {
        return detailSupport.buildInventoryItemDetail(player, itemInstances, stack)
    }

    fun buildEquippedItemDetail(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        slotKey: String
    ): EquippedItemDetailView? {
        return detailSupport.buildEquippedItemDetail(
            player = player,
            itemInstances = itemInstances,
            slotKey = slotKey,
            isOffhandBlocked = ::isOffhandBlocked
        )
    }

    fun equippedSlotOrder(): List<String> {
        return listOf(
            EquipSlot.WEAPON_MAIN.name,
            EquipSlot.WEAPON_OFF.name,
            EquipSlot.ALJAVA.name,
            EquipSlot.HEAD.name,
            EquipSlot.CHEST.name,
            EquipSlot.LEGS.name,
            EquipSlot.GLOVES.name,
            EquipSlot.BOOTS.name
        ) + accessorySlots
    }

    fun equippedSlotLabel(slotKey: String): String = when (slotKey) {
        EquipSlot.WEAPON_MAIN.name -> "Arma primaria"
        EquipSlot.WEAPON_OFF.name -> "Arma secundaria"
        EquipSlot.ALJAVA.name -> "Aljava"
        EquipSlot.HEAD.name -> "Cabeca"
        EquipSlot.CHEST.name -> "Peito"
        EquipSlot.LEGS.name -> "Pernas"
        EquipSlot.GLOVES.name -> "Luvas"
        EquipSlot.BOOTS.name -> "Botas"
        else -> if (slotKey.uppercase().startsWith("ACCESSORY")) {
            slotKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        } else {
            slotKey
        }
    }

    fun itemDisplayLabel(item: ResolvedItem): String = "[${item.rarity.colorLabel}] ${item.name}"

    fun classTagDisplayLabel(tag: String): String {
        return equipRules.classTagDisplayLabel(tag)
    }

    fun questEquipRestrictionReason(player: PlayerState, item: ResolvedItem): String? {
        return equipRules.questEquipRestrictionReason(player, item)
    }

    fun previewEquipDelta(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>
    ): EquipComparisonPreviewData? {
        return equipRules.previewEquipDelta(player, item, itemInstances)
    }

    fun formatEquipComparison(before: rpg.engine.ComputedStats, after: rpg.engine.ComputedStats): String {
        return equipRules.formatEquipComparison(before, after)
    }

    fun buildUnequippedPreview(player: PlayerState, slotKey: String): PlayerState {
        return equipRules.buildUnequippedPreview(player, slotKey)
    }

    fun formatItemBonuses(item: ResolvedItem): String {
        return detailSupport.formatItemBonuses(item)
    }

    fun formatItemEffectsSummary(item: ResolvedItem): String {
        return detailSupport.formatItemEffectsSummary(item)
    }

    fun isOffhandBlocked(itemId: String?): Boolean = itemId == offhandBlockedId

    fun resolveAccessorySlotForAutoPreview(
        equipped: Map<String, String>,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        return equipRules.resolveAccessorySlotForAutoPreview(equipped, itemInstances)
    }

    fun accessorySlots(): List<String> = accessorySlots

    fun moveEquippedToInventory(
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>,
        slotKey: String
    ) {
        val previous = equipped[slotKey] ?: return
        if (previous != offhandBlockedId) {
            inventory.add(previous)
        }
        equipped.remove(slotKey)
    }

    fun isTwoHanded(itemId: String, itemInstances: Map<String, ItemInstance>): Boolean {
        return equipRules.isTwoHanded(itemId, itemInstances)
    }
}
