package rpg.validation

import rpg.io.DataRepository
import rpg.registry.ItemRegistry

class DataIntegrityValidator {
    fun validateOrThrow(repo: DataRepository, itemRegistry: ItemRegistry) {
        val errors = validate(repo, itemRegistry)
        if (errors.isEmpty()) return
        val preview = errors.take(20).joinToString("\n") { "- $it" }
        val suffix = if (errors.size > 20) "\n... e mais ${errors.size - 20} erro(s)." else ""
        error("Falha na integridade dos dados:\n$preview$suffix")
    }

    fun validate(repo: DataRepository, itemRegistry: ItemRegistry): List<String> {
        val errors = mutableListOf<String>()
        fun exists(itemId: String): Boolean = itemRegistry.entry(itemId) != null

        for (recipe in repo.craftRecipes.values) {
            if (!exists(recipe.outputItemId)) {
                errors += "Recipe ${recipe.id}: outputItemId inválido (${recipe.outputItemId})."
            }
            for (ingredient in recipe.ingredients) {
                if (!exists(ingredient.itemId)) {
                    errors += "Recipe ${recipe.id}: ingrediente inválido (${ingredient.itemId})."
                }
            }
        }

        for (node in repo.gatherNodes.values) {
            if (!exists(node.resourceItemId)) {
                errors += "Gather node ${node.id}: resourceItemId inválido (${node.resourceItemId})."
            }
        }

        for (spot in repo.huntingSpots.values) {
            if (spot.minCycleSeconds <= 0) {
                errors += "Hunting spot ${spot.id}: minCycleSeconds deve ser >= 1."
            }
            if (spot.drops.isEmpty()) {
                errors += "Hunting spot ${spot.id}: sem drops configurados."
            }
            for (drop in spot.drops) {
                if (!exists(drop.itemId)) {
                    errors += "Hunting spot ${spot.id}: drop inválido (${drop.itemId})."
                }
                if (drop.minQty <= 0 || drop.maxQty < drop.minQty) {
                    errors += "Hunting spot ${spot.id}: faixa de quantidade invalida para ${drop.itemId}."
                }
                if (drop.weight <= 0) {
                    errors += "Hunting spot ${spot.id}: peso deve ser > 0 para ${drop.itemId}."
                }
            }
        }

        val enchantIds = linkedSetOf<String>()
        enchantIds += repo.enchantConfig.enhancementRuneItemIds()
        enchantIds += repo.enchantConfig.protectionRuneItemIds()
        enchantIds += repo.extractionConfig.enchantStoneTemplateIds()
        enchantIds += repo.fusionConfig.enchantStoneTemplateIds()
        enchantIds += repo.extractionConfig.removalScrollItemIds()
        enchantIds += repo.extractionConfig.protectionScrollItemIds()
        for (id in enchantIds) {
            if (!exists(id)) {
                errors += "Encantamento/Extração: item referenciado inexistente ($id)."
            }
        }

        for (level in 1..repo.enchantConfig.maxEnchantLevel.coerceAtLeast(1)) {
            val extractionStoneId = repo.extractionConfig.enchantStoneTemplateIdForLevel(level)
            if (!exists(extractionStoneId)) {
                errors += "Extração: pedra para nível +$level inexistente ($extractionStoneId)."
            }
            val fusionStoneId = repo.fusionConfig.enchantStoneTemplateIdForLevel(level)
            if (!exists(fusionStoneId)) {
                errors += "Fusão: pedra para nível +$level inexistente ($fusionStoneId)."
            }
        }

        for (buff in repo.cookingBuffConfig.buffs) {
            if (!exists(buff.itemId)) {
                errors += "Cooking buff ${buff.id}: itemId inválido (${buff.itemId})."
            }
            if (buff.baseValue < 0.0) {
                errors += "Cooking buff ${buff.id}: baseValue deve ser >= 0."
            }
            if (buff.baseDurationMinutes <= 0.0) {
                errors += "Cooking buff ${buff.id}: baseDurationMinutes deve ser > 0."
            }
        }

        return errors
    }
}



