// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.EquipSlot
import rpg.model.GameState
import rpg.model.PlayerState

internal class LegacyEquipmentFlow(
    private val engine: GameEngine,
    private val accessorySlots: List<String>,
    private val offhandBlockedId: String,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val autoSave: (GameState) -> Unit,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> ComputedStats,
    private val normalizePlayerStorage: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val clampPlayerResources: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val format: (Double) -> String,
    private val formatSignedDouble: (Double) -> String,
    private val detailSupport: LegacyInventoryDetailSupport,
    private val itemDisplayLabel: (rpg.item.ResolvedItem) -> String,
    private val equippedSlotLabel: (String) -> String
) {
    fun openEquipped(state: GameState): GameState {
        var player = state.player
        val itemInstances = state.itemInstances
        val slotOrder = listOf(
            EquipSlot.WEAPON_MAIN.name,
            EquipSlot.WEAPON_OFF.name,
            EquipSlot.ALJAVA.name,
            EquipSlot.HEAD.name,
            EquipSlot.CHEST.name,
            EquipSlot.LEGS.name,
            EquipSlot.GLOVES.name,
            EquipSlot.BOOTS.name
        ) + accessorySlots

        while (true) {
            println("\n=== Equipados ===")
            slotOrder.forEachIndexed { index, slot ->
                val equippedId = player.equipped[slot]
                val label = when {
                    equippedId == null -> "-"
                    equippedId == offhandBlockedId -> "Bloqueado por arma de duas maos"
                    else -> engine.itemResolver.resolve(equippedId, itemInstances)?.let(itemDisplayLabel) ?: equippedId
                }
                val extra = if (slot == EquipSlot.ALJAVA.name && equippedId != null && equippedId != offhandBlockedId) {
                    val current = InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
                    val max = InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
                    " | Aljava: $current / $max flechas"
                } else {
                    ""
                }
                println("${index + 1}. ${equippedSlotLabel(slot)} -> $label$extra")
            }
            val stats = computePlayerStats(player, itemInstances)
            println(
                "Resumo: DMG ${format(stats.derived.damagePhysical)} | " +
                    "DEF ${format(stats.derived.defPhysical)} | " +
                    "HP ${format(stats.derived.hpMax)} | " +
                    "MP ${format(stats.derived.mpMax)}"
            )
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, slotOrder.size)
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            val slotKey = slotOrder[choice - 1]
            val equippedId = player.equipped[slotKey]
            if (equippedId == null) {
                println("Slot vazio.")
                continue
            }
            if (equippedId == offhandBlockedId) {
                println("Slot bloqueado por arma de duas maos.")
                continue
            }
            val item = engine.itemResolver.resolve(equippedId, itemInstances)
            if (item == null) {
                println("Item equipado nao encontrado.")
                continue
            }

            showEquippedItemDetails(player, itemInstances, slotKey, item)
            println("1. Desequipar")
            println("x. Voltar")
            if (readMenuChoice("Escolha: ", 1, 1) == 1) {
                val updated = unequipSlot(player, itemInstances, slotKey, item)
                if (updated != player) {
                    player = updated
                    autoSave(state.copy(player = player, itemInstances = itemInstances))
                }
            }
        }
    }

    fun equipItem(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): PlayerState {
        val lockReason = detailSupport.questEquipRestrictionReason(player, item)
        if (lockReason != null) {
            println(lockReason)
            return player
        }
        if (player.level < item.minLevel) {
            println("Nivel insuficiente para equipar ${item.name} (req ${item.minLevel}).")
            return player
        }
        val slot = item.slot ?: return player
        val equipped = player.equipped.toMutableMap()
        val inventory = player.inventory.toMutableList()

        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val targetSlot = pickAccessorySlot(equipped, itemInstances) ?: return player
                moveEquippedToInventory(equipped, inventory, targetSlot)
                equipped[targetSlot] = item.id
                inventory.remove(item.id)
                val updated = normalizePlayerStorage(
                    player.copy(equipped = equipped, inventory = inventory),
                    itemInstances
                )
                println("Equipou ${item.name} no slot $targetSlot.")
                clampPlayerResources(updated, itemInstances)
            }
            EquipSlot.WEAPON_MAIN -> {
                equipMainWeapon(player, item, itemInstances, equipped, inventory)
            }
            EquipSlot.WEAPON_OFF -> {
                equipOffhand(player, item, itemInstances, equipped, inventory)
            }
            else -> {
                val slotKey = slot.name
                moveEquippedToInventory(equipped, inventory, slotKey)
                equipped[slotKey] = item.id
                inventory.remove(item.id)
                val updated = normalizePlayerStorage(
                    player.copy(equipped = equipped, inventory = inventory),
                    itemInstances
                )
                println("Equipou ${item.name} no slot ${slot.name}.")
                clampPlayerResources(updated, itemInstances)
            }
        }
    }

    fun applyTwoHandedLoadout(equipped: MutableMap<String, String>) {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val mainItemId = equipped[mainKey] ?: return
        val resolved = engine.itemResolver.resolve(mainItemId, emptyMap()) ?: return
        if (resolved.twoHanded) {
            equipped[offKey] = offhandBlockedId
        }
    }

    private fun showEquippedItemDetails(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        slotKey: String,
        item: rpg.item.ResolvedItem
    ) {
        println("\n=== ${equippedSlotLabel(slotKey)} ===")
        println("Item: ${itemDisplayLabel(item)}")
        if (item.description.isNotBlank()) {
            println("Descricao: ${item.description}")
        }
        val slotLabel = item.slot?.name ?: slotKey
        val handLabel = if (item.twoHanded) " (duas maos)" else ""
        println("Slot: $slotLabel$handLabel")
        if (slotKey == EquipSlot.ALJAVA.name) {
            val current = InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
            val max = InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
            println("Aljava: $current / $max flechas")
        }
        val bonusLabel = detailSupport.formatItemBonuses(item)
        if (bonusLabel.isNotBlank()) {
            println("Bonus: $bonusLabel")
        }
        val removalPreview = buildUnequippedPreview(player, slotKey)
        val before = computePlayerStats(player, itemInstances)
        val after = computePlayerStats(removalPreview, itemInstances)
        println(
            "Ao desequipar: DMG ${formatSignedDouble(after.derived.damagePhysical - before.derived.damagePhysical)} | " +
                "DEF ${formatSignedDouble(after.derived.defPhysical - before.derived.defPhysical)} | " +
                "HP ${formatSignedDouble(after.derived.hpMax - before.derived.hpMax)} | " +
                "SPD ${formatSignedDouble(after.derived.attackSpeed - before.derived.attackSpeed)}"
        )
    }

    private fun buildUnequippedPreview(player: PlayerState, slotKey: String): PlayerState {
        val equipped = player.equipped.toMutableMap()
        equipped.remove(slotKey)
        if (slotKey == EquipSlot.WEAPON_MAIN.name && equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
            equipped.remove(EquipSlot.WEAPON_OFF.name)
        }
        return player.copy(equipped = equipped)
    }

    private fun unequipSlot(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        slotKey: String,
        item: rpg.item.ResolvedItem
    ): PlayerState {
        val unequipped = buildUnequippedPreview(player, slotKey)
        val insertion = InventorySystem.addItemsWithLimit(
            player = unequipped,
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = listOf(item.id)
        )
        if (insertion.rejected.isNotEmpty()) {
            println("Inventario sem espaco para desequipar ${item.name}.")
            return player
        }
        println("Desequipou ${item.name}.")
        return clampPlayerResources(
            normalizePlayerStorage(
                unequipped.copy(
                    inventory = insertion.inventory,
                    quiverInventory = insertion.quiverInventory,
                    selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
                ),
                itemInstances
            ),
            itemInstances
        )
    }

    private fun equipMainWeapon(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val offhand = equipped[offKey]

        if (item.twoHanded) {
            moveEquippedToInventory(equipped, inventory, mainKey)
            if (offhand != null && offhand != offhandBlockedId) {
                moveEquippedToInventory(equipped, inventory, offKey)
            }
            equipped[mainKey] = item.id
            equipped[offKey] = offhandBlockedId
            inventory.remove(item.id)
            val updated = normalizePlayerStorage(
                player.copy(equipped = equipped, inventory = inventory),
                itemInstances
            )
            println("Equipou ${item.name} (duas maos).")
            return clampPlayerResources(updated, itemInstances)
        }

        moveEquippedToInventory(equipped, inventory, mainKey)
        if (offhand == offhandBlockedId) {
            equipped.remove(offKey)
        }
        equipped[mainKey] = item.id
        inventory.remove(item.id)
        val updated = normalizePlayerStorage(
            player.copy(equipped = equipped, inventory = inventory),
            itemInstances
        )
        println("Equipou ${item.name} na arma primaria.")
        return clampPlayerResources(updated, itemInstances)
    }

    private fun equipOffhand(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val mainItemId = equipped[mainKey]

        if (item.twoHanded) {
            println("Item de duas maos nao pode ser equipado na secundaria.")
            return player
        }

        if (mainItemId != null && isTwoHanded(mainItemId, itemInstances)) {
            println("Arma de duas maos equipada. Remova-a antes de usar secundaria.")
            return player
        }
        if (equipped[offKey] == offhandBlockedId) {
            println("Arma de duas maos equipada. Remova-a antes de usar secundaria.")
            return player
        }
        if (item.tags.contains("shield")) {
            if (mainItemId != null && isTwoHanded(mainItemId, itemInstances)) {
                println("Escudos nao podem ser usados com armas de duas maos.")
                return player
            }
        }

        moveEquippedToInventory(equipped, inventory, offKey)
        equipped[offKey] = item.id
        inventory.remove(item.id)
        val updated = normalizePlayerStorage(
            player.copy(equipped = equipped, inventory = inventory),
            itemInstances
        )
        println("Equipou ${item.name} na arma secundaria.")
        return clampPlayerResources(updated, itemInstances)
    }

    private fun pickAccessorySlot(
        equipped: Map<String, String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): String? {
        val emptySlot = accessorySlots.firstOrNull { !equipped.containsKey(it) }
        if (emptySlot != null) return emptySlot

        println("\nSlots de acessorio ocupados:")
        accessorySlots.forEachIndexed { index, slot ->
            val itemId = equipped[slot]
            val name = if (itemId == null || itemId == offhandBlockedId) {
                "Vazio"
            } else {
                engine.itemResolver.resolve(itemId, itemInstances)?.name ?: itemId
            }
            println("${index + 1}. $slot -> $name")
        }
        val choice = readInt("Substituir qual slot? ", 1, accessorySlots.size)
        return accessorySlots[choice - 1]
    }

    private fun moveEquippedToInventory(
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

    private fun isTwoHanded(itemId: String, itemInstances: Map<String, rpg.model.ItemInstance>): Boolean {
        val resolved = engine.itemResolver.resolve(itemId, itemInstances)
        return resolved?.twoHanded == true
    }
}
