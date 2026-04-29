package rpg.events

import rpg.application.model.EventRoomOutcome
import rpg.engine.GameEngine
import rpg.model.MapTierDef
import rpg.model.PlayerState

fun interface DungeonNpcEventHandler {
    fun handle(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome
}

fun interface DungeonLiquidEventHandler {
    fun handle(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        depth: Int,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome
}

fun interface DungeonChestEventHandler {
    fun handle(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome
}

internal class DungeonEventRouter(
    private val engine: GameEngine,
    private val biomeNpcEventBonusPct: (biomeId: String?) -> Int,
    private val npcHandler: DungeonNpcEventHandler,
    private val liquidHandler: DungeonLiquidEventHandler,
    private val chestHandler: DungeonChestEventHandler
) {
    fun eventRoom(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        loot: MutableList<String>,
        difficultyLevel: Int,
        depth: Int,
        tier: MapTierDef,
        questBoard: rpg.quest.QuestBoardState
    ): EventRoomOutcome {
        val roll = engine.rollInt(100)
        val npcThreshold = (34 + biomeNpcEventBonusPct(tier.biomeId)).coerceIn(10, 85)
        val secondaryThreshold = npcThreshold + ((100 - npcThreshold) / 2)
        return when {
            roll < npcThreshold -> npcHandler.handle(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            roll < secondaryThreshold -> liquidHandler.handle(player, itemInstances, depth, questBoard)
            else -> chestHandler.handle(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }
    }
}
