package rpg.events

import rpg.cli.model.EventRoomOutcome
import rpg.cli.DungeonChestEventFlow
import rpg.cli.DungeonLiquidEventFlow
import rpg.cli.DungeonNpcEventFlow
import rpg.engine.GameEngine
import rpg.model.MapTierDef
import rpg.model.PlayerState

internal class DungeonEventRouter(
    private val engine: GameEngine,
    private val biomeNpcEventBonusPct: (biomeId: String?) -> Int,
    private val npcFlow: DungeonNpcEventFlow,
    private val liquidFlow: DungeonLiquidEventFlow,
    private val chestFlow: DungeonChestEventFlow
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
            roll < npcThreshold -> npcFlow.npcEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
            roll < secondaryThreshold -> liquidFlow.liquidEvent(player, itemInstances, depth, questBoard)
            else -> chestFlow.chestEvent(player, itemInstances, loot, difficultyLevel, depth, tier, questBoard)
        }
    }
}
