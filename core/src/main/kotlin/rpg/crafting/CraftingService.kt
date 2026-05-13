package rpg.crafting

import kotlin.math.max
import kotlin.random.Random
import rpg.inventory.InventorySystem
import rpg.item.ItemEngine
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.ItemInstance
import rpg.model.PlayerState
import rpg.model.SkillSnapshot
import rpg.model.SkillType
import rpg.skills.SkillSystem
import rpg.progression.PermanentUpgradeService
import rpg.premium.PremiumSupport
import rpg.registry.ItemRegistry

data class CraftExecutionResult(
    val success: Boolean,
    val message: String,
    val player: PlayerState,
    val itemInstances: Map<String, ItemInstance>,
    val recipe: CraftRecipeDef? = null,
    val outputItemId: String? = null,
    val outputQuantity: Int = 0,
    val gainedXp: Double = 0.0,
    val skillType: SkillType? = null,
    val skillSnapshot: SkillSnapshot? = null,
    val rejectedOutputs: Int = 0,
    val successfulCrafts: Int = 0
)

class CraftingService(
    private val recipes: Map<String, CraftRecipeDef>,
    private val itemRegistry: ItemRegistry,
    private val itemEngine: ItemEngine,
    private val skillSystem: SkillSystem,
    private val rng: Random,
    private val permanentUpgradeService: PermanentUpgradeService,
    private val raceProfessionBonusPct: (PlayerState, SkillType) -> Double = { _, _ -> 0.0 }
) {
    private val catalogRevision: Int = buildCatalogRevision()
    private val enabledCatalogByDiscipline: Map<CraftDiscipline, List<CraftRecipeDef>> =
        CraftDiscipline.entries.associateWith { discipline ->
            recipes.values
                .asSequence()
                .filter { it.enabled && it.discipline == discipline }
                .toList()
        }

    fun availableRecipes(playerLevel: Int, discipline: CraftDiscipline? = null): List<CraftRecipeDef> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredPlayerLevel = playerLevel
        return recipes.values
            .asSequence()
            .filter { it.enabled }
            .filter { discipline == null || it.discipline == discipline }
            .sortedWith(recipeComparator())
            .toList()
    }

    fun enabledRecipeCatalog(discipline: CraftDiscipline): List<CraftRecipeDef> {
        return enabledCatalogByDiscipline[discipline].orEmpty()
    }

    fun recipeCatalogRevision(): Int = catalogRevision

    fun availableRecipes(player: PlayerState, discipline: CraftDiscipline? = null): List<CraftRecipeDef> {
        val prepared = skillSystem.ensureProgress(player)
        return recipes.values
            .asSequence()
            .filter { it.enabled }
            .filter { discipline == null || it.discipline == discipline }
            .filter { recipe ->
                val skill = recipeSkill(recipe)
                skillSystem.snapshot(prepared, skill).level >= recipe.minSkillLevel
            }
            .sortedWith(recipeComparator())
            .toList()
    }

    fun craft(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        recipeId: String,
        times: Int = 1
    ): CraftExecutionResult {
        val preparedPlayer = skillSystem.ensureProgress(player)
        val recipe = recipes[recipeId]
            ?: return CraftExecutionResult(false, "Receita nao encontrada.", preparedPlayer, itemInstances)
        if (!recipe.enabled) {
            return CraftExecutionResult(false, "Receita desativada.", preparedPlayer, itemInstances, recipe = recipe)
        }
        val skillType = recipeSkill(recipe)
        val skillSnapshot = skillSystem.snapshot(preparedPlayer, skillType)
        if (skillSnapshot.level < recipe.minSkillLevel) {
            return CraftExecutionResult(
                false,
                "Skill insuficiente para esta receita.",
                preparedPlayer,
                itemInstances,
                recipe = recipe,
                skillType = skillType,
                skillSnapshot = skillSnapshot
            )
        }

        val batch = times.coerceAtLeast(1)
        val inventory = preparedPlayer.inventory.toMutableList()
        val updatedInstances = itemInstances.toMutableMap()
        val generatedOutputIds = mutableListOf<String>()
        var successfulCrafts = 0

        for (attempt in 1..batch) {
            val craftCostReductionPct = (
                permanentUpgradeService.craftingCostReductionPct(preparedPlayer, recipe.discipline) +
                    PremiumSupport.productionCostReductionPct(preparedPlayer)
                ).coerceIn(0.0, 90.0)
            val effectiveIngredients = recipe.ingredients.map { ingredient ->
                val baseRequired = ingredient.quantity.coerceAtLeast(1)
                val discounted = max(
                    1,
                    kotlin.math.ceil(baseRequired * (1.0 - craftCostReductionPct / 100.0)).toInt()
                )
                val reduced = if (
                    discounted > 1 &&
                    rng.nextDouble(0.0, 100.0) <= skillSnapshot.materialReductionChancePct
                ) {
                    discounted - 1
                } else {
                    discounted
                }
                ingredient.itemId to reduced
            }

            val canCraft = effectiveIngredients.all { (itemId, required) ->
                consumeItems(inventory, updatedInstances, itemId, required, commit = false)
            }
            if (!canCraft) {
                break
            }
            val noConsume = rng.nextDouble(0.0, 100.0) <= skillSnapshot.noConsumeChancePct
            if (!noConsume) {
                for ((itemId, required) in effectiveIngredients) {
                    consumeItems(inventory, updatedInstances, itemId, required, commit = true)
                }
            }

            val crit = rng.nextDouble(0.0, 100.0) <= skillSnapshot.criticalCraftChancePct
            val perCraftOutput = recipe.outputQty.coerceAtLeast(1) * if (crit) 2 else 1
            if (!appendRecipeOutput(
                    recipe = recipe,
                    outputAmount = perCraftOutput,
                    playerLevel = preparedPlayer.level,
                    inventory = generatedOutputIds,
                    itemInstances = updatedInstances
                )
            ) {
                return CraftExecutionResult(
                    false,
                    "Item de saida invalido.",
                    preparedPlayer,
                    itemInstances,
                    recipe = recipe,
                    skillType = skillType,
                    skillSnapshot = skillSnapshot
                )
            }
            successfulCrafts++
        }

        if (successfulCrafts <= 0) {
            return CraftExecutionResult(
                false,
                "Ingredientes insuficientes para ${recipe.name}.",
                preparedPlayer,
                itemInstances,
                recipe = recipe,
                skillType = skillType,
                skillSnapshot = skillSnapshot
            )
        }

        val withCapacity = InventorySystem.addItemsWithLimit(
            player = preparedPlayer.copy(inventory = inventory),
            itemInstances = updatedInstances,
            itemRegistry = itemRegistry,
            incomingItemIds = generatedOutputIds
        )
        val rejectedGenerated = withCapacity.rejected.filter { updatedInstances.containsKey(it) }
        for (rejectedId in rejectedGenerated) {
            updatedInstances.remove(rejectedId)
        }

        val acceptedOutputs = withCapacity.accepted.size
        if (acceptedOutputs <= 0) {
            return CraftExecutionResult(
                false,
                "Inventario cheio. Nenhum item produzido foi armazenado.",
                preparedPlayer,
                itemInstances,
                recipe = recipe,
                skillType = skillType,
                skillSnapshot = skillSnapshot,
                rejectedOutputs = withCapacity.rejected.size,
                successfulCrafts = successfulCrafts
            )
        }

        var updatedPlayer = preparedPlayer.copy(
            inventory = withCapacity.inventory,
            quiverInventory = withCapacity.quiverInventory,
            selectedAmmoTemplateId = withCapacity.selectedAmmoTemplateId
        )
        val rarityMultiplier = outputRarityMultiplier(recipe.outputItemId)
        val professionXpMultiplier = permanentUpgradeService.professionXpMultiplier(preparedPlayer, skillType)
        val raceBonusMultiplier = 1.0 + (
            raceProfessionBonusPct(preparedPlayer, skillType).coerceIn(-50.0, 250.0) / 100.0
            )
        val xpResult = skillSystem.gainXp(
            player = updatedPlayer,
            skill = skillType,
            baseXp = recipe.baseXp * successfulCrafts * professionXpMultiplier * raceBonusMultiplier,
            rarityMultiplier = rarityMultiplier,
            difficulty = recipe.difficulty,
            tier = recipe.tier
        )
        updatedPlayer = xpResult.player

        return CraftExecutionResult(
            success = true,
            message = buildString {
                append("Craft concluido: ${recipe.name} x$successfulCrafts.")
                if (withCapacity.rejected.isNotEmpty()) {
                    append(" Inventario cheio: ${withCapacity.rejected.size} item(ns) nao couberam.")
                }
            },
            player = updatedPlayer,
            itemInstances = updatedInstances,
            recipe = recipe,
            outputItemId = recipe.outputItemId,
            outputQuantity = acceptedOutputs,
            gainedXp = xpResult.gainedXp,
            skillType = skillType,
            skillSnapshot = xpResult.snapshot,
            rejectedOutputs = withCapacity.rejected.size,
            successfulCrafts = successfulCrafts
        )
    }

    fun maxCraftable(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        recipeId: String
    ): Int {
        val recipe = recipes[recipeId] ?: return 0
        return maxCraftable(player, itemInstances, recipe)
    }

    fun maxCraftable(
        player: PlayerState,
        itemInstances: Map<String, ItemInstance>,
        recipe: CraftRecipeDef
    ): Int {
        val preparedPlayer = skillSystem.ensureProgress(player)
        if (!recipe.enabled) return 0
        val skillType = recipeSkill(recipe)
        val skillSnapshot = skillSystem.snapshot(preparedPlayer, skillType)
        if (skillSnapshot.level < recipe.minSkillLevel) return 0

        if (recipe.ingredients.isEmpty()) return 20
        var craftable = Int.MAX_VALUE
        for (ingredient in recipe.ingredients) {
            val required = ingredient.quantity.coerceAtLeast(1)
            val owned = preparedPlayer.inventory.count { id ->
                id == ingredient.itemId || itemInstances[id]?.templateId == ingredient.itemId
            }
            craftable = minOf(craftable, owned / required)
            if (craftable <= 0) return 0
        }
        return craftable.coerceAtLeast(0)
    }

    fun recipeSkill(recipe: CraftRecipeDef): SkillType {
        return recipe.skillType ?: when (recipe.discipline) {
            CraftDiscipline.FORGE -> SkillType.BLACKSMITH
            CraftDiscipline.ALCHEMY -> SkillType.ALCHEMIST
            CraftDiscipline.COOKING -> SkillType.COOKING
        }
    }

    private fun recipeComparator(): Comparator<CraftRecipeDef> {
        return compareBy({ recipeCategoryRank(it) }, { recipeSubcategoryRank(it) }, { it.tier }, { it.name.lowercase() })
    }

    private fun recipeCategoryRank(recipe: CraftRecipeDef): Int {
        val entry = itemRegistry.entry(recipe.outputItemId)
        return when (entry?.type) {
            rpg.model.ItemType.EQUIPMENT -> 0
            rpg.model.ItemType.MATERIAL -> 1
            rpg.model.ItemType.CONSUMABLE -> 2
            else -> 3
        }
    }

    private fun recipeSubcategoryRank(recipe: CraftRecipeDef): Int {
        val entry = itemRegistry.entry(recipe.outputItemId) ?: return 999
        return when (entry.type) {
            rpg.model.ItemType.EQUIPMENT -> equipmentSlotRank(entry.slot)
            rpg.model.ItemType.MATERIAL -> materialRank(entry)
            rpg.model.ItemType.CONSUMABLE -> consumableRank(entry)
            else -> 999
        }
    }

    private fun equipmentSlotRank(slot: rpg.model.EquipSlot?): Int {
        return when (slot) {
            rpg.model.EquipSlot.HEAD -> 0
            rpg.model.EquipSlot.CHEST -> 1
            rpg.model.EquipSlot.LEGS -> 2
            rpg.model.EquipSlot.BOOTS -> 3
            rpg.model.EquipSlot.GLOVES -> 4
            rpg.model.EquipSlot.CAPE -> 5
            rpg.model.EquipSlot.WEAPON_MAIN -> 6
            rpg.model.EquipSlot.WEAPON_OFF -> 7
            rpg.model.EquipSlot.ALJAVA -> 8
            rpg.model.EquipSlot.BACKPACK -> 9
            rpg.model.EquipSlot.ACCESSORY -> 10
            null -> 11
        }
    }

    private fun materialRank(entry: rpg.registry.ItemRegistryEntry): Int {
        val tags = entry.tags.map { it.lowercase() } + entry.name.lowercase()
        return when {
            tags.any { it.contains("bar") || it.contains("lingote") } -> 0
            tags.any { it.contains("ore") || it.contains("mineral") } -> 1
            tags.any { it.contains("crystal") || it.contains("shard") || it.contains("gem") } -> 2
            else -> 3
        }
    }

    private fun consumableRank(entry: rpg.registry.ItemRegistryEntry): Int {
        val tags = entry.tags.map { it.lowercase() } + entry.name.lowercase()
        return when {
            tags.any { it.contains("food") || it.contains("potion") || it.contains("ether") } -> 0
            else -> 1
        }
    }

    private fun appendRecipeOutput(
        recipe: CraftRecipeDef,
        outputAmount: Int,
        playerLevel: Int,
        inventory: MutableList<String>,
        itemInstances: MutableMap<String, ItemInstance>
    ): Boolean {
        if (itemRegistry.isTemplate(recipe.outputItemId)) {
            val template = itemRegistry.template(recipe.outputItemId) ?: return false
            repeat(max(1, outputAmount)) {
                val item = itemEngine.generateFromTemplate(
                    template = template,
                    level = playerLevel,
                    rarity = template.rarity
                )
                itemInstances[item.id] = item
                inventory += item.id
            }
            return true
        }
        if (itemRegistry.item(recipe.outputItemId) == null) return false
        repeat(max(1, outputAmount)) {
            inventory += recipe.outputItemId
        }
        return true
    }

    private fun outputRarityMultiplier(outputItemId: String): Double {
        val rarity = itemRegistry.item(outputItemId)?.rarity ?: itemRegistry.template(outputItemId)?.rarity
        return when (rarity?.name) {
            "COMMON" -> 1.0
            "UNCOMMON" -> 1.2
            "RARE" -> 1.45
            "EPIC" -> 1.8
            "LEGENDARY" -> 2.3
            else -> 1.0
        }
    }

    private fun consumeItems(
        inventory: MutableList<String>,
        itemInstances: MutableMap<String, ItemInstance>,
        targetId: String,
        required: Int,
        commit: Boolean
    ): Boolean {
        if (required <= 0) return true
        val matchingIds = inventory.filter { id ->
            id == targetId || itemInstances[id]?.templateId == targetId
        }
        if (matchingIds.size < required) return false
        if (!commit) return true

        val toRemove = matchingIds.take(required)
        for (id in toRemove) {
            val index = inventory.indexOf(id)
            if (index >= 0) {
                inventory.removeAt(index)
            }
            if (itemInstances.containsKey(id)) {
                itemInstances.remove(id)
            }
        }
        return true
    }

    private fun buildCatalogRevision(): Int {
        var signature = 17
        recipes.values
            .asSequence()
            .filter { it.enabled }
            .sortedBy { it.id.lowercase() }
            .forEach { recipe ->
                signature = (signature * 31) + recipe.id.hashCode()
                signature = (signature * 31) + recipe.name.lowercase().hashCode()
                signature = (signature * 31) + recipe.discipline.name.hashCode()
                signature = (signature * 31) + recipe.minSkillLevel
                signature = (signature * 31) + recipe.outputItemId.hashCode()
                signature = (signature * 31) + recipe.outputQty
                signature = (signature * 31) + recipe.ingredients.size
            }
        return signature
    }
}
