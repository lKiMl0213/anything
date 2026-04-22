package rpg.cli

import rpg.cli.model.EventRoomOutcome
import rpg.engine.GameEngine
import rpg.events.DungeonEventService
import rpg.events.EventEngine
import rpg.events.EventSource
import rpg.model.PlayerState

internal class DungeonLiquidEventFlow(
    private val engine: GameEngine,
    private val dungeonEventService: DungeonEventService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val eventContext: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int
    ) -> rpg.events.EventContext,
    private val support: DungeonEventSupport,
    private val emit: (String) -> Unit
) {
    fun liquidEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        emit("\nEvento: ${dungeonEventService.liquidIntro { bound -> engine.rollInt(bound) }}")
        emit("1. Provar")
        emit("2. Testar com cuidado")
        emit("3. Ignorar")
        emit("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 3) ?: 3
        if (choice == 3) {
            emit(dungeonEventService.liquidIgnoreLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val context = eventContext(player, itemInstances, depth)
        val event = if (choice == 2) {
            val first = EventEngine.generateEvent(EventSource.LIQUID, context)
            val second = EventEngine.generateEvent(EventSource.LIQUID, context)
            if (support.hasDirectDamageRisk(first) && !support.hasDirectDamageRisk(second)) second else first
        } else {
            EventEngine.generateEvent(EventSource.LIQUID, context)
        }
        emit("[${event.rarity}] ${event.description}")
        val updated = support.applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }
}
