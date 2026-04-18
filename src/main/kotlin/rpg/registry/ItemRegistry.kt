package rpg.registry

import rpg.item.ItemRarity
import rpg.model.Bonuses
import rpg.model.EquipSlot
import rpg.model.ItemDef
import rpg.model.ItemEffects
import rpg.model.ItemTemplateDef
import rpg.model.ItemType

data class ItemRegistryEntry(
    val id: String,
    val name: String,
    val type: ItemType,
    val minLevel: Int,
    val rarity: ItemRarity,
    val maxRarity: ItemRarity = rarity,
    val tags: List<String>,
    val lootTags: List<String> = emptyList(),
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val value: Int = 0,
    val description: String = "",
    val dropWeight: Int = 0,
    val template: ItemTemplateDef? = null
)

class ItemRegistry(
    private val items: Map<String, ItemDef>,
    private val templates: Map<String, ItemTemplateDef>
) {
    fun entry(id: String): ItemRegistryEntry? {
        val item = items[id]
        if (item != null) {
            return ItemRegistryEntry(
                id = item.id,
                name = item.name,
                type = item.type,
                minLevel = item.minLevel,
                rarity = item.rarity,
                maxRarity = item.rarity,
                tags = item.tags,
                lootTags = emptyList(),
                slot = item.slot,
                twoHanded = item.twoHanded,
                bonuses = item.bonuses,
                effects = item.effects,
                value = item.value,
                description = item.description,
                dropWeight = 0
            )
        }
        val template = templates[id] ?: return null
        return ItemRegistryEntry(
            id = template.id,
            name = template.name,
            type = template.type,
            minLevel = template.minLevel,
            rarity = template.rarity,
            maxRarity = template.maxRarity,
            tags = template.tags,
            lootTags = template.lootTags,
            slot = template.slot,
            twoHanded = template.twoHanded,
            description = template.description,
            dropWeight = template.dropWeight,
            template = template
        )
    }

    fun item(id: String): ItemDef? = items[id]

    fun template(id: String): ItemTemplateDef? = templates[id]

    fun isTemplate(id: String): Boolean = templates.containsKey(id)

    fun allItems(): Collection<ItemDef> = items.values

    fun allTemplates(): Collection<ItemTemplateDef> = templates.values
}
