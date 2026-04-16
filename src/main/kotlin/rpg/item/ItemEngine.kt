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
        val template = templates.random(rng)
        val rarity = forcedRarity ?: ItemRarity.roll(rng)
        return ItemGenerator.generate(template, level, rarity, rng, affixes)
    }

    fun availableTemplates(dropTier: Int): List<ItemTemplateDef> {
        val candidates = registry.allTemplates().filter { it.dropTier <= dropTier }
        return if (candidates.isNotEmpty()) candidates else registry.allTemplates().toList()
    }

    fun generateFromTemplate(template: ItemTemplateDef, level: Int, rarity: ItemRarity): ItemInstance {
        return ItemGenerator.generate(template, level, rarity, rng, affixes)
    }
}
