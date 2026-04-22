package rpg.cli

import rpg.cli.model.*
import rpg.classquest.ClassQuestTagRules
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.item.ItemRarity
import rpg.model.GameState
import rpg.model.ItemType
import rpg.model.PlayerState

internal class LegacyInventoryFlow(
    private val engine: GameEngine,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val autoSave: (GameState) -> Unit,
    private val normalizePlayerStorage: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val detailSupport: LegacyInventoryDetailSupport,
    private val equipmentFlow: LegacyEquipmentFlow,
    private val quiverFlow: LegacyQuiverFlow,
    private val itemUseSellFlow: LegacyItemUseSellFlow
) {
    fun openInventory(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        var inventoryFilter = InventoryFilter()

        while (true) {
            player = normalizePlayerStorage(player, itemInstances)
            val allStacks = buildInventoryStacks(player, itemInstances)
            val stacks = applyInventoryFilter(allStacks, inventoryFilter)
            val slotUsed = InventorySystem.slotsUsed(player, itemInstances, engine.itemRegistry)
            val slotLimit = InventorySystem.inventoryLimit(player, itemInstances, engine.itemRegistry)
            val quiverCapacity = InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
            val quiverAmmo = InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
            val reserveAmmo = InventorySystem.inventoryArrowReserveCount(player, itemInstances, engine.itemRegistry)
            val hasQuiverMenu = quiverCapacity > 0 || quiverAmmo > 0 || reserveAmmo > 0
            if (allStacks.isEmpty() && !hasQuiverMenu) {
                println("Inventario vazio.")
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            println("\n=== Inventario ===")
            println("Slots: $slotUsed/$slotLimit")
            println("Filtros: ${inventoryFilterSummary(inventoryFilter)}")
            var nextOption = 1
            val quiverOption = if (hasQuiverMenu) nextOption++ else null
            val filterOption = if (allStacks.isNotEmpty()) nextOption++ else null
            if (hasQuiverMenu) {
                val activeAmmoName = player.selectedAmmoTemplateId?.let { templateId ->
                    quiverFlow.buildAmmoStacks(player.quiverInventory, itemInstances, player.selectedAmmoTemplateId)
                        .firstOrNull { it.templateId == templateId }
                        ?.item
                        ?.name
                } ?: "-"
                println("Aljava: $quiverAmmo/$quiverCapacity | Reserva: $reserveAmmo | Municao ativa: $activeAmmoName")
                println("${quiverOption}. Gerenciar aljava")
            }
            if (filterOption != null) {
                println("${filterOption}. Filtrar inventario")
            }
            if (allStacks.isNotEmpty() && stacks.isEmpty()) {
                println("Nenhum item corresponde aos filtros atuais.")
            }
            stacks.forEachIndexed { index, stack ->
                val qtyLabel = if (stack.quantity > 1) " x${stack.quantity}" else ""
                println("${nextOption + index}. ${detailSupport.itemDisplayLabel(stack.item)}$qtyLabel")
            }
            println("x. Voltar")

            val maxOption = nextOption + stacks.size - 1
            val choice = readMenuChoice("Escolha: ", 1, maxOption.coerceAtLeast(1))
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            if (quiverOption != null && choice == quiverOption) {
                val result = quiverFlow.openQuiverMenu(player, itemInstances)
                player = result.player
                itemInstances = result.itemInstances
                autoSave(state.copy(player = player, itemInstances = itemInstances))
                continue
            }

            if (filterOption != null && choice == filterOption) {
                inventoryFilter = openInventoryFilterMenu(inventoryFilter)
                continue
            }

            val selectedIndex = choice - nextOption
            if (selectedIndex !in stacks.indices) {
                continue
            }
            val selected = stacks[selectedIndex]
            val result = handleInventoryAction(player, itemInstances, selected)
            player = result.player
            itemInstances = result.itemInstances
            autoSave(state.copy(player = player, itemInstances = itemInstances))
        }
    }

    fun buildInventoryStacks(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): List<InventoryStack> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in player.inventory) {
            val key = InventorySystem.stackKey(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }

        return grouped.values.mapNotNull { ids ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            InventoryStack(
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<InventoryStack> { it.item.rarity.ordinal }
                .thenByDescending { it.item.powerScore }
                .thenBy { it.item.name.lowercase() }
        )
    }

    private fun applyInventoryFilter(
        stacks: List<InventoryStack>,
        filter: InventoryFilter
    ): List<InventoryStack> {
        return stacks.filter { stack ->
            val typeOk = filter.type == null || stack.item.type == filter.type
            val rarityOk = filter.minimumRarity == null || stack.item.rarity.ordinal >= filter.minimumRarity.ordinal
            typeOk && rarityOk
        }
    }

    private fun inventoryFilterSummary(filter: InventoryFilter): String {
        val typeLabel = when (filter.type) {
            null -> "todos"
            ItemType.EQUIPMENT -> "equipamentos"
            ItemType.CONSUMABLE -> "consumiveis"
            ItemType.MATERIAL -> "materiais"
        }
        val rarityLabel = filter.minimumRarity?.colorLabel ?: "qualquer raridade"
        return "tipo=$typeLabel | raridade min=$rarityLabel"
    }

    private fun openInventoryFilterMenu(current: InventoryFilter): InventoryFilter {
        var filter = current
        while (true) {
            println("\n=== Filtros do Inventario ===")
            println("Atual: ${inventoryFilterSummary(filter)}")
            println("1. Tipo")
            println("2. Raridade minima")
            println("3. Limpar filtros")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> {
                    println("\nTipo:")
                    println("1. Todos")
                    println("2. Equipamentos")
                    println("3. Consumiveis")
                    println("4. Materiais")
                    println("x. Voltar")
                    when (readMenuChoice("Escolha: ", 1, 4)) {
                        1 -> filter = filter.copy(type = null)
                        2 -> filter = filter.copy(type = ItemType.EQUIPMENT)
                        3 -> filter = filter.copy(type = ItemType.CONSUMABLE)
                        4 -> filter = filter.copy(type = ItemType.MATERIAL)
                        null -> {}
                    }
                }
                2 -> {
                    println("\nRaridade minima:")
                    println("1. Qualquer")
                    ItemRarity.entries.forEachIndexed { index, rarity ->
                        println("${index + 2}. ${rarity.colorLabel}")
                    }
                    println("x. Voltar")
                    when (val choice = readMenuChoice("Escolha: ", 1, ItemRarity.entries.size + 1)) {
                        1 -> filter = filter.copy(minimumRarity = null)
                        null -> {}
                        else -> {
                            val rarity = ItemRarity.entries.getOrNull(choice - 2)
                            if (rarity != null) {
                                filter = filter.copy(minimumRarity = rarity)
                            }
                        }
                    }
                }
                3 -> filter = InventoryFilter()
                null -> return filter
            }
        }
    }

    private fun handleInventoryAction(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        stack: InventoryStack
    ): UseItemResult {
        val itemId = stack.itemIds.firstOrNull() ?: return UseItemResult(player, itemInstances)
        val resolved = engine.itemResolver.resolve(itemId, itemInstances) ?: return UseItemResult(player, itemInstances)
        val forcedSaleValue = ClassQuestTagRules.forcedSellValue(resolved.tags)
        val saleValue = forcedSaleValue ?: engine.economyEngine.sellValue(
            itemValue = resolved.value,
            rarity = resolved.rarity,
            type = resolved.type,
            tags = resolved.tags
        )
        println("\nItem: ${detailSupport.itemDisplayLabel(resolved)}")
        println("Tipo: ${resolved.type.name.lowercase()} | Valor de venda por unidade: $saleValue")
        detailSupport.printInventoryItemDetails(player, itemInstances, stack, resolved)

        return when (resolved.type) {
            ItemType.CONSUMABLE -> {
                println("1. Usar")
                println("2. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        if (player.level < resolved.minLevel) {
                            println("Nivel insuficiente para usar este item (req ${resolved.minLevel}).")
                            UseItemResult(player, itemInstances)
                        } else {
                            itemUseSellFlow.useItem(player, itemInstances, itemId)
                        }
                    }
                    2 -> {
                        val qty = itemUseSellFlow.chooseSellQuantity(stack.quantity)
                        itemUseSellFlow.sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
            ItemType.EQUIPMENT -> {
                println("1. Equipar")
                println("2. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 2)) {
                    1 -> {
                        if (player.level < resolved.minLevel) {
                            println("Nivel insuficiente para equipar (req ${resolved.minLevel}).")
                            return UseItemResult(player, itemInstances)
                        }
                        val updated = equipmentFlow.equipItem(player, resolved, itemInstances)
                        UseItemResult(updated, itemInstances)
                    }
                    2 -> {
                        val qty = itemUseSellFlow.chooseSellQuantity(stack.quantity)
                        itemUseSellFlow.sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
            ItemType.MATERIAL -> {
                println("1. Vender")
                println("x. Voltar")
                when (readMenuChoice("Escolha: ", 1, 1)) {
                    1 -> {
                        val qty = itemUseSellFlow.chooseSellQuantity(stack.quantity)
                        itemUseSellFlow.sellInventoryItem(player, itemInstances, stack.itemIds, resolved.name, saleValue, qty)
                    }
                    null -> UseItemResult(player, itemInstances)
                    else -> UseItemResult(player, itemInstances)
                }
            }
        }
    }
}
