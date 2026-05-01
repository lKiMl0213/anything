package rpg.cli

import rpg.application.PendingEncounter
import rpg.engine.GameEngine
import rpg.inventory.InventorySystem
import rpg.model.ItemInstance
import rpg.model.PlayerState

internal data class ClassDungeonCombatResult(
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val messages: List<String>,
    val bonusGold: Int
)

internal fun applyClassDungeonCombatRewards(
    engine: GameEngine,
    encounter: PendingEncounter,
    player: PlayerState,
    itemInstances: Map<String, ItemInstance>
): ClassDungeonCombatResult {
    if (encounter.classDungeon == null) {
        return ClassDungeonCombatResult(player, itemInstances, emptyList(), bonusGold = 0)
    }

    var workingPlayer = player
    var workingInstances = itemInstances
    var bonusGold = 0
    val messages = mutableListOf<String>()

    val collectibleDrops = engine.classQuestService.collectibleDropsForDungeonKill(
        player = workingPlayer,
        monsterId = encounter.monster.archetypeId,
        isBoss = encounter.isBoss
    )
    if (collectibleDrops.isNotEmpty()) {
        val mergedInstances = workingInstances.toMutableMap()
        for (drop in collectibleDrops) {
            mergedInstances[drop.id] = drop
        }
        workingInstances = mergedInstances
        val insert = InventorySystem.addItemsWithLimit(
            player = workingPlayer,
            itemInstances = workingInstances,
            itemRegistry = engine.itemRegistry,
            incomingItemIds = collectibleDrops.map { it.id }
        )
        val rejectedGenerated = insert.rejected.filter { workingInstances.containsKey(it) }
        if (rejectedGenerated.isNotEmpty()) {
            workingInstances = workingInstances - rejectedGenerated.toSet()
            messages += "Inventario cheio: parte dos drops exclusivos da instancia foi perdida."
        }
        workingPlayer = workingPlayer.copy(
            inventory = insert.inventory,
            quiverInventory = insert.quiverInventory,
            selectedAmmoTemplateId = insert.selectedAmmoTemplateId
        )
        val acceptedSet = insert.accepted.toSet()
        val grouped = collectibleDrops
            .filter { it.id in acceptedSet }
            .groupingBy { it.templateId to it.name }
            .eachCount()
        for ((entry, qty) in grouped) {
            val (_, name) = entry
            val label = if (qty > 1) "$name x$qty" else name
            messages += "Drop exclusivo da instancia: $label"
        }
        val collectedItems = grouped.entries.associate { (entry, qty) -> entry.first to qty }
        if (collectedItems.isNotEmpty()) {
            val collectUpdate = engine.classQuestService.onItemsCollected(
                player = workingPlayer,
                itemInstances = workingInstances,
                collectedItems = collectedItems
            )
            val goldDelta = (collectUpdate.player.gold - workingPlayer.gold).coerceAtLeast(0)
            bonusGold += goldDelta
            workingPlayer = collectUpdate.player
            workingInstances = collectUpdate.itemInstances
            messages += collectUpdate.messages
        }
    }

    val outcomeUpdate = engine.classQuestService.onCombatOutcome(
        player = workingPlayer,
        itemInstances = workingInstances,
        monsterId = encounter.monster.archetypeId,
        isBoss = encounter.isBoss,
        monsterBaseType = encounter.monster.baseType
    )
    val outcomeGold = (outcomeUpdate.player.gold - workingPlayer.gold).coerceAtLeast(0)
    bonusGold += outcomeGold
    workingPlayer = outcomeUpdate.player
    workingInstances = outcomeUpdate.itemInstances
    messages += outcomeUpdate.messages

    return ClassDungeonCombatResult(
        player = workingPlayer,
        itemInstances = workingInstances,
        messages = messages,
        bonusGold = bonusGold
    )
}
