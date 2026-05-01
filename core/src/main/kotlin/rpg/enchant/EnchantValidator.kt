package rpg.enchant

import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.PlayerState

class EnchantValidator(
    private val config: EnchantConfig
) {
    fun validate(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        item: ItemInstance?,
        request: EnchantAttemptRequest,
        goldCost: Int
    ): List<String> {
        val reasons = mutableListOf<String>()
        val normalizedRunes = request.enhancementRunes.coerceAtLeast(0)
        if (item == null) {
            reasons += "Item alvo nao encontrado no inventario."
            return reasons
        }

        if (item.type != ItemType.EQUIPMENT) {
            reasons += "Apenas equipamentos podem ser encantados."
        }
        if (item.enchantLevel >= config.maxEnchantLevel) {
            reasons += "Item ja esta no limite de +${config.maxEnchantLevel}."
        }
        if (item.level < config.minimumEnchantableItemLevel) {
            reasons += "Item precisa de nivel ${config.minimumEnchantableItemLevel}+ para encantamento."
        }
        if (!player.inventory.contains(item.id)) {
            reasons += "O item precisa estar no inventario."
        }
        if (normalizedRunes > config.maxEnhancementRunesPerAttempt) {
            reasons += "Maximo de ${config.maxEnhancementRunesPerAttempt} runas de aprimoramento por tentativa."
        }
        if (goldCost > player.gold) {
            reasons += "Ouro insuficiente (custo: $goldCost)."
        }

        val enhancementOwned = countOwned(player.inventory, itemInstances, config.enhancementRuneItemIds())
        if (normalizedRunes > enhancementOwned) {
            reasons += "Runas de aprimoramento insuficientes ($enhancementOwned disponiveis)."
        }
        if (request.useProtectionRune) {
            val protectionOwned = countOwned(player.inventory, itemInstances, config.protectionRuneItemIds())
            if (protectionOwned <= 0) {
                reasons += "Runa de protecao indisponivel."
            }
        }
        return reasons
    }

    private fun countOwned(
        inventory: List<String>,
        itemInstances: Map<String, ItemInstance>,
        targetIds: Set<String>
    ): Int {
        if (targetIds.isEmpty()) return 0
        return inventory.count { id ->
            id in targetIds || (itemInstances[id]?.templateId in targetIds)
        }
    }
}
