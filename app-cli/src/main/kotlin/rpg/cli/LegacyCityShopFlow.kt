// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.cli.model.ShopPurchaseResult
import rpg.classsystem.RaceBonusSupport
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.ShopCurrency
import rpg.progression.PermanentUpgradeService

internal class LegacyCityShopFlow(
    private val engine: GameEngine,
    private val repo: rpg.io.DataRepository,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val clearScreen: () -> Unit,
    private val autoSave: (GameState) -> Unit,
    private val applyGoldSpent: (player: PlayerState, amount: Long) -> PlayerState
) {
    private val upgradeShopFlow = LegacyCityUpgradeShopFlow(
        engine = engine,
        permanentUpgradeService = permanentUpgradeService,
        readMenuChoice = readMenuChoice,
        clearScreen = clearScreen,
        applyGoldSpent = applyGoldSpent
    )

    private enum class ShopCategory(val label: String) {
        WEAPONS("Armas"),
        ARMORS("Armaduras"),
        ITEMS("Itens"),
        ACCESSORIES("Acessorios"),
        UPGRADES("Aprimoramentos")
    }

    private enum class WeaponClassCategory(val label: String) {
        WARRIOR("Espadachim"),
        ARCHER("Arqueiro"),
        MAGE("Mago"),
        GENERAL("Geral")
    }

    private data class ShopDisplayEntry(
        val entry: rpg.model.ShopEntryDef,
        val requiredLevel: Int,
        val finalPrice: Int,
        val inStock: Boolean,
        val specialOffer: Boolean
    )

    fun openShopByCurrency(state: GameState, currency: ShopCurrency): GameState {
        var player = state.player
        var itemInstances = state.itemInstances
        val entries = repo.shopEntries.values
            .filter { it.enabled && it.currency == currency }
            .sortedWith(compareBy({ it.minPlayerLevel }, { it.price }, { it.name }))

        while (true) {
            clearScreen()
            val categoryCounts = ShopCategory.entries
                .associateWith { category ->
                    if (category == ShopCategory.UPGRADES) 0 else entries.count { classifyCategory(it) == category }
                }
            val balance = if (currency == ShopCurrency.GOLD) player.gold else player.premiumCash
            println("\n=== Loja de ${if (currency == ShopCurrency.GOLD) "Ouro" else "Cash"} ===")
            println("Saldo atual: $balance ${currencyLabel(currency)}")
            ShopCategory.entries.forEachIndexed { index, category ->
                val suffix = if (category == ShopCategory.UPGRADES) "" else " (${categoryCounts[category] ?: 0})"
                println("${index + 1}. ${category.label}$suffix")
            }
            println("x. Voltar")

            when (val choice = readMenuChoice("Escolha: ", 1, ShopCategory.entries.size)) {
                null -> {
                    val updated = state.copy(player = player, itemInstances = itemInstances)
                    autoSave(updated)
                    return updated
                }

                else -> {
                    val selected = ShopCategory.entries[choice - 1]
                    val result = if (selected == ShopCategory.UPGRADES) {
                        upgradeShopFlow.openUpgradeShop(player, itemInstances, currency)
                    } else {
                        var scoped = entries.filter { classifyCategory(it) == selected }
                        if (selected == ShopCategory.WEAPONS) {
                            val classCategory = chooseWeaponClassCategory() ?: continue
                            scoped = when (classCategory) {
                                WeaponClassCategory.GENERAL -> scoped.filter {
                                    classifyWeaponClass(it) == WeaponClassCategory.GENERAL
                                }

                                else -> scoped.filter {
                                    val entryClass = classifyWeaponClass(it)
                                    entryClass == classCategory || entryClass == WeaponClassCategory.GENERAL
                                }
                            }
                        }
                        if (scoped.isEmpty()) {
                            println("Nenhum item nesta categoria.")
                            player to itemInstances
                        } else {
                            openShopCategory(player, itemInstances, currency, selected, scoped)
                        }
                    }
                    player = result.first
                    itemInstances = result.second
                    autoSave(state.copy(player = player, itemInstances = itemInstances))
                }
            }
        }
    }

    fun openUpgradeShopByCurrency(state: GameState, currency: ShopCurrency): GameState {
        val result = upgradeShopFlow.openUpgradeShop(state.player, state.itemInstances, currency)
        return state.copy(player = result.first, itemInstances = result.second)
    }

    private fun openShopCategory(
        initialPlayer: PlayerState,
        initialInstances: Map<String, ItemInstance>,
        currency: ShopCurrency,
        category: ShopCategory,
        entries: List<rpg.model.ShopEntryDef>
    ): Pair<PlayerState, Map<String, ItemInstance>> {
        var player = initialPlayer
        var itemInstances = initialInstances
        while (true) {
            clearScreen()
            println("\n=== ${category.label} (${if (currency == ShopCurrency.GOLD) "Ouro" else "Cash"}) ===")
            val displayEntries = entries.map { entry ->
                val reqLevel = requiredLevelForShopEntry(entry)
                val specialOffer = shouldMarkSpecialOffer(player, entry)
                val inStock = isInStock(player, entry, specialOffer)
                val finalPrice = effectivePrice(player, entry, specialOffer)
                ShopDisplayEntry(
                    entry = entry,
                    requiredLevel = reqLevel,
                    finalPrice = finalPrice,
                    inStock = inStock,
                    specialOffer = specialOffer
                )
            }
            displayEntries.forEachIndexed { index, display ->
                val entry = display.entry
                val itemName = shopEntryItemName(entry)
                val lock = if (player.level < display.requiredLevel) " [bloqueado]" else ""
                val stockLabel = if (display.inStock) "" else " [fora de estoque]"
                val specialLabel = if (display.specialOffer) " [especial]" else ""
                println(
                    "${index + 1}. $itemName x${entry.quantity} - ${display.finalPrice} ${currencyLabel(currency)} " +
                        "(lvl req ${display.requiredLevel})$lock$stockLabel$specialLabel"
                )
            }
            println("x. Voltar")
            val choice = readMenuChoice("Escolha: ", 1, displayEntries.size) ?: return player to itemInstances
            val selectedDisplay = displayEntries[choice - 1]
            if (!showShopEntryDetails(player, selectedDisplay, currency)) {
                continue
            }
            val purchase = buyShopEntry(player, itemInstances, selectedDisplay)
            println(purchase.message)
            if (purchase.success) {
                var updatedPlayer = purchase.player
                val spentGold = (player.gold - updatedPlayer.gold).coerceAtLeast(0)
                if (spentGold > 0) {
                    updatedPlayer = applyGoldSpent(updatedPlayer, spentGold.toLong())
                }
                player = updatedPlayer
                itemInstances = purchase.itemInstances
            }
        }
    }

    private fun requiredLevelForShopEntry(entry: rpg.model.ShopEntryDef): Int {
        val itemReq = engine.itemRegistry.item(entry.itemId)?.minLevel
            ?: engine.itemRegistry.template(entry.itemId)?.minLevel
        return itemReq?.coerceAtLeast(1) ?: entry.minPlayerLevel.coerceAtLeast(1)
    }

    private fun showShopEntryDetails(player: PlayerState, display: ShopDisplayEntry, currency: ShopCurrency): Boolean {
        val entry = display.entry
        val itemName = shopEntryItemName(entry)
        val balance = if (currency == ShopCurrency.GOLD) player.gold else player.premiumCash
        val description = shopEntryDescription(entry)
        println("\n=== Item da Loja ===")
        println("Item: $itemName x${entry.quantity.coerceAtLeast(1)}")
        println("Preco: ${display.finalPrice} ${currencyLabel(currency)}")
        println("Saldo atual: $balance ${currencyLabel(currency)}")
        println("Nivel requerido: ${display.requiredLevel}")
        if (display.specialOffer) {
            println("Oferta especial rotativa: item raro no estoque atual.")
        }
        if (!display.inStock) {
            println("Status de estoque: indisponivel nesta rotacao.")
        }
        if (description.isNotBlank()) {
            println("Descricao: $description")
        }
        if (player.level < display.requiredLevel) {
            println("Status: bloqueado por nivel.")
        }
        println("1. Comprar")
        println("x. Voltar")
        return readMenuChoice("Escolha: ", 1, 1) == 1
    }

    private fun buyShopEntry(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        display: ShopDisplayEntry
    ): ShopPurchaseResult {
        val entry = display.entry
        val requiredLevel = display.requiredLevel
        if (!display.inStock) {
            return ShopPurchaseResult(false, player, itemInstances, "Item indisponivel no estoque desta rodada.")
        }
        if (player.level < requiredLevel) {
            return ShopPurchaseResult(false, player, itemInstances, "Nivel insuficiente. Requer nivel $requiredLevel.")
        }
        if (entry.currency == ShopCurrency.GOLD && player.gold < display.finalPrice) {
            return ShopPurchaseResult(false, player, itemInstances, "Ouro insuficiente.")
        }
        if (entry.currency == ShopCurrency.CASH && player.premiumCash < display.finalPrice) {
            return ShopPurchaseResult(false, player, itemInstances, "CASH insuficiente.")
        }

        val qty = entry.quantity.coerceAtLeast(1)
        val workingInstances = itemInstances.toMutableMap()
        val incoming = mutableListOf<String>()
        repeat(qty) {
            if (engine.itemRegistry.isTemplate(entry.itemId)) {
                val template = engine.itemRegistry.template(entry.itemId)
                    ?: return ShopPurchaseResult(false, player, itemInstances, "Template invalido: ${entry.itemId}.")
                val generated = engine.itemEngine.generateFromTemplate(
                    template = template,
                    level = maxOf(player.level, template.minLevel),
                    rarity = template.rarity
                )
                workingInstances[generated.id] = generated
                incoming += generated.id
            } else {
                if (engine.itemRegistry.item(entry.itemId) == null) {
                    return ShopPurchaseResult(false, player, itemInstances, "Item invalido: ${entry.itemId}.")
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
            return ShopPurchaseResult(false, player, itemInstances, "Inventario sem espaco para essa compra.")
        }

        val charged = when (entry.currency) {
            ShopCurrency.GOLD -> player.copy(gold = player.gold - display.finalPrice)
            ShopCurrency.CASH -> player.copy(premiumCash = player.premiumCash - display.finalPrice)
        }
        val updatedPlayer = charged.copy(
            inventory = insertion.inventory,
            quiverInventory = insertion.quiverInventory,
            selectedAmmoTemplateId = insertion.selectedAmmoTemplateId
        )
        return ShopPurchaseResult(
            success = true,
            player = updatedPlayer,
            itemInstances = workingInstances.toMap(),
            message = "Compra concluida: ${shopEntryItemName(entry)} x$qty por ${display.finalPrice} ${currencyLabel(entry.currency)}."
        )
    }

    private fun shopEntryItemName(entry: rpg.model.ShopEntryDef): String {
        val resolvedName = engine.itemRegistry.item(entry.itemId)?.name ?: engine.itemRegistry.template(entry.itemId)?.name
        return if (resolvedName.isNullOrBlank()) entry.name else resolvedName
    }

    private fun shopEntryDescription(entry: rpg.model.ShopEntryDef): String {
        if (entry.description.isNotBlank()) return entry.description
        return engine.itemRegistry.entry(entry.itemId)?.description ?: ""
    }

    private fun classifyCategory(entry: rpg.model.ShopEntryDef): ShopCategory {
        val lowerTags = entry.tags.map { it.trim().lowercase() }
        if ("accessory" in lowerTags || "ring" in lowerTags || "amulet" in lowerTags) return ShopCategory.ACCESSORIES
        if ("armor" in lowerTags || "armadura" in lowerTags) return ShopCategory.ARMORS
        if ("weapon" in lowerTags || "sword" in lowerTags || "axe" in lowerTags || "bow" in lowerTags || "staff" in lowerTags || "shield" in lowerTags) return ShopCategory.WEAPONS

        val resolved = engine.itemRegistry.entry(entry.itemId) ?: return ShopCategory.ITEMS
        if (resolved.type != rpg.model.ItemType.EQUIPMENT) return ShopCategory.ITEMS
        return when (resolved.slot) {
            rpg.model.EquipSlot.WEAPON_MAIN,
            rpg.model.EquipSlot.WEAPON_OFF,
            rpg.model.EquipSlot.ALJAVA -> ShopCategory.WEAPONS
            rpg.model.EquipSlot.ACCESSORY -> ShopCategory.ACCESSORIES
            else -> ShopCategory.ARMORS
        }
    }

    private fun chooseWeaponClassCategory(): WeaponClassCategory? {
        println("\n=== Armas por Classe ===")
        WeaponClassCategory.entries.forEachIndexed { index, category ->
            println("${index + 1}. ${category.label}")
        }
        println("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, WeaponClassCategory.entries.size) ?: return null
        return WeaponClassCategory.entries[choice - 1]
    }

    private fun classifyWeaponClass(entry: rpg.model.ShopEntryDef): WeaponClassCategory {
        val tags = entry.tags.map { it.trim().lowercase() }.toSet()
        if ("archer" in tags || "{archer}" in tags) return WeaponClassCategory.ARCHER
        if ("mage" in tags || "{mage}" in tags) return WeaponClassCategory.MAGE
        if ("warrior" in tags || "swordman" in tags || "{swordman}" in tags) return WeaponClassCategory.WARRIOR

        val itemId = entry.itemId.lowercase()
        val name = shopEntryItemName(entry).lowercase()
        return when {
            listOf("bow", "arco", "crossbow", "quiver", "aljava", "arrow", "flecha").any { it in itemId || it in name } ->
                WeaponClassCategory.ARCHER

            listOf("staff", "cajado", "scepter", "rod", "grim", "focus", "orb").any { it in itemId || it in name } ->
                WeaponClassCategory.MAGE

            listOf("sword", "espada", "axe", "machado", "shield", "escudo", "mace", "hammer").any { it in itemId || it in name } ->
                WeaponClassCategory.WARRIOR

            else -> WeaponClassCategory.GENERAL
        }
    }

    private fun effectivePrice(player: PlayerState, entry: rpg.model.ShopEntryDef, specialOffer: Boolean): Int {
        val reqLevel = requiredLevelForShopEntry(entry)
        val base = entry.price.coerceAtLeast(1).toDouble()
        val levelCurve = if (reqLevel <= 1) 1.0 else 1.0 + ((reqLevel - 1) * 0.04).coerceAtMost(1.6)
        val rarityCurve = when (engine.itemRegistry.entry(entry.itemId)?.rarity) {
            rpg.item.ItemRarity.COMMON -> 1.00
            rpg.item.ItemRarity.UNCOMMON -> 1.06
            rpg.item.ItemRarity.RARE -> 1.12
            rpg.item.ItemRarity.EPIC -> 1.26
            rpg.item.ItemRarity.LEGENDARY -> 1.45
            rpg.item.ItemRarity.MYTHIC -> 1.65
            null -> 1.0
        }
        val category = classifyCategory(entry)
        val categoryCurve = when (category) {
            ShopCategory.ITEMS -> 0.95
            ShopCategory.ARMORS -> 1.08
            ShopCategory.ACCESSORIES -> 1.14
            ShopCategory.WEAPONS -> 1.12
            ShopCategory.UPGRADES -> 1.0
        }
        val stockCurve = if (specialOffer) 1.32 else 1.0
        val raceDef = runCatching { engine.classSystem.raceDef(player.raceId) }.getOrNull()
        val raceDiscount = RaceBonusSupport.tradeBuyDiscountPct(raceDef)
        val raceMultiplier = (1.0 - raceDiscount / 100.0).coerceIn(0.75, 1.0)
        return kotlin.math.ceil(base * levelCurve * rarityCurve * categoryCurve * stockCurve * raceMultiplier).toInt()
            .coerceAtLeast(1)
    }

    private fun shouldMarkSpecialOffer(player: PlayerState, entry: rpg.model.ShopEntryDef): Boolean {
        val key = "${player.level}:${entry.id}:${player.gold / 100}"
        val roll = (key.hashCode().toLong() and 0x7FFFFFFF).toInt() % 100
        val highTier = requiredLevelForShopEntry(entry) >= (player.level + 3)
        val weaponOrAccessory = classifyCategory(entry) in setOf(ShopCategory.WEAPONS, ShopCategory.ACCESSORIES)
        return weaponOrAccessory && highTier && roll < 28
    }

    private fun isInStock(player: PlayerState, entry: rpg.model.ShopEntryDef, specialOffer: Boolean): Boolean {
        val key = "${entry.id}:${player.level}:${player.inventory.size / 3}"
        val roll = (key.hashCode().toLong() and 0x7FFFFFFF).toInt() % 100
        val requiredLevel = requiredLevelForShopEntry(entry)
        if (requiredLevel > player.level + 8) return false
        if (specialOffer) {
            return roll < 72
        }
        if (requiredLevel <= player.level + 1) {
            return true
        }
        return roll < 86
    }

    private fun currencyLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
}
