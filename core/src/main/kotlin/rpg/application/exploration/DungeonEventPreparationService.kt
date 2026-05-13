package rpg.application.exploration

import rpg.application.PendingDungeonChestEvent
import rpg.application.PendingDungeonEvent
import rpg.application.PendingDungeonLiquidEvent
import rpg.application.PendingDungeonNpcItemEvent
import rpg.application.PendingDungeonNpcMoneyEvent
import rpg.application.PendingDungeonNpcSuspiciousEvent
import rpg.engine.GameEngine
import rpg.events.EventSource
import rpg.events.NpcEventVariant
import rpg.model.DungeonRun
import rpg.model.GameState
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.MapTierDef
import rpg.model.PlayerState

internal class DungeonEventPreparationService(
    private val engine: GameEngine
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
                    NpcEventVariant.MONEY -> buildNpcMoneyEvent(state, run, tier, intro)
                    NpcEventVariant.ITEM -> buildNpcItemOrFallbackMoneyEvent(state, run, tier, intro)
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
                detailLine = "O baú pode conter recompensa... ou armadilha."
            )
        }
    }

    private fun buildNpcMoneyEvent(
        state: GameState,
        run: DungeonRun,
        tier: MapTierDef,
        introLine: String
    ): PendingDungeonNpcMoneyEvent {
        val service = engine.dungeonEventService
        val requestedGold = service.requestedGold(
            playerLevel = state.player.level,
            depth = run.depth,
            rollInt = { bound -> engine.rollInt(bound) }
        )
        val detail = service.npcMoneyPitch(requestedGold) { bound -> engine.rollInt(bound) }
        return PendingDungeonNpcMoneyEvent(
            run = run,
            tier = tier,
            introLine = introLine,
            detailLine = detail,
            requestedGold = requestedGold
        )
    }

    private fun buildNpcItemOrFallbackMoneyEvent(
        state: GameState,
        run: DungeonRun,
        tier: MapTierDef,
        introLine: String
    ): PendingDungeonEvent {
        val service = engine.dungeonEventService
        val candidate = pickTravelerRequestedItem(state.player, state.itemInstances)
        if (candidate == null) {
            return buildNpcMoneyEvent(state, run, tier, introLine)
        }
        val qty = if (candidate.itemIds.size >= 2 && engine.rollInt(100) < 45) 2 else 1
        val detail = service.npcItemPitch(
            itemName = candidate.itemName,
            qty = qty,
            rollInt = { bound -> engine.rollInt(bound) }
        )
        return PendingDungeonNpcItemEvent(
            run = run,
            tier = tier,
            introLine = introLine,
            detailLine = detail,
            requestedItemName = candidate.itemName,
            requestedItemIds = candidate.itemIds,
            requestedQty = qty
        )
    }

    private fun pickTravelerRequestedItem(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
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

    private data class ItemRequestCandidate(
        val itemName: String,
        val itemIds: List<String>
    )
}

