package rpg.presentation

import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.inventory.InventoryQueryService
import rpg.item.ItemRarity
import rpg.model.ItemType
import rpg.presentation.model.MenuScreenViewModel
import rpg.presentation.model.ScreenOptionViewModel
import rpg.presentation.model.ScreenViewModel

internal class InventoryScreenPresenter(
    private val inventoryQueryService: InventoryQueryService,
    private val support: PresentationSupport
) {
    fun presentInventory(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Inventario")
        val allStacks = inventoryQueryService.allInventoryStacks(state)
        val stacks = inventoryQueryService.inventoryStacks(state, session.inventoryFilter)
        val quiverCapacityLabel = inventoryQueryService.quiverCapacityLabel(state)
        val reserveAmmo = inventoryQueryService.reserveAmmoCount(state)
        val loadedAmmo = inventoryQueryService.quiverLoadedStacks(state)
        val hasQuiverMenu = loadedAmmo.isNotEmpty() || reserveAmmo > 0 || !quiverCapacityLabel.endsWith("/0")

        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        if (hasQuiverMenu) {
            options += ScreenOptionViewModel(index.toString(), "Gerenciar aljava", GameAction.OpenQuiver)
            index++
        }
        if (allStacks.isNotEmpty()) {
            options += ScreenOptionViewModel(index.toString(), "Filtrar inventario", GameAction.OpenInventoryFilters)
            index++
        }
        stacks.forEach { stack ->
            val qtyLabel = if (stack.quantity > 1) " x${stack.quantity}" else ""
            options += ScreenOptionViewModel(
                index.toString(),
                "${inventoryQueryService.support().itemDisplayLabel(stack.item)}$qtyLabel",
                GameAction.InspectInventoryItem(stack.sampleItemId)
            )
            index++
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf(
            "Slots: ${inventoryQueryService.inventoryCapacityLabel(state)}",
            "Filtros: ${inventoryQueryService.inventoryFilterSummary(session.inventoryFilter)}"
        )
        if (hasQuiverMenu) {
            body += "Aljava: $quiverCapacityLabel | Reserva: $reserveAmmo | Municao ativa: ${inventoryQueryService.activeAmmoName(state)}"
        }
        if (allStacks.isNotEmpty() && stacks.isEmpty()) {
            body += "Nenhum item corresponde aos filtros atuais."
        }
        if (allStacks.isEmpty() && !hasQuiverMenu) {
            body += "Inventario vazio."
        }
        return MenuScreenViewModel(
            title = "Inventario",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentInventoryFilters(session: GameSession): ScreenViewModel {
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        options += ScreenOptionViewModel(index++.toString(), "Tipo: Todos", GameAction.SetInventoryFilterType(null))
        options += ScreenOptionViewModel(index++.toString(), "Tipo: Equipamentos", GameAction.SetInventoryFilterType(ItemType.EQUIPMENT))
        options += ScreenOptionViewModel(index++.toString(), "Tipo: Consumiveis", GameAction.SetInventoryFilterType(ItemType.CONSUMABLE))
        options += ScreenOptionViewModel(index++.toString(), "Tipo: Materiais", GameAction.SetInventoryFilterType(ItemType.MATERIAL))
        options += ScreenOptionViewModel(index++.toString(), "Raridade: Qualquer", GameAction.SetInventoryMinimumRarity(null))
        ItemRarity.entries.forEach { rarity ->
            options += ScreenOptionViewModel(index++.toString(), "Raridade: ${rarity.colorLabel}", GameAction.SetInventoryMinimumRarity(rarity))
        }
        options += ScreenOptionViewModel(index++.toString(), "Limpar filtros", GameAction.ClearInventoryFilters)
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Filtros do Inventario",
            bodyLines = listOf("Atual: ${inventoryQueryService.inventoryFilterSummary(session.inventoryFilter)}"),
            options = options,
            messages = session.messages
        )
    }

    fun presentInventoryItemDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Item")
        val itemId = session.selectedInventoryItemId ?: return support.presentMissingState("Item")
        val detail = inventoryQueryService.inventoryItemDetail(state, itemId)
            ?: return MenuScreenViewModel(
                title = "Item",
                bodyLines = listOf("Esse item nao esta mais no inventario."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1
        when (detail.item.type) {
            ItemType.EQUIPMENT -> {
                options += ScreenOptionViewModel(index++.toString(), "Equipar", GameAction.EquipInventoryItem(detail.sampleItemId))
                options += ScreenOptionViewModel(index++.toString(), "Vender 1 unidade", GameAction.SellInventoryItem(detail.sampleItemId))
            }
            ItemType.CONSUMABLE -> {
                options += ScreenOptionViewModel(index++.toString(), "Usar", GameAction.UseInventoryItem(detail.sampleItemId))
                options += ScreenOptionViewModel(index++.toString(), "Vender 1 unidade", GameAction.SellInventoryItem(detail.sampleItemId))
            }
            ItemType.MATERIAL -> {
                options += ScreenOptionViewModel(index++.toString(), "Vender 1 unidade", GameAction.SellInventoryItem(detail.sampleItemId))
            }
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        val body = mutableListOf("Item: ${inventoryQueryService.support().itemDisplayLabel(detail.item)}")
        body += detail.detailLines
        detail.comparisonSummary?.let(body::add)
        if (detail.quantity > 1) {
            body += "Quantidade na stack: ${detail.quantity}"
        }
        return MenuScreenViewModel(
            title = "Detalhes do Item",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }

    fun presentEquipped(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Equipados")
        val slots = inventoryQueryService.equippedSlots(state)
        val options = slots.mapIndexed { index, slot ->
            ScreenOptionViewModel((index + 1).toString(), "${slot.label} -> ${slot.displayLabel}", GameAction.InspectEquippedSlot(slot.slotKey))
        } + ScreenOptionViewModel("x", "Voltar", GameAction.Back)
        return MenuScreenViewModel(
            title = "Equipados",
            summary = support.playerSummary(state),
            bodyLines = listOf("Resumo: ${inventoryQueryService.slotSummary(state)}"),
            options = options,
            messages = session.messages
        )
    }

    fun presentEquippedItemDetail(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Equipado")
        val slotKey = session.selectedEquipmentSlot ?: return support.presentMissingState("Equipado")
        val detail = inventoryQueryService.equippedDetail(state, slotKey)
            ?: return MenuScreenViewModel(
                title = "Equipado",
                bodyLines = listOf("Slot vazio ou bloqueado."),
                options = listOf(ScreenOptionViewModel("x", "Voltar", GameAction.Back)),
                messages = session.messages
            )
        return MenuScreenViewModel(
            title = inventoryQueryService.support().equippedSlotLabel(slotKey),
            summary = support.playerSummary(state),
            bodyLines = detail.detailLines + detail.removalSummary,
            options = listOf(
                ScreenOptionViewModel("1", "Desequipar", GameAction.UnequipSlot(slotKey)),
                ScreenOptionViewModel("x", "Voltar", GameAction.Back)
            ),
            messages = session.messages
        )
    }

    fun presentQuiver(session: GameSession): ScreenViewModel {
        val state = session.gameState ?: return support.presentMissingState("Aljava")
        val loaded = inventoryQueryService.quiverLoadedStacks(state)
        val reserve = inventoryQueryService.quiverReserveStacks(state)
        val options = mutableListOf<ScreenOptionViewModel>()
        var index = 1

        loaded.forEach { stack ->
            options += ScreenOptionViewModel(
                index++.toString(),
                "Ativar ${inventoryQueryService.support().itemDisplayLabel(stack.item)}",
                GameAction.SelectActiveAmmo(stack.templateId)
            )
        }
        reserve.forEach { stack ->
            options += ScreenOptionViewModel(
                index++.toString(),
                "Carregar 1 ${inventoryQueryService.support().itemDisplayLabel(stack.item)}",
                GameAction.LoadAmmoToQuiver(stack.sampleItemId)
            )
        }
        loaded.forEach { stack ->
            options += ScreenOptionViewModel(
                index++.toString(),
                "Retirar 1 ${inventoryQueryService.support().itemDisplayLabel(stack.item)}",
                GameAction.UnloadAmmoFromQuiver(stack.sampleItemId)
            )
        }
        loaded.forEach { stack ->
            options += ScreenOptionViewModel(
                index++.toString(),
                "Vender 1 carregada ${inventoryQueryService.support().itemDisplayLabel(stack.item)}",
                GameAction.SellLoadedAmmo(stack.sampleItemId)
            )
        }
        options += ScreenOptionViewModel("x", "Voltar", GameAction.Back)

        val body = mutableListOf(
            "Capacidade: ${inventoryQueryService.quiverCapacityLabel(state)}",
            "Municao ativa: ${inventoryQueryService.activeAmmoName(state)}"
        )
        body += if (loaded.isEmpty()) {
            listOf("Carregadas: -")
        } else {
            listOf("Carregadas:") + loaded.map {
                val marker = if (it.templateId == state.player.selectedAmmoTemplateId) "[ATIVA] " else ""
                "$marker${inventoryQueryService.support().itemDisplayLabel(it.item)} x${it.quantity}"
            }
        }
        body += if (reserve.isEmpty()) {
            listOf("Reserva: -")
        } else {
            listOf("Reserva:") + reserve.map {
                "${inventoryQueryService.support().itemDisplayLabel(it.item)} x${it.quantity}"
            }
        }
        return MenuScreenViewModel(
            title = "Aljava",
            summary = support.playerSummary(state),
            bodyLines = body,
            options = options,
            messages = session.messages
        )
    }
}
