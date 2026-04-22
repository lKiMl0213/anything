package rpg.classquest

import kotlin.random.Random
import rpg.classsystem.AttributeEngine
import rpg.classsystem.ClassSystem
import rpg.inventory.InventorySystem
import rpg.io.DataRepository
import rpg.item.ItemGenerator
import rpg.item.ItemRarity
import rpg.model.EquipSlot
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.progression.ExperienceEngine
import rpg.registry.ItemRegistry

internal class ClassQuestRewardResolver(
    private val repo: DataRepository,
    private val itemRegistry: ItemRegistry,
    private val classSystem: ClassSystem,
    private val rng: Random
) {
    fun grantStageReward(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        context: ClassQuestContext,
        stage: ClassQuestStageDefinition
    ): ClassQuestUpdate {
        val reward = stage.reward
        var updatedPlayer = player.copy(gold = player.gold + reward.gold)
        updatedPlayer = applyXpWithAutoPoints(updatedPlayer, reward.xp)

        val updatedInstances = itemInstances.toMutableMap()
        val incomingItems = mutableListOf<String>()

        if (itemExists(reward.hpPotionId)) {
            repeat(reward.hpPotionQty.coerceAtLeast(0)) { incomingItems += reward.hpPotionId }
        }
        val manaItemId = if (itemExists(reward.mpPotionId)) reward.mpPotionId else defaultMpPotionId
        if (itemExists(manaItemId)) {
            repeat(reward.mpPotionQty.coerceAtLeast(0)) { incomingItems += manaItemId }
        }

        for (slot in reward.equipmentSlots) {
            val rewardItemId = classQuestRewardItemId(
                definition = context.definition,
                pathId = context.progress.chosenPath ?: continue,
                slot = slot
            ) ?: continue
            val template = itemRegistry.template(rewardItemId)
            if (template != null) {
                val generated = ItemGenerator.generate(
                    template = template,
                    level = updatedPlayer.level.coerceAtLeast(template.minLevel),
                    rarity = classQuestRewardRarity(
                        templateMinRarity = template.rarity,
                        templateMaxRarity = template.maxRarity,
                        unlockType = context.definition.unlockType,
                        stage = stage.stage
                    ),
                    rng = rng,
                    affixPool = repo.affixes
                )
                updatedInstances[generated.id] = generated
                incomingItems += generated.id
            } else if (itemExists(rewardItemId)) {
                incomingItems += rewardItemId
            }
        }

        val insert = InventorySystem.addItemsWithLimit(
            player = updatedPlayer,
            itemInstances = updatedInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = incomingItems
        )

        val rejectedGenerated = insert.rejected.filter { updatedInstances.containsKey(it) }
        for (id in rejectedGenerated) {
            updatedInstances.remove(id)
        }

        val granted = mutableMapOf<String, Int>()
        for (id in insert.accepted) {
            val canonical = updatedInstances[id]?.templateId ?: id
            granted[canonical] = (granted[canonical] ?: 0) + 1
        }

        updatedPlayer = updatedPlayer.copy(
            inventory = insert.inventory,
            quiverInventory = insert.quiverInventory,
            selectedAmmoTemplateId = insert.selectedAmmoTemplateId
        )
        val stageName = "Etapa ${stage.stage}"
        val messages = mutableListOf(
            "$stageName: +${reward.xp} XP, +${reward.gold} ouro e recompensas recebidas."
        )
        if (insert.rejected.isNotEmpty()) {
            messages += "$stageName: inventario cheio, ${insert.rejected.size} item(ns) foram descartados."
        }

        return ClassQuestUpdate(
            player = updatedPlayer,
            itemInstances = updatedInstances.toMap(),
            messages = messages,
            grantedItems = granted
        )
    }

    private fun applyXpWithAutoPoints(player: PlayerState, xp: Int): PlayerState {
        if (xp <= 0) return player
        var updated = ExperienceEngine.applyXp(player, xp)
        val gained = updated.level - player.level
        if (gained <= 0) return updated
        val classDef = classSystem.classDef(updated.classId)
        val raceDef = classSystem.raceDef(updated.raceId)
        val subclassDef = classSystem.subclassDef(updated.subclassId)
        val specializationDef = classSystem.specializationDef(updated.specializationId)
        repeat(gained) {
            updated = AttributeEngine.applyAutoPoints(updated, classDef, raceDef, subclassDef, specializationDef, rng)
        }
        return updated
    }

    private fun classQuestRewardItemId(
        definition: ClassQuestDefinition,
        pathId: String,
        slot: EquipSlot
    ): String? {
        val prefix = ClassQuestCatalogSupport.classQuestRewardPrefix(definition.unlockType, definition.classId, pathId) ?: return null
        return when (slot) {
            EquipSlot.HEAD -> "${prefix}_helm_template"
            EquipSlot.CHEST -> "${prefix}_chest_template"
            EquipSlot.GLOVES -> "${prefix}_gloves_template"
            EquipSlot.LEGS -> "${prefix}_legs_template"
            EquipSlot.BOOTS -> "${prefix}_boots_template"
            EquipSlot.WEAPON_MAIN -> ClassQuestCatalogSupport.classQuestWeaponTemplateId(pathId, definition.classId, definition.unlockType)
                ?: "${prefix}_weapon"
            EquipSlot.ALJAVA -> ClassQuestCatalogSupport.classQuestQuiverTemplateId(pathId)
                ?: "${prefix}_quiver"
            else -> null
        }
    }

    private fun classQuestRewardRarity(
        templateMinRarity: ItemRarity,
        templateMaxRarity: ItemRarity,
        unlockType: ClassQuestUnlockType,
        stage: Int
    ): ItemRarity {
        val target = when (unlockType) {
            ClassQuestUnlockType.SUBCLASS -> if (stage >= 4) ItemRarity.EPIC else ItemRarity.RARE
            ClassQuestUnlockType.SPECIALIZATION -> if (stage >= 4) ItemRarity.LEGENDARY else ItemRarity.EPIC
        }
        return ItemRarity.clamp(target, templateMinRarity, templateMaxRarity)
    }

    private fun itemExists(itemId: String): Boolean {
        return itemRegistry.item(itemId) != null || itemRegistry.template(itemId) != null
    }

    private val defaultMpPotionId: String
        get() = when {
            itemExists("mp_potion_medium") -> "mp_potion_medium"
            itemExists("ether_small") -> "ether_small"
            itemExists("mp_potion_small") -> "mp_potion_small"
            else -> "hp_potion_medium"
        }
}
