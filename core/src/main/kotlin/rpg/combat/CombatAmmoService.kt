package rpg.combat

import rpg.inventory.InventorySystem
import rpg.item.ItemResolver
import rpg.model.Bonuses
import rpg.model.CombatStatusApplyDef
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.registry.ItemRegistry

internal class CombatAmmoService(
    private val itemResolver: ItemResolver,
    private val itemRegistry: ItemRegistry,
    private val logBuilder: CombatLogBuilder
) {
    fun rangedAmmoRequirementReason(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        if (!playerUsesBowAmmo(player, itemInstances)) return null
        if (player.equipped[EquipSlot.ALJAVA.name].isNullOrBlank()) {
            return "Voce precisa equipar uma aljava para usar arcos."
        }
        val normalizedPlayer = InventorySystem.normalizeAmmoStorage(player, itemInstances, itemRegistry)
        if (InventorySystem.quiverAmmoCount(normalizedPlayer, itemInstances, itemRegistry) <= 0) {
            return "Voce esta sem flechas na aljava."
        }
        return null
    }

    fun playerUsesBowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>
    ): Boolean {
        val mainWeaponId = player.equipped[EquipSlot.WEAPON_MAIN.name] ?: return false
        val weapon = itemResolver.resolve(mainWeaponId, itemInstances) ?: return false
        val normalizedTags = weapon.tags.mapTo(mutableSetOf()) { it.trim().lowercase() }
        if ("bow" in normalizedTags) return true
        val source = "${weapon.id} ${weapon.name}".lowercase()
        return "bow" in source || "arco" in source
    }

    fun consumeArrowAmmo(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int = 1
    ): rpg.inventory.ArrowConsumeResult? {
        if (!playerUsesBowAmmo(player, itemInstances)) return null
        val consumed = InventorySystem.consumeArrowAmmo(
            player = player,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry,
            amount = amount
        ) ?: return null
        val arrowLabel = if (consumed.consumedArrowIds.size == 1) {
            itemResolver.resolve(consumed.consumedArrowIds.first(), itemInstances)?.name ?: "Flecha"
        } else {
            "${consumed.consumedArrowIds.size} flecha(s)"
        }
        logBuilder.combatLog(logBuilder.colorize("Municao consumida: $arrowLabel.", CombatLogBuilder.ansiBlue))
        return consumed
    }

    fun previewAmmoPayload(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        amount: Int
    ): AmmoPayload {
        if (!playerUsesBowAmmo(player, itemInstances)) return AmmoPayload()
        val ammoIds = InventorySystem.peekArrowAmmo(
            player = player,
            itemInstances = itemInstances,
            itemRegistry = itemRegistry,
            amount = amount
        )
        if (ammoIds.isEmpty()) return AmmoPayload()
        var bonuses = Bonuses()
        val statuses = mutableListOf<CombatStatusApplyDef>()
        val labelCounts = linkedMapOf<String, Int>()
        for (ammoId in ammoIds) {
            val ammo = itemResolver.resolve(ammoId, itemInstances) ?: continue
            bonuses += ammo.bonuses
            statuses += ammo.effects.applyStatuses
            labelCounts[ammo.name] = (labelCounts[ammo.name] ?: 0) + 1
        }
        val label = labelCounts.entries.joinToString(", ") { (name, qty) ->
            if (qty > 1) "$name x$qty" else name
        }
        return AmmoPayload(bonuses = bonuses, statuses = statuses, label = label)
    }
}
