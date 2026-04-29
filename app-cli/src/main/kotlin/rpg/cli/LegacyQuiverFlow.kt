// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.cli.model.*
import rpg.classsystem.RaceBonusSupport
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.EquipSlot
import rpg.model.PlayerState

internal class LegacyQuiverFlow(
    private val engine: GameEngine,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val normalizePlayerStorage: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val itemDisplayLabel: (rpg.item.ResolvedItem) -> String,
    private val sellInventoryItem: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ) -> UseItemResult,
    private val sellQuiverAmmo: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ) -> UseItemResult,
    private val chooseSellQuantity: (maxQuantity: Int) -> Int
) {
    fun buildAmmoStacks(
        itemIds: List<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        selectedTemplateId: String?
    ): List<AmmoStack> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in itemIds) {
            if (!InventorySystem.isArrowAmmo(itemId, itemInstances, engine.itemRegistry)) continue
            val templateId = InventorySystem.ammoTemplateId(itemId, itemInstances, engine.itemRegistry)
            grouped.getOrPut(templateId) { mutableListOf() }.add(itemId)
        }

        return grouped.mapNotNull { (templateId, ids) ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, itemInstances) ?: return@mapNotNull null
            AmmoStack(
                templateId = templateId,
                sampleItemId = sampleId,
                quantity = ids.size,
                itemIds = ids.toList(),
                item = resolved
            )
        }.sortedWith(
            compareByDescending<AmmoStack> { it.templateId == selectedTemplateId?.trim()?.lowercase() }
                .thenBy { it.item.name.lowercase() }
        )
    }

    fun openQuiverMenu(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        var updatedPlayer = normalizePlayerStorage(player, itemInstances)
        var updatedInstances = itemInstances

        while (true) {
            updatedPlayer = normalizePlayerStorage(updatedPlayer, updatedInstances)
            val quiverName = updatedPlayer.equipped[EquipSlot.ALJAVA.name]?.let { quiverId ->
                engine.itemResolver.resolve(quiverId, updatedInstances)?.name ?: quiverId
            } ?: "Nenhuma"
            val quiverCapacity = InventorySystem.quiverCapacity(updatedPlayer, updatedInstances, engine.itemRegistry)
            val quiverStacks = buildAmmoStacks(
                updatedPlayer.quiverInventory,
                updatedInstances,
                updatedPlayer.selectedAmmoTemplateId
            )
            val reserveStacks = buildAmmoStacks(
                updatedPlayer.inventory,
                updatedInstances,
                updatedPlayer.selectedAmmoTemplateId
            )

            println("\n=== Aljava ===")
            println("Aljava equipada: $quiverName")
            println("Capacidade: ${updatedPlayer.quiverInventory.size}/$quiverCapacity")
            val activeAmmoLabel = quiverStacks
                .firstOrNull { it.templateId == updatedPlayer.selectedAmmoTemplateId }
                ?.item
                ?.name
                ?: "-"
            println("Municao ativa: $activeAmmoLabel")

            if (quiverStacks.isNotEmpty()) {
                println("Carregadas:")
                quiverStacks.forEachIndexed { index, stack ->
                    val marker = if (stack.templateId == updatedPlayer.selectedAmmoTemplateId) "[ATIVA] " else ""
                    println("${index + 1}. $marker${itemDisplayLabel(stack.item)} x${stack.quantity}")
                }
            } else {
                println("Carregadas: -")
            }

            if (reserveStacks.isNotEmpty()) {
                println("Reserva:")
                reserveStacks.forEachIndexed { index, stack ->
                    println("${index + 1}. ${itemDisplayLabel(stack.item)} x${stack.quantity}")
                }
            } else {
                println("Reserva: -")
            }

            var option = 1
            val selectAmmoOption = if (quiverStacks.isNotEmpty()) option++ else null
            val loadAmmoOption = if (quiverCapacity > updatedPlayer.quiverInventory.size && reserveStacks.isNotEmpty()) option++ else null
            val unloadAmmoOption = if (quiverStacks.isNotEmpty()) option++ else null
            val sellReserveOption = if (reserveStacks.isNotEmpty()) option++ else null
            val sellLoadedOption = if (quiverStacks.isNotEmpty()) option++ else null

            if (selectAmmoOption != null) println("$selectAmmoOption. Selecionar municao ativa")
            if (loadAmmoOption != null) println("$loadAmmoOption. Carregar da reserva")
            if (unloadAmmoOption != null) println("$unloadAmmoOption. Retirar da aljava")
            if (sellReserveOption != null) println("$sellReserveOption. Vender da reserva")
            if (sellLoadedOption != null) println("$sellLoadedOption. Vender da aljava")
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, (option - 1).coerceAtLeast(1)) ?: return UseItemResult(
                updatedPlayer,
                updatedInstances
            )
            when (choice) {
                selectAmmoOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Municao ativa")
                    updatedPlayer = InventorySystem.selectAmmoTemplate(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.templateId
                    )
                    println("Municao ativa alterada para ${stack.item.name}.")
                }
                loadAmmoOption -> {
                    val stack = chooseAmmoStack(reserveStacks, "Carregar")
                    val maxQty = minOf(
                        stack.quantity,
                        (quiverCapacity - updatedPlayer.quiverInventory.size).coerceAtLeast(0)
                    )
                    if (maxQty <= 0) {
                        println("A aljava esta cheia.")
                        continue
                    }
                    val qty = chooseTransferQuantity("Quantidade para carregar", maxQty)
                    val result = InventorySystem.moveAmmoToQuiver(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.itemIds.take(qty)
                    )
                    updatedPlayer = updatedPlayer.copy(
                        inventory = result.inventory,
                        quiverInventory = result.quiverInventory,
                        selectedAmmoTemplateId = result.selectedAmmoTemplateId
                    )
                    println("Carregou ${stack.item.name} x${result.accepted.size}.")
                }
                unloadAmmoOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Retirar")
                    val qty = chooseTransferQuantity("Quantidade para retirar", stack.quantity)
                    val result = InventorySystem.unloadAmmoFromQuiver(
                        updatedPlayer,
                        updatedInstances,
                        engine.itemRegistry,
                        stack.itemIds.take(qty)
                    )
                    updatedPlayer = updatedPlayer.copy(
                        inventory = result.inventory,
                        quiverInventory = result.quiverInventory,
                        selectedAmmoTemplateId = result.selectedAmmoTemplateId
                    )
                    if (result.accepted.isNotEmpty()) {
                        println("Retirou ${stack.item.name} x${result.accepted.size}.")
                    }
                    if (result.rejected.isNotEmpty()) {
                        println("Inventario sem espaco para ${result.rejected.size} flecha(s).")
                    }
                }
                sellReserveOption -> {
                    val stack = chooseAmmoStack(reserveStacks, "Vender da reserva")
                    val qty = chooseSellQuantity(stack.quantity)
                    val baseSaleValue = engine.economyEngine.sellValue(
                        itemValue = stack.item.value,
                        rarity = stack.item.rarity,
                        type = stack.item.type,
                        tags = stack.item.tags
                    )
                    val raceDef = runCatching { engine.classSystem.raceDef(updatedPlayer.raceId) }.getOrNull()
                    val raceBonusPct = RaceBonusSupport.tradeSellBonusPct(raceDef)
                    val saleValue = RaceBonusSupport.applyTradeSellBonus(baseSaleValue, raceBonusPct)
                    val result = sellInventoryItem(
                        updatedPlayer,
                        updatedInstances,
                        stack.itemIds,
                        stack.item.name,
                        saleValue,
                        qty
                    )
                    updatedPlayer = result.player
                    updatedInstances = result.itemInstances
                }
                sellLoadedOption -> {
                    val stack = chooseAmmoStack(quiverStacks, "Vender da aljava")
                    val qty = chooseSellQuantity(stack.quantity)
                    val baseSaleValue = engine.economyEngine.sellValue(
                        itemValue = stack.item.value,
                        rarity = stack.item.rarity,
                        type = stack.item.type,
                        tags = stack.item.tags
                    )
                    val raceDef = runCatching { engine.classSystem.raceDef(updatedPlayer.raceId) }.getOrNull()
                    val raceBonusPct = RaceBonusSupport.tradeSellBonusPct(raceDef)
                    val saleValue = RaceBonusSupport.applyTradeSellBonus(baseSaleValue, raceBonusPct)
                    val result = sellQuiverAmmo(
                        updatedPlayer,
                        updatedInstances,
                        stack.itemIds,
                        stack.item.name,
                        saleValue,
                        qty
                    )
                    updatedPlayer = result.player
                    updatedInstances = result.itemInstances
                }
            }
        }
    }

    private fun chooseAmmoStack(options: List<AmmoStack>, label: String): AmmoStack {
        if (options.isEmpty()) error("Nenhuma municao disponivel para $label")
        println("\n$label:")
        options.forEachIndexed { index, stack ->
            println("${index + 1}. ${itemDisplayLabel(stack.item)} x${stack.quantity}")
        }
        val choice = readInt("Escolha: ", 1, options.size)
        return options[choice - 1]
    }

    private fun chooseTransferQuantity(label: String, maxQuantity: Int): Int {
        if (maxQuantity <= 1) return 1
        return readInt("$label (1-$maxQuantity): ", 1, maxQuantity)
    }
}
