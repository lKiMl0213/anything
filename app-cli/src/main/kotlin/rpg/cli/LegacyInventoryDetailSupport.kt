package rpg.cli

import kotlin.math.abs
import rpg.cli.model.*
import rpg.classquest.ClassQuestTagRules
import rpg.engine.ComputedStats
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.inventory.InventorySystem
import rpg.item.ItemRarity
import rpg.model.EquipSlot
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.status.StatusSystem

internal class LegacyInventoryDetailSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val accessorySlots: List<String>,
    private val offhandBlockedId: String,
    private val ansiCombatReset: String,
    private val computePlayerStats: (
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> ComputedStats,
    private val canonicalItemId: (
        itemId: String,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ) -> String,
    private val equippedSlotLabel: (String) -> String,
    private val format: (Double) -> String,
    private val formatSigned: (Int) -> String,
    private val formatSignedDouble: (Double) -> String
) {
    fun printInventoryItemDetails(
        player: PlayerState,
        itemInstances: Map<String, rpg.model.ItemInstance>,
        stack: InventoryStack,
        item: rpg.item.ResolvedItem
    ) {
        println("Raridade: ${colorizeUi(item.rarity.colorLabel, item.rarity.ansiColorCode)}")
        if (item.qualityRollPct != 100 || item.powerScore > 0) {
            val powerLabel = if (item.powerScore > 0) " | Poder ${item.powerScore}" else ""
            println("Qualidade: ${item.qualityRollPct}%$powerLabel")
        }
        if (item.minLevel > 1) {
            println("Nivel requerido: ${item.minLevel}")
        }
        if (item.description.isNotBlank()) {
            println("Descricao: ${item.description}")
        }
        if (item.affixes.isNotEmpty()) {
            println("Afixos: ${item.affixes.joinToString(", ")}")
        }
        when (item.type) {
            ItemType.CONSUMABLE -> {
                val restores = mutableListOf<String>()
                if (item.effects.hpRestore > 0.0) restores += "HP +${format(item.effects.hpRestore)}"
                if (item.effects.mpRestore > 0.0) restores += "MP +${format(item.effects.mpRestore)}"
                if (item.effects.hpRestorePct > 0.0) restores += "HP +${format(item.effects.hpRestorePct)}%"
                if (item.effects.mpRestorePct > 0.0) restores += "MP +${format(item.effects.mpRestorePct)}%"
                if (item.effects.fullRestore) restores += "Restaura HP/MP total"
                if (item.effects.clearNegativeStatuses) restores += "Remove status negativos"
                if (item.effects.statusImmunitySeconds > 0.0) {
                    restores += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
                }
                if (item.effects.roomAttributeMultiplierPct != 0.0 && item.effects.roomAttributeDurationRooms > 0) {
                    restores +=
                        "Buff ${format(item.effects.roomAttributeMultiplierPct)}% por ${item.effects.roomAttributeDurationRooms} sala(s)"
                }
                if (item.effects.runAttributeMultiplierPct != 0.0) {
                    restores += "Buff de run ${format(item.effects.runAttributeMultiplierPct)}%"
                }
                if (restores.isNotEmpty()) {
                    println("Efeito: ${restores.joinToString(" | ")}")
                }
            }
            ItemType.MATERIAL -> {
                val canonical = canonicalItemId(stack.sampleItemId, itemInstances)
                if (InventorySystem.isArrowAmmo(stack.sampleItemId, itemInstances, engine.itemRegistry)) {
                    println("Uso: municao para armas de arco; carregue pela aljava.")
                    val ammoBonusLabel = formatItemBonuses(item)
                    if (ammoBonusLabel.isNotBlank()) {
                        println("Bonus da municao: $ammoBonusLabel")
                    }
                    val ammoEffectLabel = formatItemEffectsSummary(item)
                    if (ammoEffectLabel.isNotBlank()) {
                        println("Efeito da municao: $ammoEffectLabel")
                    }
                }
                val gatherSources = repo.gatherNodes.values
                    .filter { it.resourceItemId == canonical }
                    .take(4)
                    .map { it.name }
                val uses = repo.craftRecipes.values
                    .filter { recipe -> recipe.ingredients.any { it.itemId == canonical } }
                    .take(6)
                    .map { "${it.name} (${it.discipline.name.lowercase()})" }
                if (gatherSources.isNotEmpty()) {
                    println("Origem: ${gatherSources.joinToString(", ")}")
                }
                if (uses.isNotEmpty()) {
                    println("Usado em: ${uses.joinToString(", ")}")
                }
            }
            ItemType.EQUIPMENT -> {
                val slotLabel = item.slot?.name ?: "desconhecido"
                val handLabel = if (item.twoHanded) " (duas maos)" else ""
                println("Slot: $slotLabel$handLabel")
                val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
                println("Tag de classe: ${classTagDisplayLabel(classTag)}")
                val classLock = ClassQuestTagRules.classLocked(item.tags)
                val pathLock = ClassQuestTagRules.pathLocked(item.tags)
                if (classLock != null || pathLock != null) {
                    val classLabel = classLock ?: "-"
                    val pathLabel = pathLock ?: "-"
                    println("Restricoes: classe=$classLabel | caminho=$pathLabel")
                }
                if (ClassQuestTagRules.isQuestReward(item.tags)) {
                    println("Item de set de quest (revenda fixa).")
                }
                val bonusLabel = formatItemBonuses(item)
                if (bonusLabel.isNotBlank()) {
                    println("Bonus: $bonusLabel")
                }
                val effectLabel = formatItemEffectsSummary(item)
                if (effectLabel.isNotBlank()) {
                    println("Efeito: $effectLabel")
                }

                val preview = previewEquipDelta(player, item, itemInstances)
                if (preview != null) {
                    val equippedLabel = preview.replacedItem?.let(::itemDisplayLabel) ?: "Vazio"
                    println("Comparando com (${equippedSlotLabel(preview.slotKey)}): $equippedLabel")
                    val deltaLabel = formatEquipComparison(preview.before, preview.after)
                    if (deltaLabel.isNotBlank()) {
                        println("Delta: $deltaLabel")
                    } else {
                        println("Delta: sem alteracoes relevantes nos atributos principais.")
                    }
                }
            }
        }
    }

    fun questEquipRestrictionReason(player: PlayerState, item: rpg.item.ResolvedItem): String? {
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

    fun formatItemBonuses(item: rpg.item.ResolvedItem): String {
        val attrs = item.bonuses.attributes
        val attrParts = mutableListOf<String>()
        if (attrs.str != 0) attrParts += "STR ${formatSigned(attrs.str)}"
        if (attrs.agi != 0) attrParts += "AGI ${formatSigned(attrs.agi)}"
        if (attrs.dex != 0) attrParts += "DEX ${formatSigned(attrs.dex)}"
        if (attrs.vit != 0) attrParts += "VIT ${formatSigned(attrs.vit)}"
        if (attrs.`int` != 0) attrParts += "INT ${formatSigned(attrs.`int`)}"
        if (attrs.spr != 0) attrParts += "SPR ${formatSigned(attrs.spr)}"
        if (attrs.luk != 0) attrParts += "LUK ${formatSigned(attrs.luk)}"
        val add = item.bonuses.derivedAdd
        val mult = item.bonuses.derivedMult
        val derivedParts = mutableListOf<String>()
        if (add.damagePhysical != 0.0) derivedParts += "DMG ${formatSignedDouble(add.damagePhysical)}"
        if (add.damageMagic != 0.0) derivedParts += "M-DMG ${formatSignedDouble(add.damageMagic)}"
        if (add.defPhysical != 0.0) derivedParts += "DEF ${formatSignedDouble(add.defPhysical)}"
        if (add.defMagic != 0.0) derivedParts += "M-DEF ${formatSignedDouble(add.defMagic)}"
        if (add.hpMax != 0.0) derivedParts += "HP ${formatSignedDouble(add.hpMax)}"
        if (add.mpMax != 0.0) derivedParts += "MP ${formatSignedDouble(add.mpMax)}"
        if (add.attackSpeed != 0.0) derivedParts += "ASPD ${formatSignedDouble(add.attackSpeed)}"
        if (add.moveSpeed != 0.0) derivedParts += "MOVE ${formatSignedDouble(add.moveSpeed)}"
        if (add.critChancePct != 0.0) derivedParts += "CRIT ${formatSignedDouble(add.critChancePct)}%"
        if (add.critDamagePct != 0.0) derivedParts += "CRIT DMG ${formatSignedDouble(add.critDamagePct)}%"
        if (add.accuracy != 0.0) derivedParts += "ACC ${formatSignedDouble(add.accuracy)}"
        if (add.evasion != 0.0) derivedParts += "EVA ${formatSignedDouble(add.evasion)}"
        if (add.cdrPct != 0.0) derivedParts += "CDR ${formatSignedDouble(add.cdrPct)}%"
        if (add.dropBonusPct != 0.0) derivedParts += "DROP ${formatSignedDouble(add.dropBonusPct)}%"
        if (add.hpRegen != 0.0) derivedParts += "HP REG ${formatSignedDouble(add.hpRegen)}"
        if (add.mpRegen != 0.0) derivedParts += "MP REG ${formatSignedDouble(add.mpRegen)}"
        if (add.vampirismPct != 0.0) derivedParts += "VAMP ${formatSignedDouble(add.vampirismPct)}%"
        if (add.damageReductionPct != 0.0) derivedParts += "DR ${formatSignedDouble(add.damageReductionPct)}%"
        if (add.tenacityPct != 0.0) derivedParts += "TEN ${formatSignedDouble(add.tenacityPct)}%"
        if (add.penPhysical != 0.0) derivedParts += "P-PEN ${formatSignedDouble(add.penPhysical)}"
        if (add.penMagic != 0.0) derivedParts += "M-PEN ${formatSignedDouble(add.penMagic)}"
        if (add.xpGainPct != 0.0) derivedParts += "XP ${formatSignedDouble(add.xpGainPct)}%"
        if (mult.attackSpeed != 0.0) derivedParts += "ASPD ${formatSignedDouble(mult.attackSpeed)}%"
        if (mult.moveSpeed != 0.0) derivedParts += "MOVE ${formatSignedDouble(mult.moveSpeed)}%"
        if (mult.damagePhysical != 0.0) derivedParts += "DMG ${formatSignedDouble(mult.damagePhysical)}%"
        if (mult.damageMagic != 0.0) derivedParts += "M-DMG ${formatSignedDouble(mult.damageMagic)}%"
        if (mult.defPhysical != 0.0) derivedParts += "DEF ${formatSignedDouble(mult.defPhysical)}%"
        if (mult.defMagic != 0.0) derivedParts += "M-DEF ${formatSignedDouble(mult.defMagic)}%"
        return (attrParts + derivedParts).joinToString(", ")
    }

    fun formatItemEffectsSummary(item: rpg.item.ResolvedItem): String {
        val parts = mutableListOf<String>()
        if (item.effects.statusImmunitySeconds > 0.0) {
            parts += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
        }
        if (item.effects.runAttributeMultiplierPct != 0.0) {
            parts += "Buff de run ${format(item.effects.runAttributeMultiplierPct)}%"
        }
        for (status in item.effects.applyStatuses) {
            val chanceLabel = if (status.chancePct > 0.0) "${format(status.chancePct)}%" else "100%"
            val durationLabel = if (status.durationSeconds > 0.0) " por ${format(status.durationSeconds)}s" else ""
            parts += "${StatusSystem.displayName(status.type)} $chanceLabel$durationLabel"
        }
        return parts.joinToString(", ")
    }

    fun itemDisplayLabel(item: rpg.item.ResolvedItem): String {
        return itemDisplayLabel(item.name, item.rarity)
    }

    fun itemDisplayLabel(name: String, rarity: ItemRarity): String {
        return colorizeUi("[${rarity.colorLabel}] $name", rarity.ansiColorCode)
    }

    private fun previewEquipDelta(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): EquipComparisonPreview? {
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
        val before = computePlayerStats(player, itemInstances)
        val after = computePlayerStats(player.copy(equipped = equipped), itemInstances)
        return EquipComparisonPreview(
            slotKey = target.slotKey,
            replacedItem = target.currentItemId?.let { currentId ->
                engine.itemResolver.resolve(currentId, itemInstances)
            },
            before = before,
            after = after
        )
    }

    private fun resolveEquipPreviewTarget(
        player: PlayerState,
        item: rpg.item.ResolvedItem,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): EquipPreviewTarget? {
        val slot = item.slot ?: return null
        val equipped = player.equipped
        return when (slot) {
            EquipSlot.ACCESSORY -> {
                val emptySlot = accessorySlots.firstOrNull { !equipped.containsKey(it) }
                if (emptySlot != null) {
                    EquipPreviewTarget(slotKey = emptySlot, currentItemId = null)
                } else {
                    val targetSlot = accessorySlots.minByOrNull { slotKey ->
                        val currentId = equipped[slotKey]
                        engine.itemResolver.resolve(currentId ?: "", itemInstances)?.powerScore ?: Int.MAX_VALUE
                    } ?: return null
                    EquipPreviewTarget(slotKey = targetSlot, currentItemId = equipped[targetSlot])
                }
            }
            EquipSlot.WEAPON_MAIN -> EquipPreviewTarget(
                slotKey = EquipSlot.WEAPON_MAIN.name,
                currentItemId = equipped[EquipSlot.WEAPON_MAIN.name]?.takeIf { it != offhandBlockedId }
            )
            EquipSlot.WEAPON_OFF -> {
                if (equipped[EquipSlot.WEAPON_OFF.name] == offhandBlockedId) {
                    null
                } else {
                    EquipPreviewTarget(
                        slotKey = EquipSlot.WEAPON_OFF.name,
                        currentItemId = equipped[EquipSlot.WEAPON_OFF.name]
                    )
                }
            }
            else -> EquipPreviewTarget(
                slotKey = slot.name,
                currentItemId = equipped[slot.name]?.takeIf { it != offhandBlockedId }
            )
        }
    }

    private fun formatEquipComparison(before: ComputedStats, after: ComputedStats): String {
        val deltas = listOf(
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
        }
        return deltas.joinToString(" | ")
    }

    private fun classTagDisplayLabel(tag: String): String {
        val normalized = tag.trim().lowercase()
        if (normalized.isBlank() || normalized == ClassQuestTagRules.anyClassTag) {
            return "Any.Class (neutro)"
        }
        val className = repo.classes[normalized]?.name
        if (className != null) return className
        val subclassName = repo.subclasses[normalized]?.name
        if (subclassName != null) return subclassName
        val specializationName = repo.specializations[normalized]?.name
        if (specializationName != null) return specializationName
        return normalized
    }

    private fun colorizeUi(text: String, colorCode: String): String {
        return "$colorCode$text$ansiCombatReset"
    }
}
