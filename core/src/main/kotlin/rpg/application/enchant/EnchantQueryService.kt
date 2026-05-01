package rpg.application.enchant

import rpg.enchant.EnchantAttemptRequest
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemType

class EnchantQueryService(
    private val engine: GameEngine
) {
    private val runePresets = listOf(0, 1, 3, 5, 10)

    fun inventoryItems(state: GameState): List<EnchantInventoryItemView> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in state.player.inventory) {
            val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: continue
            if (resolved.type != ItemType.EQUIPMENT) continue
            val key = if (state.itemInstances.containsKey(itemId)) itemId else resolved.id
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }

        return grouped.values.mapNotNull { ids ->
            val sampleId = ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, state.itemInstances) ?: return@mapNotNull null
            val preview = engine.enchantService.preview(
                player = state.player,
                itemInstances = state.itemInstances,
                request = EnchantAttemptRequest(itemId = sampleId)
            )
            EnchantInventoryItemView(
                itemId = sampleId,
                displayLabel = formatItemLabel(resolved.name, resolved.enchantLevel),
                quantity = ids.size,
                enchantLevel = resolved.enchantLevel,
                maxEnchantLevel = preview.maxEnchantLevel,
                available = preview.available,
                blockedReasons = preview.blockedReasons
            )
        }.sortedWith(
            compareByDescending<EnchantInventoryItemView> { it.enchantLevel }
                .thenBy { it.displayLabel.lowercase() }
        )
    }

    fun itemDetail(state: GameState, itemId: String): EnchantItemDetailView? {
        val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return null
        if (resolved.type != ItemType.EQUIPMENT) return null

        val basePreview = engine.enchantService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = EnchantAttemptRequest(itemId = itemId)
        )
        val enhancementOwned = ownedRuneCount(
            state = state,
            runeItemIds = engine.enchantService.enhancementRuneItemIds()
        )
        val protectionOwned = ownedRuneCount(
            state = state,
            runeItemIds = engine.enchantService.protectionRuneItemIds()
        )
        val options = buildAttemptOptions(
            state = state,
            itemId = itemId,
            includeProtection = protectionOwned > 0
        )

        val detailLines = mutableListOf<String>()
        detailLines += "Nível atual: +${resolved.enchantLevel}/${basePreview.maxEnchantLevel}"
        detailLines += "Tentativa base: ${formatPct(basePreview.successChancePct)} sucesso | ${formatPct(basePreview.breakChancePct)} quebra"
        detailLines += "Custo base: ${basePreview.goldCost} ouro"
        detailLines += "Runas de aprimoramento: $enhancementOwned"
        detailLines += "Runas de proteção: $protectionOwned"
        if (basePreview.blockedReasons.isNotEmpty()) {
            detailLines += "Bloqueios: ${basePreview.blockedReasons.joinToString(" | ")}"
        }

        return EnchantItemDetailView(
            itemId = itemId,
            itemLabel = formatItemLabel(resolved.name, resolved.enchantLevel),
            enchantLevel = resolved.enchantLevel,
            maxEnchantLevel = basePreview.maxEnchantLevel,
            attemptOptions = options,
            detailLines = detailLines
        )
    }

    fun selectedAttemptOption(
        state: GameState,
        itemId: String,
        enhancementRunes: Int,
        useProtectionRune: Boolean
    ): EnchantAttemptOptionView {
        return attemptOption(state, itemId, enhancementRunes, useProtectionRune)
    }

    private fun buildAttemptOptions(
        state: GameState,
        itemId: String,
        includeProtection: Boolean
    ): List<EnchantAttemptOptionView> {
        val maxRunes = engine.enchantService.maxEnhancementRunesPerAttempt()
        val runeOptions = runePresets.map { it.coerceAtMost(maxRunes) }.distinct()
        val options = mutableListOf<EnchantAttemptOptionView>()
        for (runes in runeOptions) {
            options += attemptOption(state, itemId, runes, useProtection = false)
            if (includeProtection) {
                options += attemptOption(state, itemId, runes, useProtection = true)
            }
        }
        return options
    }

    private fun attemptOption(
        state: GameState,
        itemId: String,
        runes: Int,
        useProtection: Boolean
    ): EnchantAttemptOptionView {
        val preview = engine.enchantService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = EnchantAttemptRequest(
                itemId = itemId,
                enhancementRunes = runes,
                useProtectionRune = useProtection
            )
        )
        val protectionLabel = if (useProtection) " + Proteção" else ""
        val label = buildString {
            append("Runas $runes$protectionLabel")
            append(" | Sucesso ${formatPct(preview.successChancePct)}")
            append(" | Quebra ${formatPct(preview.breakChancePct)}")
            append(" | Custo ${preview.goldCost} ouro")
            append(" | Tempo ${formatSeconds(preview.durationSeconds)}s")
        }
        return EnchantAttemptOptionView(
            enhancementRunes = runes,
            useProtectionRune = useProtection,
            displayLabel = label,
            available = preview.available,
            blockedReasons = preview.blockedReasons
        )
    }

    private fun ownedRuneCount(state: GameState, runeItemIds: Set<String>): Int {
        if (runeItemIds.isEmpty()) return 0
        return state.player.inventory.count { id ->
            id in runeItemIds || (state.itemInstances[id]?.templateId in runeItemIds)
        }
    }

    private fun formatItemLabel(name: String, enchantLevel: Int): String {
        return if (enchantLevel > 0) "$name +$enchantLevel" else name
    }

    private fun formatPct(value: Double): String = "%.1f%%".format(value)
    private fun formatSeconds(value: Double): String = "%.1f".format(value)
}
