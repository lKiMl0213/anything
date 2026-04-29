package rpg.application.inventory

import rpg.achievement.AchievementTracker
import rpg.classsystem.RaceBonusSupport
import rpg.classquest.ClassQuestTagRules
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.item.ResolvedItem
import rpg.model.EquipSlot
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState

class InventoryCommandService(
    private val engine: GameEngine,
    private val achievementTracker: AchievementTracker,
    private val support: InventoryRulesSupport
) {
    private val offhandBlockedId = "__offhand_blocked__"

    fun equipItem(state: GameState, itemId: String): InventoryMutationResult {
        val player = state.player
        val item = engine.itemResolver.resolve(itemId, state.itemInstances)
            ?: return InventoryMutationResult(state, listOf("Item nao encontrado."))
        if (item.type != ItemType.EQUIPMENT) {
            return InventoryMutationResult(state, listOf("Esse item nao pode ser equipado."))
        }
        val updatedPlayer = equipItem(player, item, state.itemInstances)
        return if (updatedPlayer == player) {
            InventoryMutationResult(state)
        } else {
            InventoryMutationResult(
                state.copy(player = support.clampPlayerResources(updatedPlayer, state.itemInstances))
            )
        }
    }

    fun unequipSlot(state: GameState, slotKey: String): InventoryMutationResult {
        val player = state.player
        val equippedId = player.equipped[slotKey]
            ?: return InventoryMutationResult(state, listOf("Slot vazio."))
        if (support.isOffhandBlocked(equippedId)) {
            return InventoryMutationResult(state, listOf("Slot bloqueado por arma de duas maos."))
        }
        val item = engine.itemResolver.resolve(equippedId, state.itemInstances)
            ?: return InventoryMutationResult(state, listOf("Item equipado nao encontrado."))
        val unequipped = support.buildUnequippedPreview(player, slotKey)
        val insertion = InventorySystem.addItemsWithLimit(
            player = unequipped,
            itemInstances = state.itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = listOf(item.id)
        )
        if (insertion.rejected.isNotEmpty()) {
            return InventoryMutationResult(state, listOf("Inventario sem espaco para desequipar ${item.name}."))
        }
        val updatedPlayer = support.clampPlayerResources(
            support.normalizePlayerStorage(
                unequipped.copy(
                    inventory = insertion.inventory,
                    quiverInventory = insertion.quiverInventory,
                    selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
                ),
                state.itemInstances
            ),
            state.itemInstances
        )
        return InventoryMutationResult(
            state.copy(player = updatedPlayer),
            listOf("Desequipou ${item.name}.")
        )
    }

    fun useItem(state: GameState, itemId: String): InventoryMutationResult {
        val player = state.player
        val item = engine.itemResolver.resolve(itemId, state.itemInstances)
            ?: return InventoryMutationResult(state, listOf("Item nao encontrado."))
        if (item.type != ItemType.CONSUMABLE) {
            return InventoryMutationResult(state, listOf("Esse item nao e consumivel."))
        }
        if (player.level < item.minLevel) {
            return InventoryMutationResult(state, listOf("Nivel insuficiente para usar este item (req ${item.minLevel})."))
        }

        val stats = engine.computePlayerStats(player, state.itemInstances)
        val hpPctRestore = stats.derived.hpMax * (item.effects.hpRestorePct / 100.0)
        val mpPctRestore = stats.derived.mpMax * (item.effects.mpRestorePct / 100.0)
        var hpRestored = item.effects.hpRestore + hpPctRestore
        var mpRestored = item.effects.mpRestore + mpPctRestore
        if (item.effects.fullRestore) {
            hpRestored = stats.derived.hpMax
            mpRestored = stats.derived.mpMax
        }

        var updatedPlayer = player.copy(
            currentHp = (player.currentHp + hpRestored).coerceAtMost(stats.derived.hpMax),
            currentMp = (player.currentMp + mpRestored).coerceAtMost(stats.derived.mpMax)
        )
        if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
            val mult = (1.0 + item.effects.roomAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = updatedPlayer.copy(
                roomEffectMultiplier = mult,
                roomEffectRooms = item.effects.roomAttributeDurationRooms
            )
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            val mult = (1.0 + item.effects.runAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = updatedPlayer.copy(runAttrMultiplier = (updatedPlayer.runAttrMultiplier * mult).coerceAtLeast(0.1))
        }

        val inventory = updatedPlayer.inventory.toMutableList()
        inventory.remove(itemId)
        val updatedInstances = if (state.itemInstances.containsKey(itemId)) {
            state.itemInstances - itemId
        } else {
            state.itemInstances
        }
        updatedPlayer = support.clampPlayerResources(updatedPlayer.copy(inventory = inventory), updatedInstances)
        val messages = mutableListOf("Usou ${item.name}.")
        if (item.effects.clearNegativeStatuses || item.effects.statusImmunitySeconds > 0.0) {
            messages += "Esse efeito defensivo e aplicado apenas em combate."
        }
        return InventoryMutationResult(
            state.copy(player = updatedPlayer, itemInstances = updatedInstances),
            messages
        )
    }

    fun sellInventoryItem(state: GameState, itemId: String): InventoryMutationResult {
        val item = engine.itemResolver.resolve(itemId, state.itemInstances)
            ?: return InventoryMutationResult(state, listOf("Item nao encontrado."))
        val forcedSaleValue = ClassQuestTagRules.forcedSellValue(item.tags)
        val saleValue = if (forcedSaleValue != null) {
            forcedSaleValue
        } else {
            val baseSaleValue = engine.economyEngine.sellValue(
                itemValue = item.value,
                rarity = item.rarity,
                type = item.type,
                tags = item.tags
            )
            applyRaceSellBonus(state.player, baseSaleValue)
        }
        val inventory = state.player.inventory.toMutableList()
        if (!inventory.remove(itemId)) {
            return InventoryMutationResult(state, listOf("Item nao esta no inventario."))
        }
        var updatedPlayer = state.player.copy(
            inventory = inventory,
            gold = state.player.gold + saleValue
        )
        updatedPlayer = achievementTracker.onGoldEarned(updatedPlayer, saleValue.toLong()).player
        val updatedInstances = if (state.itemInstances.containsKey(itemId)) state.itemInstances - itemId else state.itemInstances
        return InventoryMutationResult(
            state.copy(player = updatedPlayer, itemInstances = updatedInstances),
            listOf("Vendeu ${item.name} por $saleValue ouro.")
        )
    }

    fun selectActiveAmmo(state: GameState, templateId: String): InventoryMutationResult {
        val updatedPlayer = InventorySystem.selectAmmoTemplate(
            state.player,
            state.itemInstances,
            engine.itemRegistry,
            templateId
        )
        val ammoName = engine.itemResolver.resolve(
            state.player.quiverInventory.firstOrNull {
                InventorySystem.ammoTemplateId(it, state.itemInstances, engine.itemRegistry) == templateId
            } ?: "",
            state.itemInstances
        )?.name ?: templateId
        return InventoryMutationResult(
            state.copy(player = updatedPlayer),
            listOf("Municao ativa alterada para $ammoName.")
        )
    }

    fun loadAmmoToQuiver(state: GameState, itemId: String): InventoryMutationResult {
        val result = InventorySystem.moveAmmoToQuiver(
            state.player,
            state.itemInstances,
            engine.itemRegistry,
            listOf(itemId)
        )
        if (result.accepted.isEmpty()) {
            return InventoryMutationResult(state, listOf("Nao foi possivel carregar essa flecha para a aljava."))
        }
        val name = engine.itemResolver.resolve(itemId, state.itemInstances)?.name ?: itemId
        return InventoryMutationResult(
            state.copy(
                player = state.player.copy(
                    inventory = result.inventory,
                    quiverInventory = result.quiverInventory,
                    selectedAmmoTemplateId = result.selectedAmmoTemplateId
                )
            ),
            listOf("Carregou $name x${result.accepted.size}.")
        )
    }

    fun unloadAmmoFromQuiver(state: GameState, itemId: String): InventoryMutationResult {
        val result = InventorySystem.unloadAmmoFromQuiver(
            state.player,
            state.itemInstances,
            engine.itemRegistry,
            listOf(itemId)
        )
        if (result.accepted.isEmpty()) {
            return InventoryMutationResult(state, listOf("Nao foi possivel retirar essa flecha da aljava."))
        }
        val name = engine.itemResolver.resolve(itemId, state.itemInstances)?.name ?: itemId
        val messages = mutableListOf("Retirou $name x${result.accepted.size}.")
        if (result.rejected.isNotEmpty()) {
            messages += "Inventario sem espaco para ${result.rejected.size} flecha(s)."
        }
        return InventoryMutationResult(
            state.copy(
                player = state.player.copy(
                    inventory = result.inventory,
                    quiverInventory = result.quiverInventory,
                    selectedAmmoTemplateId = result.selectedAmmoTemplateId
                )
            ),
            messages
        )
    }

    fun sellLoadedAmmo(state: GameState, itemId: String): InventoryMutationResult {
        val item = engine.itemResolver.resolve(itemId, state.itemInstances)
            ?: return InventoryMutationResult(state, listOf("Municao nao encontrada."))
        val baseSaleValue = engine.economyEngine.sellValue(
            itemValue = item.value,
            rarity = item.rarity,
            type = item.type,
            tags = item.tags
        )
        val saleValue = applyRaceSellBonus(state.player, baseSaleValue)
        val quiverInventory = state.player.quiverInventory.toMutableList()
        if (!quiverInventory.remove(itemId)) {
            return InventoryMutationResult(state, listOf("Essa flecha nao esta carregada."))
        }
        val updatedInstances = if (state.itemInstances.containsKey(itemId)) state.itemInstances - itemId else state.itemInstances
        var updatedPlayer = state.player.copy(
            quiverInventory = quiverInventory,
            gold = state.player.gold + saleValue
        )
        updatedPlayer = support.normalizePlayerStorage(updatedPlayer, updatedInstances)
        updatedPlayer = achievementTracker.onGoldEarned(updatedPlayer, saleValue.toLong()).player
        return InventoryMutationResult(
            state.copy(player = updatedPlayer, itemInstances = updatedInstances),
            listOf("Vendeu ${item.name} por $saleValue ouro.")
        )
    }

    private fun equipItem(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>
    ): PlayerState {
        val lockReason = support.questEquipRestrictionReason(player, item)
        if (lockReason != null) return player
        if (player.level < item.minLevel) return player
        val slot = item.slot ?: return player
        val equipped = player.equipped.toMutableMap()
        val inventory = player.inventory.toMutableList()
        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val emptySlot = support.accessorySlots().firstOrNull { !equipped.containsKey(it) }
                if (emptySlot == null) {
                    player
                } else {
                    equipped[emptySlot] = item.id
                    inventory.remove(item.id)
                    support.normalizePlayerStorage(
                        player.copy(equipped = equipped, inventory = inventory),
                        itemInstances
                    )
                }
            }
            EquipSlot.WEAPON_MAIN -> equipMainWeapon(player, item, itemInstances, equipped, inventory)
            EquipSlot.WEAPON_OFF -> equipOffhand(player, item, itemInstances, equipped, inventory)
            else -> {
                val slotKey = slot.name
                support.moveEquippedToInventory(equipped, inventory, slotKey)
                equipped[slotKey] = item.id
                inventory.remove(item.id)
                support.normalizePlayerStorage(
                    player.copy(equipped = equipped, inventory = inventory),
                    itemInstances
                )
            }
        }
    }

    private fun equipMainWeapon(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val offhand = equipped[offKey]
        if (item.twoHanded) {
            support.moveEquippedToInventory(equipped, inventory, mainKey)
            if (offhand != null && offhand != offhandBlockedId) {
                support.moveEquippedToInventory(equipped, inventory, offKey)
            }
            equipped[mainKey] = item.id
            equipped[offKey] = offhandBlockedId
            inventory.remove(item.id)
            return support.normalizePlayerStorage(player.copy(equipped = equipped, inventory = inventory), itemInstances)
        }
        support.moveEquippedToInventory(equipped, inventory, mainKey)
        if (offhand == offhandBlockedId) {
            equipped.remove(offKey)
        }
        equipped[mainKey] = item.id
        inventory.remove(item.id)
        return support.normalizePlayerStorage(player.copy(equipped = equipped, inventory = inventory), itemInstances)
    }

    private fun equipOffhand(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>,
        equipped: MutableMap<String, String>,
        inventory: MutableList<String>
    ): PlayerState {
        val mainKey = EquipSlot.WEAPON_MAIN.name
        val offKey = EquipSlot.WEAPON_OFF.name
        val mainItemId = equipped[mainKey]
        if (item.twoHanded) return player
        if (mainItemId != null && support.isTwoHanded(mainItemId, itemInstances)) return player
        if (equipped[offKey] == offhandBlockedId) return player
        if (item.tags.contains("shield") && mainItemId != null && support.isTwoHanded(mainItemId, itemInstances)) {
            return player
        }
        support.moveEquippedToInventory(equipped, inventory, offKey)
        equipped[offKey] = item.id
        inventory.remove(item.id)
        return support.normalizePlayerStorage(player.copy(equipped = equipped, inventory = inventory), itemInstances)
    }

    private fun applyRaceSellBonus(player: PlayerState, baseSaleValue: Int): Int {
        val raceDef = runCatching { engine.classSystem.raceDef(player.raceId) }.getOrNull()
        val bonusPct = RaceBonusSupport.tradeSellBonusPct(raceDef)
        return RaceBonusSupport.applyTradeSellBonus(baseSaleValue, bonusPct)
    }
}
