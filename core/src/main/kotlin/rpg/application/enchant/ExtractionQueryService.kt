package rpg.application.enchant

import rpg.enchant.ExtractionRequest
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemType

class ExtractionQueryService(
    private val engine: GameEngine
) {
    private val presets = listOf(
        ExtractionRequest(itemId = "", useRemovalScroll = false, useProtectionScroll = false),
        ExtractionRequest(itemId = "", useRemovalScroll = true, useProtectionScroll = false),
        ExtractionRequest(itemId = "", useRemovalScroll = true, useProtectionScroll = true),
        ExtractionRequest(itemId = "", useRemovalScroll = false, useProtectionScroll = true)
    )

    fun inventoryItems(state: GameState): List<ExtractionInventoryItemView> {
        return state.player.inventory.mapNotNull { itemId ->
            val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return@mapNotNull null
            if (resolved.type != ItemType.EQUIPMENT || resolved.enchantLevel <= 0) return@mapNotNull null
            val preview = engine.extractionService.preview(
                player = state.player,
                itemInstances = state.itemInstances,
                request = ExtractionRequest(itemId = itemId)
            )
            ExtractionInventoryItemView(
                itemId = itemId,
                displayLabel = formatItemLabel(resolved.name, resolved.enchantLevel),
                enchantLevel = resolved.enchantLevel,
                available = preview.available,
                blockedReasons = preview.blockedReasons
            )
        }.distinctBy { it.itemId }.sortedWith(
            compareByDescending<ExtractionInventoryItemView> { it.enchantLevel }
                .thenBy { it.displayLabel.lowercase() }
        )
    }

    fun detail(state: GameState, itemId: String): ExtractionDetailView? {
        val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return null
        if (resolved.type != ItemType.EQUIPMENT || resolved.enchantLevel <= 0) return null
        val attempts = presets.map { preset ->
            val preview = engine.extractionService.preview(
                player = state.player,
                itemInstances = state.itemInstances,
                request = preset.copy(itemId = itemId)
            )
            ExtractionAttemptOptionView(
                useRemovalScroll = preview.useRemovalScroll,
                useProtectionScroll = preview.useProtectionScroll,
                displayLabel = buildLabel(preview),
                available = preview.available,
                blockedReasons = preview.blockedReasons
            )
        }
        val basePreview = engine.extractionService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = ExtractionRequest(itemId = itemId)
        )
        val lines = mutableListOf<String>()
        lines += "Item: ${formatItemLabel(resolved.name, resolved.enchantLevel)}"
        lines += "Pedra gerada em sucesso: +${basePreview.stoneEnchantLevel}"
        lines += "Com proteção: item vira +0"
        lines += "Sem proteção: item é destruído"
        if (basePreview.blockedReasons.isNotEmpty()) {
            lines += "Bloqueios base: ${basePreview.blockedReasons.joinToString(" | ")}"
        }
        return ExtractionDetailView(
            itemId = itemId,
            itemLabel = formatItemLabel(resolved.name, resolved.enchantLevel),
            enchantLevel = resolved.enchantLevel,
            attemptOptions = attempts,
            detailLines = lines
        )
    }

    private fun buildLabel(preview: rpg.enchant.ExtractionPreview): String {
        val scroll = buildString {
            append(if (preview.useRemovalScroll) "Remoção" else "Sem remoção")
            append(" | ")
            append(if (preview.useProtectionScroll) "Proteção" else "Sem proteção")
        }
        return "$scroll | Sucesso ${formatPct(preview.successChancePct)} | Custo ${preview.goldCost} ouro | Tempo ${formatSeconds(preview.durationSeconds)}s"
    }

    private fun formatItemLabel(name: String, enchantLevel: Int): String {
        return if (enchantLevel > 0) "$name +$enchantLevel" else name
    }

    private fun formatPct(value: Double): String = "%.1f%%".format(value)
    private fun formatSeconds(value: Double): String = "%.1f".format(value)
}




