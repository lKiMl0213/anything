package rpg.item

import kotlin.random.Random
import rpg.model.AffixDef
import rpg.model.ItemInstance
import rpg.model.ItemTemplateDef
import rpg.registry.ItemRegistry

class ItemEngine(
    private val registry: ItemRegistry,
    private val affixes: Map<String, AffixDef>,
    private val rng: Random
) {
    fun generateItem(level: Int, dropTier: Int, forcedRarity: ItemRarity? = null): ItemInstance {
        val templates = availableTemplates(dropTier)
        val template = pickWeightedTemplate(templates)
        val rolledRarity = forcedRarity ?: ItemRarity.roll(rng)
        val rarity = ItemRarity.clamp(rolledRarity, template.rarity, template.maxRarity)
        return ItemGenerator.generate(template, level, rarity, rng, affixes)
    }

    fun availableTemplates(dropTier: Int): List<ItemTemplateDef> {
        val candidates = registry.allTemplates().filter { it.dropTier <= dropTier }
        return if (candidates.isNotEmpty()) candidates else registry.allTemplates().toList()
    }

    fun generateFromTemplate(template: ItemTemplateDef, level: Int, rarity: ItemRarity): ItemInstance {
        val clampedRarity = ItemRarity.clamp(rarity, template.rarity, template.maxRarity)
        return ItemGenerator.generate(template, level, clampedRarity, rng, affixes)
    }

    private fun pickWeightedTemplate(templates: List<ItemTemplateDef>): ItemTemplateDef {
        if (templates.size <= 1) return templates.first()
        val total = templates.sumOf { it.dropWeight.coerceAtLeast(1) }
        var roll = rng.nextInt(total)
        for (template in templates) {
            roll -= template.dropWeight.coerceAtLeast(1)
            if (roll < 0) return template
        }
        return templates.first()
    }
}
