package rpg.item

import rpg.registry.ItemRegistry
import rpg.model.ItemEffects
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.EquipSlot
import rpg.model.Bonuses

data class ResolvedItem(
    val id: String,
    val templateId: String? = null,
    val name: String,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val level: Int = 1,
    val type: ItemType,
    val minLevel: Int = 1,
    val qualityRollPct: Int = 100,
    val powerScore: Int = 0,
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val tags: List<String> = emptyList(),
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val value: Int = 0,
    val description: String = "",
    val affixes: List<String> = emptyList()
)

class ItemResolver(private val registry: ItemRegistry) {
    fun resolve(id: String, instances: Map<String, ItemInstance>): ResolvedItem? {
        val instance = instances[id]
        if (instance != null) {
            return ResolvedItem(
                id = instance.id,
                templateId = instance.templateId,
                name = instance.name,
                rarity = instance.rarity,
                level = instance.level,
                type = instance.type,
                minLevel = instance.minLevel,
                qualityRollPct = instance.qualityRollPct,
                powerScore = instance.powerScore,
                slot = instance.slot,
                twoHanded = instance.twoHanded,
                tags = instance.tags,
                bonuses = instance.bonuses,
                effects = instance.effects,
                value = instance.value,
                description = instance.description,
                affixes = instance.affixes
            )
        }
        val def = registry.item(id)
        if (def != null) {
            return ResolvedItem(
                id = def.id,
                templateId = null,
                name = def.name,
                rarity = def.rarity,
                level = def.minLevel,
                type = def.type,
                minLevel = def.minLevel,
                qualityRollPct = 100,
                powerScore = 0,
                slot = def.slot,
                twoHanded = def.twoHanded,
                tags = def.tags,
                bonuses = def.bonuses,
                effects = def.effects,
                value = def.value,
                description = def.description,
                affixes = emptyList()
            )
        }
        val template = registry.template(id) ?: return null
        return ResolvedItem(
            id = template.id,
            templateId = template.id,
            name = template.name,
            rarity = template.rarity,
            level = template.minLevel,
            type = template.type,
            minLevel = template.minLevel,
            qualityRollPct = 100,
            powerScore = 0,
            slot = template.slot,
            twoHanded = template.twoHanded,
            tags = template.tags,
            bonuses = Bonuses(),
            effects = ItemEffects(),
            value = template.vendorBaseValue,
            description = template.description,
            affixes = emptyList()
        )
    }
}
