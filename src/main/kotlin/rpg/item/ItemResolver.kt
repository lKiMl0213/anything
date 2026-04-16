package rpg.item

import rpg.registry.ItemRegistry
import rpg.model.ItemEffects
import rpg.model.ItemInstance
import rpg.model.ItemType
import rpg.model.EquipSlot
import rpg.model.Bonuses

data class ResolvedItem(
    val id: String,
    val name: String,
    val type: ItemType,
    val minLevel: Int = 1,
    val slot: EquipSlot? = null,
    val twoHanded: Boolean = false,
    val tags: List<String> = emptyList(),
    val bonuses: Bonuses = Bonuses(),
    val effects: ItemEffects = ItemEffects(),
    val value: Int = 0,
    val description: String = ""
)

class ItemResolver(private val registry: ItemRegistry) {
    fun resolve(id: String, instances: Map<String, ItemInstance>): ResolvedItem? {
        val instance = instances[id]
        if (instance != null) {
            return ResolvedItem(
                id = instance.id,
                name = instance.name,
                type = instance.type,
                minLevel = instance.minLevel,
                slot = instance.slot,
                twoHanded = instance.twoHanded,
                tags = instance.tags,
                bonuses = instance.bonuses,
                effects = instance.effects,
                value = instance.value,
                description = instance.description
            )
        }
        val def = registry.item(id) ?: return null
        return ResolvedItem(
            id = def.id,
            name = def.name,
            type = def.type,
            minLevel = def.minLevel,
            slot = def.slot,
            twoHanded = def.twoHanded,
            tags = def.tags,
            bonuses = def.bonuses,
            effects = def.effects,
            value = def.value,
            description = def.description
        )
    }
}
