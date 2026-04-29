package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingDungeonEvent
import rpg.application.PendingEncounter
import rpg.engine.GameEngine
import rpg.events.EventDefinition
import rpg.events.EventEffect
import rpg.events.EventEngine
import rpg.events.EventExecutor
import rpg.events.EventSource
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

internal class DungeonEventOutcomeService(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport
) {
    fun applyGeneratedEvent(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        source: EventSource,
        depth: Int,
        tierId: String
    ): Pair<PlayerState, List<String>> {
        val context = engine.buildEventContext(player, itemInstances, depth).copy(tierId = tierId)
        val event = EventEngine.generateEvent(source, context)
        val afterRaw = EventExecutor.execute(event, player, context)
        val after = stateSupport.applyGoldDeltaAchievements(player, afterRaw)
        return after to (listOf("[${event.rarity}] ${event.description}") + buildDeltaLines(player, after))
    }

    fun completeEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonEvent,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        lines: List<String>
    ): GameActionResult {
        val advancedRun = engine.advanceRun(
            run = pending.run,
            bossDefeated = false,
            clearedRoomType = RunRoomType.EVENT,
            victoryInRoom = false
        )
        val updatedState = stateSupport.normalize(
            state.copy(
                player = player,
                itemInstances = itemInstances,
                currentRun = advancedRun
            )
        )
        return GameActionResult(
            session = session.copy(
                gameState = updatedState,
                navigation = NavigationState.Exploration,
                pendingDungeonEvent = null,
                pendingEncounter = null,
                messages = lines.filter { it.isNotBlank() }
            )
        )
    }

    fun startAmbush(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonEvent,
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        introLine: String
    ): GameActionResult {
        val mimic = engine.buildMimicMonster(pending.run.difficultyLevel)
        val encounterText = engine.encounterText(mimic, pending.tier, engine.computePlayerStats(player, itemInstances))
        val encounter = PendingEncounter(
            run = pending.run,
            tier = pending.tier,
            player = player,
            itemInstances = itemInstances,
            monster = mimic,
            isBoss = false,
            roomType = RunRoomType.EVENT,
            introLines = listOf(introLine, encounterText)
        )
        val updatedState = state.copy(
            player = player,
            itemInstances = itemInstances,
            currentRun = pending.run
        )
        return GameActionResult(
            session = session.copy(
                gameState = updatedState,
                navigation = NavigationState.Combat,
                pendingDungeonEvent = null,
                pendingEncounter = encounter,
                messages = emptyList()
            ),
            effect = GameEffect.LaunchCombat(encounter)
        )
    }

    fun consumeRequestedItems(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        requestedItemIds: List<String>,
        quantity: Int
    ): ConsumedItems {
        val targetCount = quantity.coerceAtLeast(1)
        val inventory = player.inventory.toMutableList()
        val instances = itemInstances.toMutableMap()
        var removed = 0
        for (itemId in requestedItemIds) {
            if (removed >= targetCount) break
            if (inventory.remove(itemId)) {
                removed++
                if (instances.containsKey(itemId)) {
                    instances.remove(itemId)
                }
            }
        }
        return ConsumedItems(
            player = player.copy(inventory = inventory),
            itemInstances = instances.toMap()
        )
    }

    fun hasDirectDamageRisk(event: EventDefinition): Boolean {
        return event.effects.any { it is EventEffect.DamagePercentCurrent }
    }

    fun deltaLines(before: PlayerState, after: PlayerState): List<String> {
        return buildDeltaLines(before, after)
    }

    private fun buildDeltaLines(before: PlayerState, after: PlayerState): List<String> {
        val lines = mutableListOf<String>()
        val hpDelta = after.currentHp - before.currentHp
        val mpDelta = after.currentMp - before.currentMp
        val goldDelta = after.gold - before.gold
        val addedItems = inventoryAdded(before.inventory, after.inventory)
        if (hpDelta != 0.0) {
            val sign = if (hpDelta > 0.0) "+" else ""
            lines += "HP $sign${format(hpDelta)}"
        }
        if (mpDelta != 0.0) {
            val sign = if (mpDelta > 0.0) "+" else ""
            lines += "MP $sign${format(mpDelta)}"
        }
        if (goldDelta != 0) {
            val sign = if (goldDelta > 0) "+" else ""
            lines += "Ouro $sign$goldDelta"
        }
        if (addedItems.isNotEmpty()) {
            lines += "Itens: ${addedItems.joinToString(" | ")}"
        }
        if (!before.ignoreNextDebuff && after.ignoreNextDebuff) {
            lines += "Efeito: proximo debuff sera ignorado."
        }
        if (!before.reviveOnce && after.reviveOnce) {
            lines += "Efeito: reviver uma vez ativado."
        }
        return lines
    }

    private fun inventoryAdded(before: List<String>, after: List<String>): List<String> {
        val remaining = before.groupingBy { it }.eachCount().toMutableMap()
        val added = mutableMapOf<String, Int>()
        for (itemId in after) {
            val count = remaining[itemId] ?: 0
            if (count > 0) {
                remaining[itemId] = count - 1
            } else {
                added[itemId] = (added[itemId] ?: 0) + 1
            }
        }
        return added.entries.map { (itemId, qty) ->
            val name = engine.itemRegistry.item(itemId)?.name
                ?: engine.itemRegistry.template(itemId)?.name
                ?: itemId
            "$name x$qty"
        }
    }

    private fun format(value: Double): String = "%.1f".format(value)
}

internal data class ConsumedItems(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>
)
