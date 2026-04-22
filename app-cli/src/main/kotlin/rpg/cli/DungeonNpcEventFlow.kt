package rpg.cli

import rpg.cli.model.BattleOutcome
import rpg.cli.model.EventRoomOutcome
import rpg.cli.model.InventoryStack
import rpg.cli.model.UseItemResult
import rpg.engine.GameEngine
import rpg.events.DungeonEventService
import rpg.events.EventEngine
import rpg.events.EventSource
import rpg.events.NpcEventVariant
import rpg.model.ItemType
import rpg.model.MapTierDef
import rpg.model.PlayerState
import rpg.monster.MonsterInstance

internal class DungeonNpcEventFlow(
    private val engine: GameEngine,
    private val dungeonEventService: DungeonEventService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val eventContext: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int
    ) -> rpg.events.EventContext,
    private val support: DungeonEventSupport,
    private val buildInventoryStacks: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> List<InventoryStack>,
    private val battleMonster: (
        playerState: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        monster: MonsterInstance,
        tier: MapTierDef,
        lootCollector: MutableList<String>?,
        isBoss: Boolean
    ) -> BattleOutcome,
    private val applyBattleQuestProgress: (
        board: rpg.quest.QuestBoardState,
        monster: MonsterInstance,
        outcome: BattleOutcome,
        isBoss: Boolean
    ) -> rpg.quest.QuestBoardState,
    private val onGoldSpent: (player: PlayerState, amount: Long) -> PlayerState,
    private val emit: (String) -> Unit
) {
    fun npcEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val intro = dungeonEventService.npcIntro { bound -> engine.rollInt(bound) }
        emit("\nEvento: $intro")

        return when (dungeonEventService.pickNpcVariant { bound -> engine.rollInt(bound) }) {
            NpcEventVariant.MONEY -> npcMoneyRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            NpcEventVariant.ITEM -> npcItemRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            NpcEventVariant.SUSPICIOUS -> npcSuspiciousEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }
    }

    fun runNpcAmbush(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val mimic = engine.buildMimicMonster(difficultyLevel)
        val outcome = battleMonster(player, itemInstances, mimic, tier, loot, false)
        val progressedBoard = if (outcome.victory) {
            applyBattleQuestProgress(questBoard, mimic, outcome, false)
        } else {
            questBoard
        }
        return EventRoomOutcome(
            player = outcome.playerAfter,
            itemInstances = outcome.itemInstances,
            battleOutcome = outcome,
            questBoard = progressedBoard
        )
    }

    private fun npcMoneyRequestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val requestedGold = dungeonEventService.requestedGold(
            playerLevel = player.level,
            depth = depth,
            rollInt = { bound -> engine.rollInt(bound) }
        )
        emit(dungeonEventService.npcMoneyPitch(requestedGold) { bound -> engine.rollInt(bound) })
        emit("1. Entregar ouro")
        emit("2. Recusar")
        emit("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2

        if (choice == 2) {
            if (dungeonEventService.shouldAmbushOnMoneyRefuse { bound -> engine.rollInt(bound) }) {
                emit(dungeonEventService.npcMoneyRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            emit("O viajante murmura algo e some no corredor.")
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        if (player.gold < requestedGold) {
            emit(dungeonEventService.npcMoneyNoGoldLine { bound -> engine.rollInt(bound) })
            if (dungeonEventService.shouldAmbushOnMoneyNoGold { bound -> engine.rollInt(bound) }) {
                emit(dungeonEventService.npcMoneyRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val paidPlayer = onGoldSpent(player.copy(gold = player.gold - requestedGold), requestedGold.toLong())
        return when {
            dungeonEventService.shouldScamOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                emit(dungeonEventService.npcMoneyScamLine { bound -> engine.rollInt(bound) })
                runNpcAmbush(paidPlayer, itemInstances, loot, difficultyLevel, tier, questBoard)
            }

            dungeonEventService.shouldRewardOnMoneyGive { bound -> engine.rollInt(bound) } -> {
                emit(dungeonEventService.npcMoneyRewardLine { bound -> engine.rollInt(bound) })
                val context = eventContext(paidPlayer, itemInstances, depth)
                val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
                emit("[${event.rarity}] ${event.description}")
                val updated = support.applyEventWithFeedback(paidPlayer, itemInstances, event, context)
                EventRoomOutcome(player = updated, questBoard = questBoard)
            }

            else -> {
                emit(dungeonEventService.npcMoneyNeutralLine { bound -> engine.rollInt(bound) })
                EventRoomOutcome(player = paidPlayer, questBoard = questBoard)
            }
        }
    }

    private fun npcItemRequestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val requested = pickTravelerRequestedStack(player, itemInstances)
        if (requested == null) {
            emit(dungeonEventService.npcItemNoItemsLine { bound -> engine.rollInt(bound) })
            return npcMoneyRequestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }

        val qty = if (requested.quantity >= 2 && engine.rollInt(100) < 45) 2 else 1
        emit(
            dungeonEventService.npcItemPitch(
                itemName = requested.item.name,
                qty = qty,
                rollInt = { bound -> engine.rollInt(bound) }
            )
        )
        emit("1. Entregar item")
        emit("2. Recusar")
        emit("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2
        if (choice == 2) {
            if (dungeonEventService.shouldAmbushOnItemRefuse { bound -> engine.rollInt(bound) }) {
                emit(dungeonEventService.npcItemRefuseLine { bound -> engine.rollInt(bound) })
                return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
            }
            emit("O viajante segue por outro caminho.")
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val consumed = consumeInventoryItems(player, itemInstances, requested.itemIds, qty)
        return when {
            dungeonEventService.shouldScamOnItemGive { bound -> engine.rollInt(bound) } -> {
                emit(dungeonEventService.npcItemScamLine { bound -> engine.rollInt(bound) })
                runNpcAmbush(consumed.player, consumed.itemInstances, loot, difficultyLevel, tier, questBoard)
            }

            dungeonEventService.shouldRewardOnItemGive { bound -> engine.rollInt(bound) } -> {
                emit(dungeonEventService.npcItemRewardLine { bound -> engine.rollInt(bound) })
                val context = eventContext(consumed.player, consumed.itemInstances, depth)
                val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
                emit("[${event.rarity}] ${event.description}")
                val updated = support.applyEventWithFeedback(consumed.player, consumed.itemInstances, event, context)
                EventRoomOutcome(player = updated, itemInstances = consumed.itemInstances, questBoard = questBoard)
            }

            else -> {
                emit(dungeonEventService.npcItemNeutralLine { bound -> engine.rollInt(bound) })
                EventRoomOutcome(player = consumed.player, itemInstances = consumed.itemInstances, questBoard = questBoard)
            }
        }
    }

    private fun npcSuspiciousEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        emit(dungeonEventService.npcSuspiciousPitch { bound -> engine.rollInt(bound) })
        emit("1. Seguir a indicacao")
        emit("2. Ignorar")
        emit("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 2) ?: 2
        if (choice == 2) {
            emit(dungeonEventService.npcSuspiciousRefuseLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        if (dungeonEventService.shouldAmbushOnSuspiciousAccept { bound -> engine.rollInt(bound) }) {
            emit(dungeonEventService.npcSuspiciousScamLine { bound -> engine.rollInt(bound) })
            return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
        }

        emit(dungeonEventService.npcSuspiciousRewardLine { bound -> engine.rollInt(bound) })
        val context = eventContext(player, itemInstances, depth)
        val event = EventEngine.generateEvent(EventSource.NPC_HELP, context)
        emit("[${event.rarity}] ${event.description}")
        val updated = support.applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }

    private fun pickTravelerRequestedStack(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): InventoryStack? {
        val candidates = buildInventoryStacks(player, itemInstances).filter { it.item.type != ItemType.EQUIPMENT }
        if (candidates.isEmpty()) return null
        return candidates[engine.rollInt(candidates.size)]
    }

    private fun consumeInventoryItems(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        itemIds: List<String>,
        quantity: Int
    ): UseItemResult {
        val count = quantity.coerceAtLeast(1)
        val toConsume = itemIds.take(count)
        val inventory = player.inventory.toMutableList()
        val instances = itemInstances.toMutableMap()
        var removed = 0
        for (itemId in toConsume) {
            if (inventory.remove(itemId)) {
                removed++
                if (instances.containsKey(itemId)) {
                    instances.remove(itemId)
                }
            }
        }
        if (removed > 0) {
            emit("Itens entregues: x$removed")
        }
        return UseItemResult(
            player = player.copy(inventory = inventory),
            itemInstances = instances.toMap()
        )
    }
}
