package rpg.cli

import rpg.engine.GameEngine
import rpg.events.EventContext
import rpg.events.EventDefinition
import rpg.events.EventExecutor
import rpg.inventory.InventorySystem
import rpg.model.PlayerState

internal class DungeonEventSupport(
    private val engine: GameEngine,
    private val itemName: (String) -> String,
    private val canonicalItemId: (
        itemId: String,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> String,
    private val clampPlayerResources: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val onGoldEarned: (player: PlayerState, amount: Long) -> PlayerState,
    private val onGoldSpent: (player: PlayerState, amount: Long) -> PlayerState,
    private val emit: (String) -> Unit
) {
    fun hasDirectDamageRisk(event: EventDefinition): Boolean {
        return event.effects.any { it is rpg.events.EventEffect.DamagePercentCurrent }
    }

    fun applyEventWithFeedback(
        before: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        event: EventDefinition,
        context: EventContext
    ): PlayerState {
        val raw = EventExecutor.execute(event, before, context)
        val addedIds = extractAddedInventoryIds(before.inventory, raw.inventory)
        val inserted = InventorySystem.addItemsWithLimit(
            player = before.copy(
                equipped = raw.equipped,
                inventory = before.inventory
            ),
            itemInstances = itemInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = addedIds
        )
        val after = raw.copy(
            inventory = inserted.inventory,
            quiverInventory = inserted.quiverInventory,
            selectedAmmoTemplateId = inserted.selectedAmmoTemplateId
        )
        val hpDelta = after.currentHp - before.currentHp
        val mpDelta = after.currentMp - before.currentMp
        val goldDelta = after.gold - before.gold
        if (hpDelta != 0.0) {
            val label = if (hpDelta > 0) "+" else ""
            emit("Efeito: HP $label${format(hpDelta)}")
        }
        if (mpDelta != 0.0) {
            val label = if (mpDelta > 0) "+" else ""
            emit("Efeito: MP $label${format(mpDelta)}")
        }
        if (goldDelta != 0) {
            val label = if (goldDelta > 0) "+" else ""
            emit("Efeito: Ouro $label$goldDelta")
        }
        if (inserted.accepted.isNotEmpty()) {
            val grouped = inserted.accepted.groupingBy { canonicalItemId(it, itemInstances) }.eachCount()
            val labels = grouped.entries.joinToString(", ") { "${itemName(it.key)} x${it.value}" }
            emit("Itens obtidos: $labels")
        }
        if (inserted.rejected.isNotEmpty()) {
            emit("Inventario cheio: ${inserted.rejected.size} item(ns) do evento foram descartados.")
        }
        var updatedPlayer = clampPlayerResources(after, itemInstances)
        if (goldDelta > 0) {
            updatedPlayer = onGoldEarned(updatedPlayer, goldDelta.toLong())
        } else if (goldDelta < 0) {
            updatedPlayer = onGoldSpent(updatedPlayer, -goldDelta.toLong())
        }
        return updatedPlayer
    }

    private fun extractAddedInventoryIds(before: List<String>, after: List<String>): List<String> {
        val remaining = before.groupingBy { it }.eachCount().toMutableMap()
        val added = mutableListOf<String>()
        for (id in after) {
            val count = remaining[id] ?: 0
            if (count > 0) {
                remaining[id] = count - 1
            } else {
                added += id
            }
        }
        return added
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
