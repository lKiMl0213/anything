package rpg.cli

import rpg.cli.model.UseItemResult
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.model.PlayerState

internal class LegacyItemUseSellFlow(
    private val engine: GameEngine,
    private val choose: (label: String, options: List<String>, nameOf: (String) -> String) -> String,
    private val readInt: (prompt: String, min: Int, max: Int) -> Int,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> ComputedStats,
    private val applyHealing: (
        player: PlayerState,
        hp: Double,
        mp: Double,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val applyRoomEffect: (
        player: PlayerState,
        multiplier: Double,
        rooms: Int
    ) -> PlayerState,
    private val normalizePlayerStorage: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> PlayerState,
    private val onGoldEarned: (player: PlayerState, amount: Long) -> PlayerState
) {
    fun useItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): UseItemResult {
        val consumables = player.inventory.filter { id ->
            engine.itemResolver.resolve(id, itemInstances)?.type == rpg.model.ItemType.CONSUMABLE
        }
        if (consumables.isEmpty()) {
            println("Nenhum consumivel disponivel.")
            return UseItemResult(player, itemInstances)
        }

        val itemId = choose("Consumivel", consumables) { id ->
            engine.itemResolver.resolve(id, itemInstances)?.name ?: id
        }
        return useItem(player, itemInstances, itemId)
    }

    fun useItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemId: String
    ): UseItemResult {
        val item = engine.itemResolver.resolve(itemId, itemInstances) ?: return UseItemResult(player, itemInstances)
        if (item.type != rpg.model.ItemType.CONSUMABLE) return UseItemResult(player, itemInstances)

        val stats = computePlayerStats(player, itemInstances)
        val hpPctRestore = stats.derived.hpMax * (item.effects.hpRestorePct / 100.0)
        val mpPctRestore = stats.derived.mpMax * (item.effects.mpRestorePct / 100.0)
        var hpRestored = item.effects.hpRestore + hpPctRestore
        var mpRestored = item.effects.mpRestore + mpPctRestore
        if (item.effects.fullRestore) {
            hpRestored = stats.derived.hpMax
            mpRestored = stats.derived.mpMax
        }

        var updatedPlayer = applyHealing(player, hpRestored, mpRestored, itemInstances)
        if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
            val mult = (1.0 + item.effects.roomAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = applyRoomEffect(updatedPlayer, mult, item.effects.roomAttributeDurationRooms)
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            val mult = (1.0 + item.effects.runAttributeMultiplierPct / 100.0).coerceAtLeast(0.1)
            updatedPlayer = updatedPlayer.copy(runAttrMultiplier = (updatedPlayer.runAttrMultiplier * mult).coerceAtLeast(0.1))
        }

        val inventory = updatedPlayer.inventory.toMutableList()
        inventory.remove(itemId)

        val updatedInstances = if (itemInstances.containsKey(itemId)) {
            itemInstances - itemId
        } else {
            itemInstances
        }

        if (item.effects.clearNegativeStatuses || item.effects.statusImmunitySeconds > 0.0) {
            println("Esse efeito defensivo e aplicado apenas em combate.")
        }
        println("Usou ${item.name}.")
        return UseItemResult(updatedPlayer.copy(inventory = inventory), updatedInstances)
    }

    fun sellInventoryItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ): UseItemResult {
        val inventory = player.inventory.toMutableList()
        val toSell = itemIds.take(quantity.coerceAtLeast(1))
        if (toSell.isEmpty()) {
            return UseItemResult(player, itemInstances)
        }
        var sold = 0
        val updatedInstances = itemInstances.toMutableMap()
        for (itemId in toSell) {
            if (inventory.remove(itemId)) {
                sold++
                if (updatedInstances.containsKey(itemId)) {
                    updatedInstances.remove(itemId)
                }
            }
        }
        if (sold <= 0) {
            return UseItemResult(player, itemInstances)
        }
        var updatedPlayer = player.copy(
            inventory = inventory,
            gold = player.gold + saleValue * sold
        )
        updatedPlayer = onGoldEarned(updatedPlayer, (saleValue * sold).toLong())
        println("Vendeu $itemName x$sold por ${saleValue * sold} ouro.")
        return UseItemResult(updatedPlayer, updatedInstances.toMap())
    }

    fun sellQuiverAmmo(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        itemName: String,
        saleValue: Int,
        quantity: Int
    ): UseItemResult {
        val quiverInventory = player.quiverInventory.toMutableList()
        val toSell = itemIds.take(quantity.coerceAtLeast(1))
        if (toSell.isEmpty()) {
            return UseItemResult(player, itemInstances)
        }

        var sold = 0
        val updatedInstances = itemInstances.toMutableMap()
        for (itemId in toSell) {
            if (quiverInventory.remove(itemId)) {
                sold++
                if (updatedInstances.containsKey(itemId)) {
                    updatedInstances.remove(itemId)
                }
            }
        }
        if (sold <= 0) {
            return UseItemResult(player, itemInstances)
        }

        var updatedPlayer = player.copy(
            quiverInventory = quiverInventory,
            gold = player.gold + saleValue * sold
        )
        updatedPlayer = normalizePlayerStorage(updatedPlayer, updatedInstances)
        updatedPlayer = onGoldEarned(updatedPlayer, (saleValue * sold).toLong())
        println("Vendeu $itemName x$sold por ${saleValue * sold} ouro.")
        return UseItemResult(updatedPlayer, updatedInstances.toMap())
    }

    fun chooseSellQuantity(maxQuantity: Int): Int {
        if (maxQuantity <= 1) return 1
        return readInt("Quantidade para vender (1-$maxQuantity): ", 1, maxQuantity)
    }
}
