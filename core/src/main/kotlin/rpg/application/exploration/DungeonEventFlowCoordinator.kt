package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingDungeonChestEvent
import rpg.application.PendingDungeonEvent
import rpg.application.PendingDungeonLiquidEvent
import rpg.application.PendingDungeonNpcItemEvent
import rpg.application.PendingDungeonNpcMoneyEvent
import rpg.application.PendingDungeonNpcSuspiciousEvent
import rpg.application.PendingEncounter
import rpg.engine.GameEngine
import rpg.events.EventDefinition
import rpg.events.EventEffect
import rpg.events.EventEngine
import rpg.events.EventExecutor
import rpg.events.EventSource
import rpg.events.NpcEventVariant
import rpg.model.DungeonRun
import rpg.model.GameState
import rpg.model.ItemType
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.navigation.NavigationState
import rpg.world.RunRoomType

internal class DungeonEventFlowCoordinator(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport
) {
    fun preparePendingEvent(
        state: GameState,
        run: DungeonRun,
        tier: MapTierDef
    ): PendingDungeonEvent {
        val service = engine.dungeonEventService
        val npcThreshold = (34 + engine.biomeNpcEventBonusPct(tier.biomeId)).coerceIn(10, 85)
        val secondaryThreshold = npcThreshold + ((100 - npcThreshold) / 2)
        val roll = engine.rollInt(100)

        val source = when {
            roll < npcThreshold -> EventSource.NPC_HELP
            roll < secondaryThreshold -> EventSource.LIQUID
            else -> EventSource.CHEST_REWARD
        }

        return when (source) {
            EventSource.NPC_HELP -> {
                val intro = service.npcIntro { bound -> engine.rollInt(bound) }
                when (service.pickNpcVariant { bound -> engine.rollInt(bound) }) {
                    NpcEventVariant.MONEY -> {
                        val requestedGold = service.requestedGold(
                            playerLevel = state.player.level,
                            depth = run.depth,
                            rollInt = { bound -> engine.rollInt(bound) }
                        )
                        val detail = service.npcMoneyPitch(requestedGold) { bound -> engine.rollInt(bound) }
                        PendingDungeonNpcMoneyEvent(
                            run = run,
                            tier = tier,
                            introLine = intro,
                            detailLine = detail,
                            requestedGold = requestedGold
                        )
                    }

                    NpcEventVariant.ITEM -> {
                        val candidate = pickTravelerRequestedItem(state.player, state.itemInstances)
                        if (candidate == null) {
                            val requestedGold = service.requestedGold(
                                playerLevel = state.player.level,
                                depth = run.depth,
                                rollInt = { bound -> engine.rollInt(bound) }
                            )
                            val detail = service.npcMoneyPitch(requestedGold) { bound -> engine.rollInt(bound) }
                            PendingDungeonNpcMoneyEvent(
                                run = run,
                                tier = tier,
                                introLine = intro,
                                detailLine = detail,
                                requestedGold = requestedGold
                            )
                        } else {
                            val qty = if (candidate.itemIds.size >= 2 && engine.rollInt(100) < 45) 2 else 1
                            val detail = service.npcItemPitch(
                                itemName = candidate.itemName,
                                qty = qty,
                                rollInt = { bound -> engine.rollInt(bound) }
                            )
                            PendingDungeonNpcItemEvent(
                                run = run,
                                tier = tier,
                                introLine = intro,
                                detailLine = detail,
                                requestedItemName = candidate.itemName,
                                requestedItemIds = candidate.itemIds,
                                requestedQty = qty
                            )
                        }
                    }

                    NpcEventVariant.SUSPICIOUS -> {
                        val detail = service.npcSuspiciousPitch { bound -> engine.rollInt(bound) }
                        PendingDungeonNpcSuspiciousEvent(
                            run = run,
                            tier = tier,
                            introLine = intro,
                            detailLine = detail
                        )
                    }
                }
            }

            EventSource.LIQUID -> PendingDungeonLiquidEvent(
                run = run,
                tier = tier,
                introLine = service.liquidIntro { bound -> engine.rollInt(bound) },
                detailLine = "Um frasco pulsa com energia instavel."
            )

            EventSource.CHEST_REWARD -> PendingDungeonChestEvent(
                run = run,
                tier = tier,
                introLine = service.chestIntro { bound -> engine.rollInt(bound) },
                detailLine = "O bau pode conter recompensa... ou armadilha."
            )
        }
    }

    fun resolve(
        session: GameSession,
        choice: Int
    ): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val pending = session.pendingDungeonEvent
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum evento pendente.")))
        val normalized = stateSupport.normalize(state)
        val service = engine.dungeonEventService

        return when (pending) {
            is PendingDungeonNpcMoneyEvent -> {
                val player = normalized.player
                if (choice != 1) {
                    if (service.shouldAmbushOnMoneyRefuse { bound -> engine.rollInt(bound) }) {
                        val line = service.npcMoneyRefuseLine { bound -> engine.rollInt(bound) }
                        startAmbush(session, normalized, pending, player, normalized.itemInstances, line)
                    } else {
                        completeEvent(
                            session = session,
                            state = normalized,
                            pending = pending,
                            player = player,
                            itemInstances = normalized.itemInstances,
                            lines = listOf("O viajante murmura algo e some no corredor.")
                        )
                    }
                } else if (player.gold < pending.requestedGold) {
                    val noGold = service.npcMoneyNoGoldLine { bound -> engine.rollInt(bound) }
                    if (service.shouldAmbushOnMoneyNoGold { bound -> engine.rollInt(bound) }) {
                        val ambush = service.npcMoneyRefuseLine { bound -> engine.rollInt(bound) }
                        startAmbush(session, normalized, pending, player, normalized.itemInstances, "$noGold $ambush")
                    } else {
                        completeEvent(
                            session = session,
                            state = normalized,
                            pending = pending,
                            player = player,
                            itemInstances = normalized.itemInstances,
                            lines = listOf(noGold)
                        )
                    }
                } else {
                    val paidRaw = player.copy(gold = player.gold - pending.requestedGold)
                    val paid = stateSupport.applyGoldDeltaAchievements(player, paidRaw)
                    when {
                        service.shouldScamOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                            val line = service.npcMoneyScamLine { bound -> engine.rollInt(bound) }
                            startAmbush(session, normalized, pending, paid, normalized.itemInstances, line)
                        }

                        service.shouldRewardOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                            val rewardLine = service.npcMoneyRewardLine { bound -> engine.rollInt(bound) }
                            val (afterEvent, eventLines) = applyGeneratedEvent(
                                player = paid,
                                itemInstances = normalized.itemInstances,
                                source = EventSource.NPC_HELP,
                                depth = pending.run.depth,
                                tierId = pending.tier.id
                            )
                            completeEvent(
                                session = session,
                                state = normalized,
                                pending = pending,
                                player = afterEvent,
                                itemInstances = normalized.itemInstances,
                                lines = listOf(rewardLine) + eventLines
                            )
                        }

                        else -> {
                            val neutral = service.npcMoneyNeutralLine { bound -> engine.rollInt(bound) }
                            completeEvent(
                                session = session,
                                state = normalized,
                                pending = pending,
                                player = paid,
                                itemInstances = normalized.itemInstances,
                                lines = listOf(neutral)
                            )
                        }
                    }
                }
            }

            is PendingDungeonNpcItemEvent -> {
                val player = normalized.player
                if (choice != 1) {
                    if (service.shouldAmbushOnItemRefuse { bound -> engine.rollInt(bound) }) {
                        val line = service.npcItemRefuseLine { bound -> engine.rollInt(bound) }
                        startAmbush(session, normalized, pending, player, normalized.itemInstances, line)
                    } else {
                        completeEvent(
                            session = session,
                            state = normalized,
                            pending = pending,
                            player = player,
                            itemInstances = normalized.itemInstances,
                            lines = listOf("O viajante segue por outro caminho.")
                        )
                    }
                } else {
                    val consumed = consumeRequestedItems(
                        player = player,
                        itemInstances = normalized.itemInstances,
                        requestedItemIds = pending.requestedItemIds,
                        quantity = pending.requestedQty
                    )
                    when {
                        service.shouldScamOnItemGive { bound -> engine.rollInt(bound) } -> {
                            val line = service.npcItemScamLine { bound -> engine.rollInt(bound) }
                            startAmbush(session, normalized, pending, consumed.player, consumed.itemInstances, line)
                        }

                        service.shouldRewardOnItemGive { bound -> engine.rollInt(bound) } -> {
                            val rewardLine = service.npcItemRewardLine { bound -> engine.rollInt(bound) }
                            val (afterEvent, eventLines) = applyGeneratedEvent(
                                player = consumed.player,
                                itemInstances = consumed.itemInstances,
                                source = EventSource.NPC_HELP,
                                depth = pending.run.depth,
                                tierId = pending.tier.id
                            )
                            completeEvent(
                                session = session,
                                state = normalized,
                                pending = pending,
                                player = afterEvent,
                                itemInstances = consumed.itemInstances,
                                lines = listOf(rewardLine, "Itens entregues: x${pending.requestedQty}") + eventLines
                            )
                        }

                        else -> {
                            val neutral = service.npcItemNeutralLine { bound -> engine.rollInt(bound) }
                            completeEvent(
                                session = session,
                                state = normalized,
                                pending = pending,
                                player = consumed.player,
                                itemInstances = consumed.itemInstances,
                                lines = listOf("Itens entregues: x${pending.requestedQty}", neutral)
                            )
                        }
                    }
                }
            }

            is PendingDungeonNpcSuspiciousEvent -> {
                val player = normalized.player
                if (choice != 1) {
                    val line = service.npcSuspiciousRefuseLine { bound -> engine.rollInt(bound) }
                    completeEvent(
                        session = session,
                        state = normalized,
                        pending = pending,
                        player = player,
                        itemInstances = normalized.itemInstances,
                        lines = listOf(line)
                    )
                } else if (service.shouldAmbushOnSuspiciousAccept { bound -> engine.rollInt(bound) }) {
                    val line = service.npcSuspiciousScamLine { bound -> engine.rollInt(bound) }
                    startAmbush(session, normalized, pending, player, normalized.itemInstances, line)
                } else {
                    val rewardLine = service.npcSuspiciousRewardLine { bound -> engine.rollInt(bound) }
                    val (afterEvent, eventLines) = applyGeneratedEvent(
                        player = player,
                        itemInstances = normalized.itemInstances,
                        source = EventSource.NPC_HELP,
                        depth = pending.run.depth,
                        tierId = pending.tier.id
                    )
                    completeEvent(
                        session = session,
                        state = normalized,
                        pending = pending,
                        player = afterEvent,
                        itemInstances = normalized.itemInstances,
                        lines = listOf(rewardLine) + eventLines
                    )
                }
            }

            is PendingDungeonLiquidEvent -> {
                val player = normalized.player
                if (choice == 3) {
                    val ignore = service.liquidIgnoreLine { bound -> engine.rollInt(bound) }
                    completeEvent(
                        session = session,
                        state = normalized,
                        pending = pending,
                        player = player,
                        itemInstances = normalized.itemInstances,
                        lines = listOf(ignore)
                    )
                } else {
                    val context = engine.buildEventContext(player, normalized.itemInstances, pending.run.depth)
                        .copy(tierId = pending.tier.id)
                    val event = if (choice == 2) {
                        val first = EventEngine.generateEvent(EventSource.LIQUID, context)
                        val second = EventEngine.generateEvent(EventSource.LIQUID, context)
                        if (hasDirectDamageRisk(first) && !hasDirectDamageRisk(second)) second else first
                    } else {
                        EventEngine.generateEvent(EventSource.LIQUID, context)
                    }
                    val afterRaw = EventExecutor.execute(event, player, context)
                    val after = stateSupport.applyGoldDeltaAchievements(player, afterRaw)
                    completeEvent(
                        session = session,
                        state = normalized,
                        pending = pending,
                        player = after,
                        itemInstances = normalized.itemInstances,
                        lines = listOf("[${event.rarity}] ${event.description}") + buildDeltaLines(player, after)
                    )
                }
            }

            is PendingDungeonChestEvent -> {
                val player = normalized.player
                if (choice == 3) {
                    val ignore = service.chestIgnoreLine { bound -> engine.rollInt(bound) }
                    completeEvent(
                        session = session,
                        state = normalized,
                        pending = pending,
                        player = player,
                        itemInstances = normalized.itemInstances,
                        lines = listOf(ignore)
                    )
                } else {
                    val mimicChance = service.chestMimicChancePct(inspected = choice == 2)
                    if (engine.rollInt(100) < mimicChance) {
                        val line = service.chestAmbushLine { bound -> engine.rollInt(bound) }
                        startAmbush(session, normalized, pending, player, normalized.itemInstances, line)
                    } else {
                        val (afterEvent, eventLines) = applyGeneratedEvent(
                            player = player,
                            itemInstances = normalized.itemInstances,
                            source = EventSource.CHEST_REWARD,
                            depth = pending.run.depth,
                            tierId = pending.tier.id
                        )
                        completeEvent(
                            session = session,
                            state = normalized,
                            pending = pending,
                            player = afterEvent,
                            itemInstances = normalized.itemInstances,
                            lines = eventLines
                        )
                    }
                }
            }
        }
    }

    private fun applyGeneratedEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
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

    private fun completeEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonEvent,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
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

    private fun startAmbush(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonEvent,
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
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

    private fun pickTravelerRequestedItem(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): ItemRequestCandidate? {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in player.inventory) {
            val resolved = engine.itemResolver.resolve(itemId, itemInstances) ?: continue
            if (resolved.type == ItemType.EQUIPMENT) continue
            val key = itemInstances[itemId]?.templateId ?: itemId
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }
        if (grouped.isEmpty()) return null
        val candidates = grouped.entries.toList()
        val selected = candidates[engine.rollInt(candidates.size)]
        val sampleId = selected.value.first()
        val name = engine.itemResolver.resolve(sampleId, itemInstances)?.name ?: selected.key
        return ItemRequestCandidate(itemName = name, itemIds = selected.value.toList())
    }

    private fun consumeRequestedItems(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
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

    private fun hasDirectDamageRisk(event: EventDefinition): Boolean {
        return event.effects.any { it is EventEffect.DamagePercentCurrent }
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

    private data class ItemRequestCandidate(
        val itemName: String,
        val itemIds: List<String>
    )

    private data class ConsumedItems(
        val player: PlayerState,
        val itemInstances: Map<String, rpg.model.ItemInstance>
    )
}
