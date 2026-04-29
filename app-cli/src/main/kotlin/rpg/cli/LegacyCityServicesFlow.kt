// TODO-REMOVE-LEGACY: fluxo antigo isolado; remover após substituição modular completa.
package rpg.cli

import rpg.application.city.CityRulesSupport
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.PlayerState
import rpg.model.ShopCurrency
import rpg.progression.PermanentUpgradeService

internal class LegacyCityServicesFlow(
    private val engine: GameEngine,
    repo: rpg.io.DataRepository,
    private val cityRulesSupport: CityRulesSupport,
    permanentUpgradeService: PermanentUpgradeService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val clearScreen: () -> Unit,
    private val autoSave: (GameState) -> Unit,
    private val computePlayerStats: (PlayerState, Map<String, rpg.model.ItemInstance>) -> rpg.engine.ComputedStats,
    private val applyGoldSpent: (player: PlayerState, amount: Long) -> PlayerState,
    private val applyFullRestSleep: (player: PlayerState) -> PlayerState
) {
    private val shopFlow = LegacyCityShopFlow(
        engine = engine,
        repo = repo,
        permanentUpgradeService = permanentUpgradeService,
        readMenuChoice = readMenuChoice,
        clearScreen = clearScreen,
        autoSave = autoSave,
        applyGoldSpent = applyGoldSpent
    )

    fun openCityMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            clearScreen()
            println("\n=== Cidade ===")
            println("1. Taverna")
            println("2. Loja de Ouro")
            println("3. Loja de Cash")
            println("4. Aprimoramentos")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> updated = visitTavern(updated)
                2 -> updated = shopFlow.openShopByCurrency(updated, ShopCurrency.GOLD)
                3 -> updated = shopFlow.openShopByCurrency(updated, ShopCurrency.CASH)
                4 -> updated = openUpgradeHub(updated)
                null -> return updated
            }
        }
    }

    private fun openUpgradeHub(state: GameState): GameState {
        var updated = state
        while (true) {
            clearScreen()
            println("\n=== Aprimoramentos ===")
            println("1. Comprar com Ouro")
            println("2. Comprar com Cash")
            println("x. Voltar")
            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> updated = shopFlow.openUpgradeShopByCurrency(updated, ShopCurrency.GOLD)
                2 -> updated = shopFlow.openUpgradeShopByCurrency(updated, ShopCurrency.CASH)
                null -> return updated
            }
            autoSave(updated)
        }
    }

    private fun visitTavern(state: GameState): GameState {
        var updatedState = state
        while (true) {
            val player = updatedState.player
            val stats = computePlayerStats(player, updatedState.itemInstances)
            val pricing = cityRulesSupport.tavernPricing(player, updatedState.itemInstances)
            clearScreen()
            println("\n=== Taverna ===")
            println("Reducao ativa: ${formatPct(pricing.discountPct)}%")
            println("1. Descansar (cura 25% HP/MP) - ${pricing.restCost} ouro (base ${pricing.restBaseCost})")
            println("2. Dormir (cura total) - ${pricing.sleepCost} ouro (base ${pricing.sleepBaseCost})")
            println("3. Purificar 1 stack - ${pricing.purifyOneCost} ouro (base ${pricing.purifyOneBaseCost})")
            println("4. Purificar tudo - ${pricing.purifyAllCost} ouro (base ${pricing.purifyAllBaseCost})")
            println("Debuff: ${player.deathDebuffStacks} stack(s), ${formatPct(player.deathDebuffMinutes)} min")
            println("HP: ${formatPct(player.currentHp)}/${formatPct(stats.derived.hpMax)} | MP: ${formatPct(player.currentMp)}/${formatPct(stats.derived.mpMax)}")
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> {
                    if (player.gold < pricing.restCost) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val before = updatedState.player
                    var next = cityRulesSupport.applyRest(updatedState)
                    val spent = (before.gold - next.player.gold).coerceAtLeast(0)
                    var finalPlayer = next.player
                    if (spent > 0) finalPlayer = applyGoldSpent(finalPlayer, spent.toLong())
                    next = next.copy(player = finalPlayer)
                    println("Voce descansou na taverna.")
                    updatedState = next
                    autoSave(updatedState)
                }

                2 -> {
                    if (player.gold < pricing.sleepCost) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val before = updatedState.player
                    var next = cityRulesSupport.applySleep(updatedState)
                    val spent = (before.gold - next.player.gold).coerceAtLeast(0)
                    var finalPlayer = next.player
                    if (spent > 0) finalPlayer = applyGoldSpent(finalPlayer, spent.toLong())
                    finalPlayer = applyFullRestSleep(finalPlayer)
                    next = next.copy(player = finalPlayer)
                    println("Voce dormiu e acordou renovado.")
                    updatedState = next
                    autoSave(updatedState)
                }

                3 -> {
                    if (player.deathDebuffStacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < pricing.purifyOneCost) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val before = updatedState.player
                    var next = cityRulesSupport.applyPurifyOne(updatedState)
                    val spent = (before.gold - next.player.gold).coerceAtLeast(0)
                    var finalPlayer = next.player
                    if (spent > 0) finalPlayer = applyGoldSpent(finalPlayer, spent.toLong())
                    next = next.copy(player = finalPlayer)
                    println("Um stack do debuff foi removido.")
                    updatedState = next
                    autoSave(updatedState)
                }

                4 -> {
                    if (player.deathDebuffStacks <= 0) {
                        println("Nenhum debuff ativo.")
                        continue
                    }
                    if (player.gold < pricing.purifyAllCost) {
                        println("Ouro insuficiente.")
                        continue
                    }
                    val before = updatedState.player
                    var next = cityRulesSupport.applyPurifyAll(updatedState)
                    val spent = (before.gold - next.player.gold).coerceAtLeast(0)
                    var finalPlayer = next.player
                    if (spent > 0) finalPlayer = applyGoldSpent(finalPlayer, spent.toLong())
                    next = next.copy(player = finalPlayer)
                    println("Purificacao completa realizada.")
                    updatedState = next
                    autoSave(updatedState)
                }

                null -> return updatedState
            }
        }
    }

    private fun formatPct(value: Double): String = "%.1f".format(value)
}
