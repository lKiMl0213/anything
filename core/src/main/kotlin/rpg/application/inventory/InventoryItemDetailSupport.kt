package rpg.application.inventory

import rpg.classquest.ClassQuestTagRules
import rpg.classsystem.RaceBonusSupport
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.item.ResolvedItem
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.status.StatusSystem

internal class InventoryItemDetailSupport(
    private val repo: DataRepository,
    private val engine: GameEngine,
    private val equipRules: InventoryEquipRulesSupport,
    private val itemDisplayLabel: (ResolvedItem) -> String,
    private val equippedSlotLabel: (String) -> String
) {
    fun buildInventoryItemDetail(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        stack: InventoryStackView
    ): InventoryItemDetailView {
        val item = stack.item
        val forcedSaleValue = ClassQuestTagRules.forcedSellValue(item.tags)
        val saleValue = if (forcedSaleValue != null) {
            forcedSaleValue
        } else {
            val baseSaleValue = engine.economyEngine.sellValue(
                itemValue = item.value,
                rarity = item.rarity,
                type = item.type,
                tags = item.tags
            )
            val raceDef = runCatching { engine.classSystem.raceDef(player.raceId) }.getOrNull()
            val raceBonus = RaceBonusSupport.tradeSellBonusPct(raceDef)
            RaceBonusSupport.applyTradeSellBonus(baseSaleValue, raceBonus)
        }
        val lines = mutableListOf<String>()
        lines += "Raridade: ${item.rarity.colorLabel}"
        if (item.qualityRollPct != 100 || item.powerScore > 0) {
            val powerLabel = if (item.powerScore > 0) " | Poder ${item.powerScore}" else ""
            lines += "Qualidade: ${item.qualityRollPct}%$powerLabel"
        }
        if (item.minLevel > 1) lines += "Nivel requerido: ${item.minLevel}"
        if (item.description.isNotBlank()) lines += "Descricao: ${item.description}"
        if (item.affixes.isNotEmpty()) lines += "Afixos: ${item.affixes.joinToString(", ")}"
        lines += "Tipo: ${item.type.name.lowercase()} | Valor por unidade: $saleValue"
        when (item.type) {
            ItemType.CONSUMABLE -> {
                val restores = mutableListOf<String>()
                if (item.effects.hpRestore > 0.0) restores += "HP +${format(item.effects.hpRestore)}"
                if (item.effects.mpRestore > 0.0) restores += "MP +${format(item.effects.mpRestore)}"
                if (item.effects.hpRestorePct > 0.0) restores += "HP +${format(item.effects.hpRestorePct)}%"
                if (item.effects.mpRestorePct > 0.0) restores += "MP +${format(item.effects.mpRestorePct)}%"
                if (item.effects.fullRestore) restores += "Restaura HP/MP total"
                if (item.effects.clearNegativeStatuses) restores += "Remove status negativos"
                if (item.effects.statusImmunitySeconds > 0.0) restores += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
                if (restores.isNotEmpty()) lines += "Efeito: ${restores.joinToString(" | ")}"
            }
            ItemType.MATERIAL -> {
                if (rpg.inventory.InventorySystem.isArrowAmmo(stack.sampleItemId, itemInstances, engine.itemRegistry)) {
                    lines += "Uso: municao para armas de arco; carregue pela aljava."
                }
                val canonical = canonicalItemId(stack.sampleItemId, itemInstances)
                val gatherSources = repo.gatherNodes.values
                    .filter { it.resourceItemId == canonical }
                    .take(4)
                    .map { it.name }
                val uses = repo.craftRecipes.values
                    .filter { recipe -> recipe.ingredients.any { it.itemId == canonical } }
                    .take(6)
                    .map { "${it.name} (${it.discipline.name.lowercase()})" }
                if (gatherSources.isNotEmpty()) lines += "Origem: ${gatherSources.joinToString(", ")}"
                if (uses.isNotEmpty()) lines += "Usado em: ${uses.joinToString(", ")}"
                val ammoBonusLabel = formatItemBonuses(item)
                if (ammoBonusLabel.isNotBlank()) lines += "Bonus: $ammoBonusLabel"
                val ammoEffectLabel = formatItemEffectsSummary(item)
                if (ammoEffectLabel.isNotBlank()) lines += "Efeito: $ammoEffectLabel"
            }
            ItemType.EQUIPMENT -> {
                val slotLabel = item.slot?.name ?: "desconhecido"
                val handLabel = if (item.twoHanded) " (duas maos)" else ""
                lines += "Slot: $slotLabel$handLabel"
                val classTag = ClassQuestTagRules.effectiveClassTag(item.tags)
                lines += "Tag de classe: ${equipRules.classTagDisplayLabel(classTag)}"
                val classLock = ClassQuestTagRules.classLocked(item.tags)
                val pathLock = ClassQuestTagRules.pathLocked(item.tags)
                if (classLock != null || pathLock != null) {
                    lines += "Restricoes: classe=${classLock ?: "-"} | caminho=${pathLock ?: "-"}"
                }
                if (ClassQuestTagRules.isQuestReward(item.tags)) {
                    lines += "Item de set de quest (revenda fixa)."
                }
                val bonusLabel = formatItemBonuses(item)
                if (bonusLabel.isNotBlank()) lines += "Bonus: $bonusLabel"
                val effectLabel = formatItemEffectsSummary(item)
                if (effectLabel.isNotBlank()) lines += "Efeito: $effectLabel"
            }
        }
        val comparison = if (item.type == ItemType.EQUIPMENT) {
            equipRules.previewEquipDelta(player, item, itemInstances)?.let { preview ->
                val equippedLabel = preview.replacedItem?.let(itemDisplayLabel) ?: "Vazio"
                "Comparando com (${equippedSlotLabel(preview.slotKey)}): $equippedLabel | " +
                    equipRules.formatEquipComparison(preview.before, preview.after).ifBlank {
                        "sem alteracoes relevantes"
                    }
            }
        } else null
        return InventoryItemDetailView(
            sampleItemId = stack.sampleItemId,
            quantity = stack.quantity,
            item = item,
            saleValue = saleValue,
            detailLines = lines,
            comparisonSummary = comparison
        )
    }

    fun buildEquippedItemDetail(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        slotKey: String,
        isOffhandBlocked: (String?) -> Boolean
    ): EquippedItemDetailView? {
        val equippedId = player.equipped[slotKey] ?: return null
        if (isOffhandBlocked(equippedId)) return null
        val item = engine.itemResolver.resolve(equippedId, itemInstances) ?: return null
        val lines = mutableListOf(
            "Item: ${itemDisplayLabel(item)}"
        )
        if (item.description.isNotBlank()) lines += "Descricao: ${item.description}"
        val slotLabel = item.slot?.name ?: slotKey
        val handLabel = if (item.twoHanded) " (duas maos)" else ""
        lines += "Slot: $slotLabel$handLabel"
        if (slotKey == EquipSlot.ALJAVA.name) {
            val current = rpg.inventory.InventorySystem.quiverAmmoCount(player, itemInstances, engine.itemRegistry)
            val max = rpg.inventory.InventorySystem.quiverCapacity(player, itemInstances, engine.itemRegistry)
            lines += "Aljava: $current/$max flechas"
        }
        val bonusLabel = formatItemBonuses(item)
        if (bonusLabel.isNotBlank()) lines += "Bonus: $bonusLabel"
        val before = engine.computePlayerStats(player, itemInstances)
        val after = engine.computePlayerStats(equipRules.buildUnequippedPreview(player, slotKey), itemInstances)
        val removal = "Ao desequipar: " +
            "DMG ${formatSignedDouble(after.derived.damagePhysical - before.derived.damagePhysical)} | " +
            "DEF ${formatSignedDouble(after.derived.defPhysical - before.derived.defPhysical)} | " +
            "HP ${formatSignedDouble(after.derived.hpMax - before.derived.hpMax)} | " +
            "SPD ${formatSignedDouble(after.derived.attackSpeed - before.derived.attackSpeed)}"
        return EquippedItemDetailView(
            slotKey = slotKey,
            itemId = equippedId,
            item = item,
            detailLines = lines,
            removalSummary = removal
        )
    }

    fun formatItemBonuses(item: ResolvedItem): String {
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

    fun formatItemEffectsSummary(item: ResolvedItem): String {
        val parts = mutableListOf<String>()
        if (item.effects.statusImmunitySeconds > 0.0) parts += "Imunidade ${format(item.effects.statusImmunitySeconds)}s"
        if (item.effects.runAttributeMultiplierPct != 0.0) parts += "Buff de run ${format(item.effects.runAttributeMultiplierPct)}%"
        for (status in item.effects.applyStatuses) {
            val chanceLabel = if (status.chancePct > 0.0) "${format(status.chancePct)}%" else "100%"
            val durationLabel = if (status.durationSeconds > 0.0) " por ${format(status.durationSeconds)}s" else ""
            parts += "${StatusSystem.displayName(status.type)} $chanceLabel$durationLabel"
        }
        return parts.joinToString(", ")
    }

    private fun canonicalItemId(itemId: String, itemInstances: Map<String, ItemInstance>): String {
        return itemInstances[itemId]?.templateId ?: itemId
    }

    private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
    private fun formatSignedDouble(value: Double): String = if (value >= 0.0) "+${format(value)}" else format(value)
    private fun format(value: Double): String = "%.1f".format(value)
}
