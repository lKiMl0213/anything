package rpg.cli

import kotlin.math.max
import kotlin.math.min
import rpg.cli.model.ShopPurchaseResult
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.ShopCurrency

internal class LegacyCityServicesFlow(
    private val engine: GameEngine,
    private val repo: rpg.io.DataRepository,
    private val tavernRestHealPct: Double,
    private val deathDebuffBaseMinutes: Double,
    private val deathDebuffExtraMinutes: Double,
    private val deathXpPenaltyPct: Double,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val autoSave: (GameState) -> Unit,
    private val computePlayerStats: (PlayerState, Map<String, rpg.model.ItemInstance>) -> rpg.engine.ComputedStats,
    private val applyGoldSpent: (player: PlayerState, amount: Long) -> PlayerState,
    private val applyFullRestSleep: (player: PlayerState) -> PlayerState
) {
    fun openCityMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            println("\n=== Cidade ===")
            println("1. Taverna")
            println("2. Loja de Ouro")
            println("3. Loja de Cash")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 3)) {
                1 -> updated = visitTavern(updated)
                2 -> updated = openShop(updated)
                3 -> updated = openCashShop(updated)
                null -> return updated
            }
        }
    }

    private fun openShop(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        val entries = repo.shopEntries.values
            .filter { it.enabled && it.currency == ShopCurrency.GOLD }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))
        if (entries.isEmpty()) {
            println("Loja de ouro sem itens configurados.")
            return state
        }

        while (true) {
            println("\n=== Loja de Ouro ===")
            println("Ouro atual: ${player.gold}")
            entries.forEachIndexed { index, entry ->
                val reqLevel = requiredLevelForShopEntry(entry)
                val itemName = shopEntryItemName(entry)
                val lock = if (player.level < reqLevel) " [bloqueado]" else ""
                println(
                    "${index + 1}. $itemName x${entry.quantity} - ${entry.price} ouro " +
                        "(lvl req $reqLevel)$lock"
                )
            }
            println("x. Voltar")

            val choice = readMenuChoice("Escolha: ", 1, entries.size)
            if (choice == null) {
                val updated = state.copy(player = player, itemInstances = itemInstances)
                autoSave(updated)
                return updated
            }

            val selected = entries[choice - 1]
            if (!showShopEntryDetails(player, selected)) {
                continue
            }
            val purchase = buyShopEntry(player, itemInstances, selected)
            println(purchase.message)
            if (purchase.success) {
                var updatedPlayer = purchase.player
                val spentGold = (player.gold - updatedPlayer.gold).coerceAtLeast(0)
                if (spentGold > 0) {
                    updatedPlayer = applyGoldSpent(updatedPlayer, spentGold.toLong())
                }
                player = updatedPlayer
                itemInstances = purchase.itemInstances
                autoSave(state.copy(player = player, itemInstances = itemInstances))
            }
        }
    }

    private fun openCashShop(state: GameState): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        val entries = repo.shopEntries.values
            .filter { it.enabled && it.currency == ShopCurrency.CASH }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))
        val packs = repo.cashPacks.values.filter { it.enabled }.sortedBy { it.premiumCashAmount }

        while (true) {
            println("\n=== Loja CASH ===")
            println("CASH atual: ${player.premiumCash}")
            println("1. Comprar pacote CASH (simulacao client-side)")
            println("2. Comprar itens CASH")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> {
                    if (packs.isEmpty()) {
                        println("Nenhum pacote CASH configurado.")
                        continue
                    }
                    println("\nPacotes disponiveis:")
                    packs.forEachIndexed { index, pack ->
                        val label = if (pack.platformPriceLabel.isBlank()) "sem preco" else pack.platformPriceLabel
                        println("${index + 1}. ${pack.name} -> +${pack.premiumCashAmount} CASH ($label)")
                    }
                    println("x. Voltar")
                    val choice = readMenuChoice("Escolha: ", 1, packs.size) ?: continue
                    val chosen = packs[choice - 1]
                    player = player.copy(premiumCash = player.premiumCash + chosen.premiumCashAmount)
                    println("Compra simulada concluida: +${chosen.premiumCashAmount} CASH.")
                    autoSave(state.copy(player = player, itemInstances = itemInstances))
                }

                2 -> {
                    if (entries.isEmpty()) {
                        println("Nenhum item CASH configurado.")
                        continue
                    }
                    while (true) {
                        println("\nItens CASH (saldo: ${player.premiumCash}):")
                        entries.forEachIndexed { index, entry ->
                            val reqLevel = requiredLevelForShopEntry(entry)
                            val itemName = shopEntryItemName(entry)
                            val lock = if (player.level < reqLevel) " [bloqueado]" else ""
                            println(
                                "${index + 1}. $itemName x${entry.quantity} - ${entry.price} CASH " +
                                    "(lvl req $reqLevel)$lock"
                            )
                        }
                        println("x. Voltar")
                        val choice = readMenuChoice("Escolha: ", 1, entries.size) ?: break

                        val selected = entries[choice - 1]
                        if (!showShopEntryDetails(player, selected)) {
                            continue
                        }
                        val purchase = buyShopEntry(player, itemInstances, selected)
                        println(purchase.message)
                        if (purchase.success) {
                            player = purchase.player
                            itemInstances = purchase.itemInstances
                            autoSave(state.copy(player = player, itemInstances = itemInstances))
                        }
                    }
                }

                null -> {
                    val updated = state.copy(player = player, itemInstances = itemInstances)
                    autoSave(updated)
                    return updated
                }
            }
        }
    }

    private fun requiredLevelForShopEntry(entry: rpg.model.ShopEntryDef): Int {
        val itemReq = engine.itemRegistry.item(entry.itemId)?.minLevel
            ?: engine.itemRegistry.template(entry.itemId)?.minLevel
            ?: 1
        return max(entry.minPlayerLevel, itemReq)
    }

    private fun showShopEntryDetails(
        player: PlayerState,
        entry: rpg.model.ShopEntryDef
    ): Boolean {
        val itemName = shopEntryItemName(entry)
        val requiredLevel = requiredLevelForShopEntry(entry)
        val currencyLabel = if (entry.currency == ShopCurrency.GOLD) "ouro" else "CASH"
        val balance = if (entry.currency == ShopCurrency.GOLD) player.gold else player.premiumCash
        val description = shopEntryDescription(entry)
        println("\n=== Item da Loja ===")
        println("Item: $itemName x${entry.quantity.coerceAtLeast(1)}")
        println("Preco: ${entry.price} $currencyLabel")
        println("Saldo atual: $balance $currencyLabel")
        println("Nivel requerido: $requiredLevel")
        if (description.isNotBlank()) {
            println("Descricao: $description")
        }
        if (player.level < requiredLevel) {
            println("Status: bloqueado por nivel.")
        }
        println("1. Comprar")
        println("x. Voltar")
        return readMenuChoice("Escolha: ", 1, 1) == 1
    }

    private fun shopEntryItemName(entry: rpg.model.ShopEntryDef): String {
        val resolvedName = engine.itemRegistry.item(entry.itemId)?.name
            ?: engine.itemRegistry.template(entry.itemId)?.name
        return if (resolvedName.isNullOrBlank()) entry.name else resolvedName
    }

    private fun shopEntryDescription(entry: rpg.model.ShopEntryDef): String {
        if (entry.description.isNotBlank()) return entry.description
        return engine.itemRegistry.entry(entry.itemId)?.description ?: ""
    }

    private fun buyShopEntry(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        entry: rpg.model.ShopEntryDef
    ): ShopPurchaseResult {
        val requiredLevel = requiredLevelForShopEntry(entry)
        if (player.level < requiredLevel) {
            return ShopPurchaseResult(
                success = false,
                player = player,
                itemInstances = itemInstances,
                message = "Nivel insuficiente. Requer nivel $requiredLevel."
            )
        }

        when (entry.currency) {
            ShopCurrency.GOLD -> {
                if (player.gold < entry.price) {
                    return ShopPurchaseResult(false, player, itemInstances, "Ouro insuficiente.")
                }
            }

            ShopCurrency.CASH -> {
                if (player.premiumCash < entry.price) {
                    return ShopPurchaseResult(false, player, itemInstances, "CASH insuficiente.")
                }
            }
        }

        val qty = entry.quantity.coerceAtLeast(1)
        val workingInstances = itemInstances.toMutableMap()
        val incoming = mutableListOf<String>()

        repeat(qty) {
            if (engine.itemRegistry.isTemplate(entry.itemId)) {
                val template = engine.itemRegistry.template(entry.itemId)
                    ?: return ShopPurchaseResult(
                        false,
                        player,
                        itemInstances,
                        "Template de item invalido na loja: ${entry.itemId}."
                    )
                val generated = engine.itemEngine.generateFromTemplate(
                    template = template,
                    level = max(player.level, template.minLevel),
                    rarity = template.rarity
                )
                workingInstances[generated.id] = generated
                incoming += generated.id
            } else {
                if (engine.itemRegistry.item(entry.itemId) == null) {
                    return ShopPurchaseResult(
                        false,
                        player,
                        itemInstances,
                        "Item invalido na loja: ${entry.itemId}."
                    )
                }
                incoming += entry.itemId
            }
        }

        val insertion = rpg.inventory.InventorySystem.addItemsWithLimit(
            player = player,
            itemInstances = workingInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = incoming
        )
        if (insertion.rejected.isNotEmpty()) {
            return ShopPurchaseResult(
                success = false,
                player = player,
                itemInstances = itemInstances,
                message = "Inventario sem espaco para essa compra."
            )
        }

        var updatedPlayer = player.copy(
            inventory = insertion.inventory,
            quiverInventory = insertion.quiverInventory,
            selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
        )
        updatedPlayer = when (entry.currency) {
            ShopCurrency.GOLD -> updatedPlayer.copy(gold = updatedPlayer.gold - entry.price)
            ShopCurrency.CASH -> updatedPlayer.copy(premiumCash = updatedPlayer.premiumCash - entry.price)
        }
        val currency = if (entry.currency == ShopCurrency.GOLD) "ouro" else "CASH"
        val itemName = shopEntryItemName(entry)
        return ShopPurchaseResult(
            success = true,
            player = updatedPlayer,
            itemInstances = workingInstances.toMap(),
            message = "Compra concluida: $itemName x$qty por ${entry.price} $currency."
        )
    }

    private fun visitTavern(state: GameState): GameState {
        var updatedState = state

        while (true) {
            val player = updatedState.player
            val stats = computePlayerStats(player, updatedState.itemInstances)
            val stacks = player.deathDebuffStacks
            val costRest = max(10, 12 + player.level * 2)
            val costSleep = max(25, 30 + player.level * 4)
            val costPurifyOne = if (stacks > 0) 30 + (stacks - 1) * 15 else 0
            val costPurifyAll = if (stacks > 0) 80 + (stacks - 1) * 40 else 0

            println("\nTaverna:")
            println("1. Descansar (cura 25% HP/MP) - custo $costRest")
            println("2. Dormir (cura total) - custo $costSleep")
            println("3. Purificar 1 stack de debuff - custo $costPurifyOne")
            println("4. Purificar tudo - custo $costPurifyAll")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> {
                    if (player.gold < costRest) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costRest,
                        currentHp = min(stats.derived.hpMax, player.currentHp + stats.derived.hpMax * tavernRestHealPct),
                        currentMp = min(stats.derived.mpMax, player.currentMp + stats.derived.mpMax * tavernRestHealPct)
                    )
                    updatedPlayer = applyGoldSpent(updatedPlayer, costRest.toLong())
                    println("Voce descansou na taverna.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }

                2 -> {
                    if (player.gold < costSleep) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costSleep,
                        currentHp = stats.derived.hpMax,
                        currentMp = stats.derived.mpMax
                    )
                    updatedPlayer = applyGoldSpent(updatedPlayer, costSleep.toLong())
                    updatedPlayer = applyFullRestSleep(updatedPlayer)
                    println("Voce dormiu e acordou renovado.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }

                3 -> {
                    if (stacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < costPurifyOne) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val newStacks = max(0, stacks - 1)
                    val capMinutes = if (newStacks == 0) {
                        0.0
                    } else {
                        deathDebuffBaseMinutes + (newStacks - 1) * deathDebuffExtraMinutes
                    }
                    val newMinutes = if (newStacks == 0) 0.0 else min(player.deathDebuffMinutes, capMinutes)
                    var updatedPlayer = player.copy(
                        gold = player.gold - costPurifyOne,
                        deathDebuffStacks = newStacks,
                        deathDebuffMinutes = newMinutes,
                        deathXpPenaltyMinutes = newMinutes,
                        deathXpPenaltyPct = if (newStacks > 0) deathXpPenaltyPct else 0.0
                    )
                    updatedPlayer = applyGoldSpent(updatedPlayer, costPurifyOne.toLong())
                    println("Um stack do debuff foi removido.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }

                4 -> {
                    if (stacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < costPurifyAll) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    var updatedPlayer = player.copy(
                        gold = player.gold - costPurifyAll,
                        deathDebuffStacks = 0,
                        deathDebuffMinutes = 0.0,
                        deathXpPenaltyMinutes = 0.0,
                        deathXpPenaltyPct = 0.0
                    )
                    updatedPlayer = applyGoldSpent(updatedPlayer, costPurifyAll.toLong())
                    println("Purificacao completa realizada.")
                    updatedState = updatedState.copy(player = updatedPlayer)
                    autoSave(updatedState)
                }

                null -> return updatedState
            }
        }
    }
}
