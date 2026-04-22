package rpg.cli

import rpg.cli.model.EventRoomOutcome
import rpg.engine.GameEngine
import rpg.events.DungeonEventService
import rpg.events.EventEngine
import rpg.events.EventSource
import rpg.model.MapTierDef
import rpg.model.PlayerState

internal class DungeonChestEventFlow(
    private val engine: GameEngine,
    private val dungeonEventService: DungeonEventService,
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val eventContext: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int
    ) -> rpg.events.EventContext,
    private val runNpcAmbush: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ) -> EventRoomOutcome,
    private val support: DungeonEventSupport,
    private val emit: (String) -> Unit
) {
    fun chestEvent(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        emit("\nEvento: ${dungeonEventService.chestIntro { bound -> engine.rollInt(bound) }}")
        emit("1. Abrir rapido")
        emit("2. Inspecionar antes de abrir")
        emit("3. Ignorar")
        emit("x. Voltar")
        val choice = readMenuChoice("Escolha: ", 1, 3) ?: 3
        if (choice == 3) {
            emit(dungeonEventService.chestIgnoreLine { bound -> engine.rollInt(bound) })
            return EventRoomOutcome(player = player, questBoard = questBoard)
        }

        val mimicChance = dungeonEventService.chestMimicChancePct(inspected = choice == 2)
        if (engine.rollInt(100) < mimicChance) {
            emit(dungeonEventService.chestAmbushLine { bound -> engine.rollInt(bound) })
            return runNpcAmbush(player, itemInstances, loot, difficultyLevel, tier, questBoard)
        }
        val context = eventContext(player, itemInstances, depth)
        val event = EventEngine.generateEvent(EventSource.CHEST_REWARD, context)
        emit("[${event.rarity}] ${event.description}")
        val updated = support.applyEventWithFeedback(player, itemInstances, event, context)
        return EventRoomOutcome(player = updated, questBoard = questBoard)
    }
}
