package rpg.application.inventory

import kotlin.math.abs
import rpg.classquest.ClassQuestTagRules
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.item.ResolvedItem
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.PlayerState

internal class InventoryEquipRulesSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val accessorySlots: List<String>,
    private val offhandBlockedId: String
) {
    fun classTagDisplayLabel(tag: String): String {
        val normalized = tag.trim().lowercase()
        if (normalized.isBlank() || normalized == ClassQuestTagRules.anyClassTag) {
            return "Any.Class (neutro)"
        }
        return repo.classes[normalized]?.name
            ?: repo.subclasses[normalized]?.name
            ?: repo.specializations[normalized]?.name
            ?: normalized
    }

    fun questEquipRestrictionReason(player: PlayerState, item: ResolvedItem): String? {
        val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
        if (!ClassQuestTagRules.canUseClassTag(player, classTag)) {
            return "Item restrito a ${classTagDisplayLabel(classTag)}."
        }
        val classLock = ClassQuestTagRules.classLocked(item.tags)
        if (classLock != null && classLock != player.classId.lowercase()) {
            val className = repo.classes[classLock]?.name ?: classLock
            return "Item restrito a classe $className."
        }
        val pathLock = ClassQuestTagRules.pathLocked(item.tags) ?: return null
        val ownerClass = classLock ?: player.classId.lowercase()
        val allowed = ClassQuestTagRules.allowedPaths(player, ownerClass)
        if (pathLock !in allowed) {
            val pathName = repo.subclasses[pathLock]?.name ?: repo.specializations[pathLock]?.name ?: pathLock
            return "Item restrito ao caminho $pathName."
        }
        return null
    }

    fun previewEquipDelta(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>
    ): EquipComparisonPreviewData? {
        if (questEquipRestrictionReason(player, item) != null) return null
        val target = resolveEquipPreviewTarget(player, item, itemInstances) ?: return null
        val equipped = player.equipped.toMutableMap()
        when (target.slotKey) {
            EquipSlot.WEAPON_MAIN.name -> {
                equipped[EquipSlot.WEAPON_MAIN.name] = item.id
                if (item.twoHanded) {
                    equipped[EquipSlot.WEAPON_OFF.name] = offhandBlockedId
                } else if (equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
                    equipped.remove(EquipSlot.WEAPON_OFF.name)
                }
            }
            EquipSlot.WEAPON_OFF.name -> {
                if (equipped[EquipSlot.WEAPON_OFF.name] != offhandBlockedId) {
                    equipped[EquipSlot.WEAPON_OFF.name] = item.id
                } else {
                    return null
                }
            }
            else -> equipped[target.slotKey] = item.id
        }
        return EquipComparisonPreviewData(
            slotKey = target.slotKey,
            replacedItem = target.currentItemId?.let { engine.itemResolver.resolve(it, itemInstances) },
            before = engine.computePlayerStats(player, itemInstances),
            after = engine.computePlayerStats(player.copy(equipped = equipped), itemInstances)
        )
    }

    fun formatEquipComparison(before: ComputedStats, after: ComputedStats): String {
        return listOf(
            "DMG" to (after.derived.damagePhysical - before.derived.damagePhysical),
            "M-DMG" to (after.derived.damageMagic - before.derived.damageMagic),
            "DEF" to (after.derived.defPhysical - before.derived.defPhysical),
            "M-DEF" to (after.derived.defMagic - before.derived.defMagic),
            "HP" to (after.derived.hpMax - before.derived.hpMax),
            "MP" to (after.derived.mpMax - before.derived.mpMax),
            "CRIT" to (after.derived.critChancePct - before.derived.critChancePct),
            "ACC" to (after.derived.accuracy - before.derived.accuracy),
            "EVA" to (after.derived.evasion - before.derived.evasion),
            "ASPD" to (after.derived.attackSpeed - before.derived.attackSpeed),
            "MOVE" to (after.derived.moveSpeed - before.derived.moveSpeed),
            "CDR" to (after.derived.cdrPct - before.derived.cdrPct),
            "DR" to (after.derived.damageReductionPct - before.derived.damageReductionPct)
        ).mapNotNull { (label, delta) ->
            if (abs(delta) < 0.01) null else "$label ${formatSignedDouble(delta)}"
        }.joinToString(" | ")
    }

    fun buildUnequippedPreview(player: PlayerState, slotKey: String): PlayerState {
        val equipped = player.equipped.toMutableMap()
        equipped.remove(slotKey)
        if (slotKey == EquipSlot.WEAPON_MAIN.name && equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
            equipped.remove(EquipSlot.WEAPON_OFF.name)
        }
        return player.copy(equipped = equipped)
    }

    fun resolveAccessorySlotForAutoPreview(
        equipped: Map<String, String>,
        itemInstances: Map<String, ItemInstance>
    ): String? {
        val emptySlot = accessorySlots.firstOrNull { !equipped.containsKey(it) }
        if (emptySlot != null) return emptySlot
        return accessorySlots.minByOrNull { slotKey ->
            val currentId = equipped[slotKey]
            engine.itemResolver.resolve(currentId ?: "", itemInstances)?.powerScore ?: Int.MAX_VALUE
        }
    }

    fun isTwoHanded(itemId: String, itemInstances: Map<String, ItemInstance>): Boolean {
        return engine.itemResolver.resolve(itemId, itemInstances)?.twoHanded == true
    }

    private fun resolveEquipPreviewTarget(
        player: PlayerState,
        item: ResolvedItem,
        itemInstances: Map<String, ItemInstance>
    ): EquipPreviewTarget? {
        val slot = item.slot ?: return null
        val equipped = player.equipped
        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val targetSlot = resolveAccessorySlotForAutoPreview(equipped, itemInstances) ?: return null
                EquipPreviewTarget(slotKey = targetSlot, currentItemId = equipped[targetSlot])
            }
            EquipSlot.WEAPON_MAIN -> EquipPreviewTarget(
                slotKey = EquipSlot.WEAPON_MAIN.name,
                currentItemId = equipped[EquipSlot.WEAPON_MAIN.name]?.takeIf { it != offhandBlockedId }
            )
            EquipSlot.WEAPON_OFF -> {
                if (equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) null else EquipPreviewTarget(
                    slotKey = EquipSlot.WEAPON_OFF.name,
                    currentItemId = equipped[EquipSlot.WEAPON_OFF.name]
                )
            }
            else -> EquipPreviewTarget(
                slotKey = slot.name,
                currentItemId = equipped[slot.name]?.takeIf { it != offhandBlockedId }
            )
        }
    }

    private fun formatSignedDouble(value: Double): String = if (value >= 0.0) "+${format(value)}" else format(value)

    private fun format(value: Double): String = "%.1f".format(value)

    private data class EquipPreviewTarget(
        val slotKey: String,
        val currentItemId: String?
    )
}
