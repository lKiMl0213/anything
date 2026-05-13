package rpg.application.enchant

import rpg.enchant.FusionPreview
import rpg.enchant.FusionRequest
import rpg.engine.GameEngine
import rpg.model.GameState
import rpg.model.ItemType

class FusionQueryService(
    private val engine: GameEngine
) {
    fun slot1Candidates(state: GameState): List<FusionInventoryItemView> {
        return candidateGroups(state).mapNotNull { group ->
            val sampleId = group.ids.firstOrNull() ?: return@mapNotNull null
            val resolved = engine.itemResolver.resolve(sampleId, state.itemInstances) ?: return@mapNotNull null
            if (!isFusionCandidate(sampleId, state)) return@mapNotNull null
            val qtyLabel = if (group.ids.size > 1) " x${group.ids.size}" else ""
            FusionInventoryItemView(
                itemId = sampleId,
                displayLabel = "${formatLabel(resolved.name, resolved.enchantLevel)}$qtyLabel",
                enchantLevel = resolved.enchantLevel,
                modeLabel = candidateTypeLabel(sampleId, state)
            )
        }.sortedWith(
            compareByDescending<FusionInventoryItemView> { it.enchantLevel }.thenBy { it.displayLabel.lowercase() }
        )
    }

    fun slot2Candidates(state: GameState, slot1ItemId: String): List<FusionInventoryItemView> {
        return slot1Candidates(state).filter { view ->
            val preview = engine.fusionService.preview(
                player = state.player,
                itemInstances = state.itemInstances,
                request = FusionRequest(slot1ItemId, view.itemId)
            )
            preview.mode != null
        }
    }

    fun preview(state: GameState, slot1ItemId: String, slot2ItemId: String): FusionPreviewView {
        val preview = engine.fusionService.preview(
            player = state.player,
            itemInstances = state.itemInstances,
            request = FusionRequest(slot1ItemId, slot2ItemId)
        )
        return FusionPreviewView(
            slot1ItemId = slot1ItemId,
            slot2ItemId = slot2ItemId,
            slot1Label = preview.slot1Label,
            slot2Label = preview.slot2Label,
            detailLines = buildPreviewLines(preview),
            available = preview.available,
            blockedReasons = preview.blockedReasons
        )
    }

    private fun buildPreviewLines(preview: FusionPreview): List<String> {
        val lines = mutableListOf<String>()
        lines += "Modo: ${modeLabel(preview.mode)}"
        lines += "Base de encantamento: +${preview.baseEnchantLevel}"
        lines += "Sucesso: +${preview.successNormalEnchantLevel} ou +${preview.successUpgradeEnchantLevel}"
        lines += "Falha: pedra entre +${preview.failureMinEnchantLevel} e +${preview.failureMaxEnchantLevel}"
        lines += "Chance de sucesso: ${formatPct(preview.successChancePct)}"
        lines += "Custo: ${preview.goldCost} ouro"
        lines += "Tempo: ${formatSeconds(preview.durationSeconds)}s"
        if (preview.blockedReasons.isNotEmpty()) {
            lines += "Bloqueios: ${preview.blockedReasons.joinToString(" | ")}"
        }
        return lines
    }

    private fun candidateGroups(state: GameState): List<CandidateGroup> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (itemId in state.player.inventory) {
            val key = rpg.inventory.InventorySystem.stackKey(itemId, state.itemInstances, engine.itemRegistry)
            grouped.getOrPut(key) { mutableListOf() }.add(itemId)
        }
        return grouped.values.map { ids -> CandidateGroup(ids = ids.toList()) }
    }

    private fun isFusionCandidate(itemId: String, state: GameState): Boolean {
        val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return false
        if (resolved.type == ItemType.EQUIPMENT) return true
        val hasStoneTag = resolved.tags.any { it.trim().equals("enchant_stone", ignoreCase = true) }
        return resolved.type == ItemType.MATERIAL && hasStoneTag
    }

    private fun candidateTypeLabel(itemId: String, state: GameState): String {
        val resolved = engine.itemResolver.resolve(itemId, state.itemInstances) ?: return "desconhecido"
        if (resolved.type == ItemType.EQUIPMENT) return "equipamento"
        return "pedra"
    }

    private fun modeLabel(mode: rpg.enchant.FusionMode?): String = when (mode) {
        rpg.enchant.FusionMode.EQUIPMENT_EQUIPMENT -> "Equipamento + Equipamento"
        rpg.enchant.FusionMode.STONE_STONE -> "Pedra + Pedra"
        rpg.enchant.FusionMode.STONE_EQUIPMENT -> "Pedra + Equipamento"
        null -> "Inválido"
    }

    private fun formatLabel(name: String, enchantLevel: Int): String {
        return if (enchantLevel > 0) "$name +$enchantLevel" else name
    }

    private fun formatPct(value: Double): String = "%.1f%%".format(value)
    private fun formatSeconds(value: Double): String = "%.1f".format(value)

    private data class CandidateGroup(
        val ids: List<String>
    )
}

