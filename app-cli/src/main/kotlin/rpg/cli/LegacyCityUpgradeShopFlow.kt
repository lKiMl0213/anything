// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.engine.GameEngine
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.ShopCurrency
import rpg.progression.PermanentUpgradeService

internal class LegacyCityUpgradeShopFlow(
    private val engine: GameEngine,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val clearScreen: () -> Unit,
    private val applyGoldSpent: (player: PlayerState, amount: Long) -> PlayerState
) {
    private enum class UpgradeMenuCategory(val label: String) {
        PRODUCTION("Producao"),
        BATTLE("Batalha"),
        UTILITY("Utilidade")
    }

    fun openUpgradeShop(
        initialPlayer: PlayerState,
        initialInstances: Map<String, ItemInstance>,
        currency: ShopCurrency
    ): Pair<PlayerState, Map<String, ItemInstance>> {
        var player = initialPlayer
        var itemInstances = initialInstances
        val defs = permanentUpgradeService.definitions()
        if (defs.isEmpty()) {
            println("Nenhum aprimoramento configurado.")
            return player to itemInstances
        }
        while (true) {
            clearScreen()
            val balance = if (currency == ShopCurrency.GOLD) player.gold else player.premiumCash
            println("\n=== Aprimoramentos (${if (currency == ShopCurrency.GOLD) "Loja Ouro" else "Loja Cash"}) ===")
            println("Saldo: $balance ${currencyLabel(currency)}")
            val grouped = defs.groupBy(::resolveCategory)
            UpgradeMenuCategory.entries.forEachIndexed { index, category ->
                val count = grouped[category]?.size ?: 0
                println("${index + 1}. ${category.label} ($count)")
            }
            println("x. Voltar")
            val categoryChoice = readMenuChoice("Escolha: ", 1, UpgradeMenuCategory.entries.size) ?: return player to itemInstances
            val category = UpgradeMenuCategory.entries[categoryChoice - 1]
            val scopedDefs = grouped[category].orEmpty().sortedBy { it.name }
            if (scopedDefs.isEmpty()) {
                println("Nenhum aprimoramento nesta categoria.")
                continue
            }
            while (true) {
                clearScreen()
                println("\n=== Aprimoramentos > ${category.label} ===")
                scopedDefs.forEachIndexed { index, def ->
                    val level = permanentUpgradeService.currentLevel(player, def.id)
                    val currentValue = permanentUpgradeService.currentEffectValue(player, def.id)
                    val nextValue = permanentUpgradeService.nextEffectValue(player, def.id)
                    val currentLabel = if (level > 0) permanentUpgradeService.effectLabel(def, currentValue) else "0"
                    val nextLabel = nextValue?.let { permanentUpgradeService.effectLabel(def, it) } ?: "MAX"
                    println("${index + 1}. ${def.name} - Nv $level/${def.maxLevel}")
                    println("   ${def.description}")
                    println("   Atual: $currentLabel | Proximo: $nextLabel")
                }
                println("x. Voltar")
                val choice = readMenuChoice("Escolha: ", 1, scopedDefs.size) ?: break
                val selected = scopedDefs[choice - 1]
                val level = permanentUpgradeService.currentLevel(player, selected.id)
                if (level >= selected.maxLevel) {
                    println("${selected.name} ja esta no nivel maximo.")
                    continue
                }
                val costs = permanentUpgradeService.nextLevelCosts(player, selected.id, currency)
                if (costs.isEmpty()) {
                    println("Nao ha forma de compra disponivel nesta loja para este aprimoramento.")
                    continue
                }

                val pickedCost = if (costs.size == 1) {
                    costs.first()
                } else {
                    println("\nFormas de compra para ${selected.name}:")
                    costs.forEachIndexed { index, cost ->
                        val label = if (cost.label.isNotBlank()) " - ${cost.label}" else ""
                        println("${index + 1}. ${renderUpgradeCost(cost)}$label")
                    }
                    println("x. Voltar")
                    val costChoice = readMenuChoice("Escolha: ", 1, costs.size) ?: continue
                    costs[costChoice - 1]
                }

                println("Custo: ${renderUpgradeCost(pickedCost)}")
                println("1. Confirmar compra")
                println("x. Voltar")
                if (readMenuChoice("Escolha: ", 1, 1) != 1) {
                    continue
                }

                val purchase = permanentUpgradeService.purchase(
                    player = player,
                    itemInstances = itemInstances,
                    upgradeId = selected.id,
                    costId = pickedCost.id,
                    shopCurrency = currency
                )
                println(purchase.message)
                if (purchase.success) {
                    var updatedPlayer = purchase.player
                    if (purchase.spentGold > 0) {
                        updatedPlayer = applyGoldSpent(updatedPlayer, purchase.spentGold.toLong())
                    }
                    player = updatedPlayer
                    itemInstances = purchase.itemInstances
                }
            }
        }
    }

    private fun resolveCategory(def: rpg.model.PermanentUpgradeDef): UpgradeMenuCategory {
        return when (def.effectType) {
            rpg.model.PermanentUpgradeEffectType.PROFESSION_XP_BOOST,
            rpg.model.PermanentUpgradeEffectType.CRAFT_BATCH_BONUS,
            rpg.model.PermanentUpgradeEffectType.FISHING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.HERBALISM_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.MINING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.WOODCUTTING_DOUBLE_CHANCE,
            rpg.model.PermanentUpgradeEffectType.FORGE_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.COOKING_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.ALCHEMY_COST_REDUCTION -> UpgradeMenuCategory.PRODUCTION

            rpg.model.PermanentUpgradeEffectType.COMBAT_XP_BOOST,
            rpg.model.PermanentUpgradeEffectType.MONSTER_RARITY_BONUS -> UpgradeMenuCategory.BATTLE

            rpg.model.PermanentUpgradeEffectType.QUEST_ITEM_KEEP_CHANCE,
            rpg.model.PermanentUpgradeEffectType.TAVERN_COST_REDUCTION,
            rpg.model.PermanentUpgradeEffectType.QUIVER_CAPACITY_BONUS -> UpgradeMenuCategory.UTILITY
        }
    }

    private fun renderUpgradeCost(cost: rpg.model.PermanentUpgradeCostDef): String {
        val parts = mutableListOf<String>()
        if (cost.goldCost > 0) parts += "${cost.goldCost} ouro"
        if (cost.cashCost > 0) parts += "${cost.cashCost} CASH"
        val reqId = cost.requiredItemId?.takeIf { it.isNotBlank() }
        if (reqId != null && cost.requiredItemQty > 0) {
            val itemName = engine.itemRegistry.entry(reqId)?.name ?: reqId
            parts += "$itemName x${cost.requiredItemQty}"
        }
        return parts.joinToString(" + ").ifBlank { "Sem custo" }
    }

    private fun currencyLabel(currency: ShopCurrency): String = if (currency == ShopCurrency.GOLD) "ouro" else "CASH"
}
