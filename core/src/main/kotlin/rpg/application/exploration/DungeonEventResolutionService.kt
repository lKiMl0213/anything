package rpg.application.exploration

import rpg.application.GameActionResult
import rpg.application.GameSession
import rpg.application.GameStateSupport
import rpg.application.PendingDungeonChestEvent
import rpg.application.PendingDungeonLiquidEvent
import rpg.application.PendingDungeonNpcItemEvent
import rpg.application.PendingDungeonNpcMoneyEvent
import rpg.application.PendingDungeonNpcSuspiciousEvent
import rpg.engine.GameEngine
import rpg.events.EventEngine
import rpg.events.EventExecutor
import rpg.events.EventSource
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.PlayerState

internal class DungeonEventResolutionService(
    private val engine: GameEngine,
    private val stateSupport: GameStateSupport,
    private val outcomeService: DungeonEventOutcomeService = DungeonEventOutcomeService(engine, stateSupport)
) {
    fun resolve(
        session: GameSession,
        choice: Int
    ): GameActionResult {
        val state = session.gameState
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum jogo carregado.")))
        val pending = session.pendingDungeonEvent
            ?: return GameActionResult(session.copy(messages = listOf("Nenhum evento pendente.")))
        val normalized = stateSupport.normalize(state)

        return when (pending) {
            is PendingDungeonNpcMoneyEvent -> resolveNpcMoneyEvent(session, normalized, pending, choice)
            is PendingDungeonNpcItemEvent -> resolveNpcItemEvent(session, normalized, pending, choice)
            is PendingDungeonNpcSuspiciousEvent -> resolveNpcSuspiciousEvent(session, normalized, pending, choice)
            is PendingDungeonLiquidEvent -> resolveLiquidEvent(session, normalized, pending, choice)
            is PendingDungeonChestEvent -> resolveChestEvent(session, normalized, pending, choice)
        }
    }

    private fun resolveNpcMoneyEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonNpcMoneyEvent,
        choice: Int
    ): GameActionResult {
        val service = engine.dungeonEventService
        val player = state.player
        val itemInstances = state.itemInstances
        if (choice != 1) {
            if (service.shouldAmbushOnMoneyRefuse { bound -> engine.rollInt(bound) }) {
                val line = service.npcMoneyRefuseLine { bound -> engine.rollInt(bound) }
                return outcomeService.startAmbush(session, state, pending, player, itemInstances, line)
            }
            return outcomeService.completeEvent(
                session = session,
                state = state,
                pending = pending,
                player = player,
                itemInstances = itemInstances,
                lines = listOf("O viajante murmura algo e some no corredor.")
            )
        }

        if (player.gold < pending.requestedGold) {
            val noGold = service.npcMoneyNoGoldLine { bound -> engine.rollInt(bound) }
            if (service.shouldAmbushOnMoneyNoGold { bound -> engine.rollInt(bound) }) {
                val ambush = service.npcMoneyRefuseLine { bound -> engine.rollInt(bound) }
                return outcomeService.startAmbush(session, state, pending, player, itemInstances, "$noGold $ambush")
            }
            return outcomeService.completeEvent(
                session = session,
                state = state,
                pending = pending,
                player = player,
                itemInstances = itemInstances,
                lines = listOf(noGold)
            )
        }

        val paidRaw = player.copy(gold = player.gold - pending.requestedGold)
        val paid = stateSupport.applyGoldDeltaAchievements(player, paidRaw)
        return when {
            service.shouldScamOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                val line = service.npcMoneyScamLine { bound -> engine.rollInt(bound) }
                outcomeService.startAmbush(session, state, pending, paid, itemInstances, line)
            }

            service.shouldRewardOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                val rewardLine = service.npcMoneyRewardLine { bound -> engine.rollInt(bound) }
                val (afterEvent, eventLines) = outcomeService.applyGeneratedEvent(
                    player = paid,
                    itemInstances = itemInstances,
                    source = EventSource.NPC_HELP,
                    depth = pending.run.depth,
                    tierId = pending.tier.id
                )
                outcomeService.completeEvent(
                    session = session,
                    state = state,
                    pending = pending,
                    player = afterEvent,
                    itemInstances = itemInstances,
                    lines = listOf(rewardLine) + eventLines
                )
            }

            else -> {
                val neutral = service.npcMoneyNeutralLine { bound -> engine.rollInt(bound) }
                outcomeService.completeEvent(
                    session = session,
                    state = state,
                    pending = pending,
                    player = paid,
                    itemInstances = itemInstances,
                    lines = listOf(neutral)
                )
            }
        }
    }

    private fun resolveNpcItemEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonNpcItemEvent,
        choice: Int
    ): GameActionResult {
        val service = engine.dungeonEventService
        val player = state.player
        val itemInstances = state.itemInstances
        if (choice != 1) {
            if (service.shouldAmbushOnItemRefuse { bound -> engine.rollInt(bound) }) {
                val line = service.npcItemRefuseLine { bound -> engine.rollInt(bound) }
                return outcomeService.startAmbush(session, state, pending, player, itemInstances, line)
            }
            return outcomeService.completeEvent(
                session = session,
                state = state,
                pending = pending,
                player = player,
                itemInstances = itemInstances,
                lines = listOf("O viajante segue por outro caminho.")
            )
        }

        val consumed = outcomeService.consumeRequestedItems(
            player = player,
            itemInstances = itemInstances,
            requestedItemIds = pending.requestedItemIds,
            quantity = pending.requestedQty
        )
        return when {
            service.shouldScamOnItemGive { bound -> engine.rollInt(bound) } -> {
                val line = service.npcItemScamLine { bound -> engine.rollInt(bound) }
                outcomeService.startAmbush(session, state, pending, consumed.player, consumed.itemInstances, line)
            }

            service.shouldRewardOnItemGive { bound -> engine.rollInt(bound) } -> {
                val rewardLine = service.npcItemRewardLine { bound -> engine.rollInt(bound) }
                val (afterEvent, eventLines) = outcomeService.applyGeneratedEvent(
                    player = consumed.player,
                    itemInstances = consumed.itemInstances,
                    source = EventSource.NPC_HELP,
                    depth = pending.run.depth,
                    tierId = pending.tier.id
                )
                outcomeService.completeEvent(
                    session = session,
                    state = state,
                    pending = pending,
                    player = afterEvent,
                    itemInstances = consumed.itemInstances,
                    lines = listOf(rewardLine, "Itens entregues: x${pending.requestedQty}") + eventLines
                )
            }

            else -> {
                val neutral = service.npcItemNeutralLine { bound -> engine.rollInt(bound) }
                outcomeService.completeEvent(
                    session = session,
                    state = state,
                    pending = pending,
                    player = consumed.player,
                    itemInstances = consumed.itemInstances,
                    lines = listOf("Itens entregues: x${pending.requestedQty}", neutral)
                )
            }
        }
    }

    private fun resolveNpcSuspiciousEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonNpcSuspiciousEvent,
        choice: Int
    ): GameActionResult {
        val service = engine.dungeonEventService
        val player = state.player
        val itemInstances = state.itemInstances
        if (choice != 1) {
            val line = service.npcSuspiciousRefuseLine { bound -> engine.rollInt(bound) }
            return outcomeService.completeEvent(session, state, pending, player, itemInstances, listOf(line))
        }
        if (service.shouldAmbushOnSuspiciousAccept { bound -> engine.rollInt(bound) }) {
            val line = service.npcSuspiciousScamLine { bound -> engine.rollInt(bound) }
            return outcomeService.startAmbush(session, state, pending, player, itemInstances, line)
        }

        val rewardLine = service.npcSuspiciousRewardLine { bound -> engine.rollInt(bound) }
        val (afterEvent, eventLines) = outcomeService.applyGeneratedEvent(
            player = player,
            itemInstances = itemInstances,
            source = EventSource.NPC_HELP,
            depth = pending.run.depth,
            tierId = pending.tier.id
        )
        return outcomeService.completeEvent(
            session = session,
            state = state,
            pending = pending,
            player = afterEvent,
            itemInstances = itemInstances,
            lines = listOf(rewardLine) + eventLines
        )
    }

    private fun resolveLiquidEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonLiquidEvent,
        choice: Int
    ): GameActionResult {
        val service = engine.dungeonEventService
        val player = state.player
        val itemInstances = state.itemInstances
        if (choice == 3) {
            val ignore = service.liquidIgnoreLine { bound -> engine.rollInt(bound) }
            return outcomeService.completeEvent(session, state, pending, player, itemInstances, listOf(ignore))
        }

        val context = engine.buildEventContext(player, itemInstances, pending.run.depth).copy(tierId = pending.tier.id)
        val event = if (choice == 2) {
            val first = EventEngine.generateEvent(EventSource.LIQUID, context)
            val second = EventEngine.generateEvent(EventSource.LIQUID, context)
            if (outcomeService.hasDirectDamageRisk(first) && !outcomeService.hasDirectDamageRisk(second)) second else first
        } else {
            EventEngine.generateEvent(EventSource.LIQUID, context)
        }
        val afterRaw = EventExecutor.execute(event, player, context)
        val after = stateSupport.applyGoldDeltaAchievements(player, afterRaw)
        return outcomeService.completeEvent(
            session = session,
            state = state,
            pending = pending,
            player = after,
            itemInstances = itemInstances,
            lines = listOf("[${event.rarity}] ${event.description}") + outcomeService.deltaLines(player, after)
        )
    }

    private fun resolveChestEvent(
        session: GameSession,
        state: GameState,
        pending: PendingDungeonChestEvent,
        choice: Int
    ): GameActionResult {
        val service = engine.dungeonEventService
        val player = state.player
        val itemInstances = state.itemInstances
        if (choice == 3) {
            val ignore = service.chestIgnoreLine { bound -> engine.rollInt(bound) }
            return outcomeService.completeEvent(session, state, pending, player, itemInstances, listOf(ignore))
        }
        val mimicChance = service.chestMimicChancePct(inspected = choice == 2)
        if (engine.rollInt(100) < mimicChance) {
            val line = service.chestAmbushLine { bound -> engine.rollInt(bound) }
            return outcomeService.startAmbush(session, state, pending, player, itemInstances, line)
        }
        val (afterEvent, eventLines) = outcomeService.applyGeneratedEvent(
            player = player,
            itemInstances = itemInstances,
            source = EventSource.CHEST_REWARD,
            depth = pending.run.depth,
            tierId = pending.tier.id
        )
        return outcomeService.completeEvent(
            session = session,
            state = state,
            pending = pending,
            player = afterEvent,
            itemInstances = itemInstances,
            lines = eventLines
        )
    }
}
