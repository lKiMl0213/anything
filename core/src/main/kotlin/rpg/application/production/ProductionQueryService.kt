package rpg.application.production

import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState
import rpg.model.SkillType

class ProductionQueryService(
    private val engine: GameEngine,
    private val durationService: ProductionActionDurationService = ProductionActionDurationService(engine)
) {
    private val supportedDisciplines = listOf(
        CraftDiscipline.FORGE,
        CraftDiscipline.ALCHEMY,
        CraftDiscipline.COOKING
    )

    private val recipeOrderCache = mutableMapOf<CraftDiscipline, RecipeOrderCacheEntry>()
    private val recipeListCache = mutableMapOf<CraftDiscipline, RecipeListCacheEntry>()

    fun warmCraftRecipeCaches(state: GameState) {
        @Suppress("UNUSED_PARAMETER")
        val ignoredState = state
        supportedDisciplines.forEach { discipline ->
            recipeOrder(discipline)
        }
    }

    fun recipes(state: GameState, discipline: CraftDiscipline): List<ProductionRecipeView> {
        val orderCache = recipeOrder(discipline)
        val player = state.player
        val counts = buildInventoryCountSnapshot(player.inventory, state.itemInstances)
        val skillLevelCache = mutableMapOf<SkillType, Int>()
        val cacheKey = RecipeListCacheKey(
            catalogRevision = orderCache.catalogRevision,
            inventorySignature = counts.signature,
            skillSignature = skillSignature(player, orderCache.entries, skillLevelCache)
        )
        val cached = recipeListCache[discipline]
        if (cached != null && cached.key == cacheKey) {
            return cached.views
        }

        val views = orderCache.entries.map { entry ->
            buildRecipeView(
                state = state,
                recipe = entry.recipe,
                discipline = discipline,
                requestedBatchSize = null,
                includeIngredients = false,
                includeTiming = false,
                countSnapshot = counts,
                skillLevelCache = skillLevelCache,
                skillTypeOverride = entry.skillType
            )
        }
        recipeListCache[discipline] = RecipeListCacheEntry(cacheKey, views)
        return views
    }

    fun recipe(
        state: GameState,
        discipline: CraftDiscipline,
        recipeId: String,
        requestedBatchSize: Int?
    ): ProductionRecipeView? {
        val player = state.player
        val orderCache = recipeOrder(discipline)
        val recipeEntry = orderCache.byId[recipeId]
            ?: return null
        val counts = buildInventoryCountSnapshot(player.inventory, state.itemInstances)
        val craftBatchLimit = engine.permanentUpgradeService.craftBatchLimit(player).coerceAtLeast(1)
        val skillLevelCache = mutableMapOf(
            recipeEntry.skillType to engine.skillSystem.snapshot(player, recipeEntry.skillType).level
        )
        return buildRecipeView(
            state = state,
            recipe = recipeEntry.recipe,
            discipline = discipline,
            requestedBatchSize = requestedBatchSize,
            includeIngredients = true,
            countSnapshot = counts,
            craftBatchLimit = craftBatchLimit,
            skillLevelCache = skillLevelCache,
            skillTypeOverride = recipeEntry.skillType
        )
    }

    fun gatherNodes(state: GameState, type: GatheringType): List<ProductionGatherNodeView> {
        val player = state.player
        val nodes = engine.gatheringService.availableNodes(player.level, type)
        return nodes.map { node ->
            val skill = engine.gatheringService.nodeSkill(node)
            val snapshot = engine.skillSystem.snapshot(player, skill)
            val unlocked = snapshot.level >= node.minSkillLevel
            val duration = durationService.resolveGather(state, type, node.id)?.durationSeconds
                ?: engine.skillSystem.actionDurationSeconds(
                    baseSeconds = node.baseDurationSeconds,
                    skillLevel = snapshot.level
                )
            ProductionGatherNodeView(
                id = node.id,
                name = node.name,
                type = node.type,
                resourceLabel = itemName(node.resourceItemId),
                skillType = skill,
                skillLevel = snapshot.level,
                minSkillLevel = node.minSkillLevel,
                unlocked = unlocked,
                unlockReason = if (unlocked) null else unlockMessage(node.minSkillLevel, skill),
                durationSeconds = duration,
                available = unlocked
            )
        }
    }

    private fun itemName(itemId: String): String {
        return engine.itemRegistry.entry(itemId)?.name ?: itemId
    }

    private fun buildInventoryCountSnapshot(
        playerInventory: List<String>,
        itemInstances: Map<String, rpg.model.ItemInstance>
    ): InventoryCountSnapshot {
        val itemCounts = mutableMapOf<String, Int>()
        val templateCounts = mutableMapOf<String, Int>()
        var signature = 17
        playerInventory.forEach { id ->
            itemCounts[id] = itemCounts.getOrDefault(id, 0) + 1
            val templateId = itemInstances[id]?.templateId
            signature = (signature * 31) + id.hashCode()
            signature = (signature * 31) + (templateId?.hashCode() ?: 0)
            templateId?.let {
                templateCounts[templateId] = templateCounts.getOrDefault(templateId, 0) + 1
            }
        }
        return InventoryCountSnapshot(itemCounts, templateCounts, signature)
    }

    private fun ownedItemQuantity(itemId: String, snapshot: InventoryCountSnapshot): Int {
        return (snapshot.itemCounts[itemId] ?: 0) + (snapshot.templateCounts[itemId] ?: 0)
    }

    private fun ingredientLines(
        recipe: rpg.model.CraftRecipeDef,
        snapshot: InventoryCountSnapshot
    ): List<String> {
        return recipe.ingredients.map { ingredient ->
            val needed = ingredient.quantity.coerceAtLeast(1)
            val owned = ownedItemQuantity(ingredient.itemId, snapshot)
            val ingredientName = itemName(ingredient.itemId)
            "$ingredientName $needed ($owned)"
        }
    }

    private fun maxCraftable(
        recipe: rpg.model.CraftRecipeDef,
        snapshot: InventoryCountSnapshot
    ): Int {
        if (recipe.ingredients.isEmpty()) return 20
        var craftable = Int.MAX_VALUE
        for (ingredient in recipe.ingredients) {
            val required = ingredient.quantity.coerceAtLeast(1)
            val owned = ownedItemQuantity(ingredient.itemId, snapshot)
            craftable = minOf(craftable, owned / required)
            if (craftable <= 0) return 0
        }
        return craftable.coerceAtLeast(0)
    }

    private fun buildRecipeView(
        state: GameState,
        recipe: rpg.model.CraftRecipeDef,
        discipline: CraftDiscipline,
        requestedBatchSize: Int?,
        includeIngredients: Boolean = true,
        includeTiming: Boolean = true,
        countSnapshot: InventoryCountSnapshot? = null,
        craftBatchLimit: Int? = null,
        skillLevelCache: MutableMap<SkillType, Int>? = null,
        skillTypeOverride: SkillType? = null
    ): ProductionRecipeView {
        val player = state.player
        val counts = countSnapshot ?: buildInventoryCountSnapshot(player.inventory, state.itemInstances)
        val effectiveCraftBatchLimit = craftBatchLimit ?: engine.permanentUpgradeService
            .craftBatchLimit(player)
            .coerceAtLeast(1)
        val skill = skillTypeOverride ?: engine.craftingService.recipeSkill(recipe)
        val skillLevel = skillLevelCache?.getOrPut(skill) {
            engine.skillSystem.snapshot(player, skill).level
        } ?: engine.skillSystem.snapshot(player, skill).level
        val ingredientLineList = if (includeIngredients) ingredientLines(recipe, counts) else emptyList()
        val maxCraftable = maxCraftable(recipe, counts)
        val unlocked = skillLevel >= recipe.minSkillLevel
        val blockedReasons = mutableListOf<String>()
        if (!unlocked) {
            blockedReasons += "skill ${skill.name.lowercase()} $skillLevel/${recipe.minSkillLevel}"
        }
        if (maxCraftable <= 0) {
            blockedReasons += "ingredientes insuficientes"
        }
        val maxSelectableBatch: Int
        val batchSize: Int
        val batchSeconds: Double
        val perActionSeconds: Double
        if (includeTiming) {
            maxSelectableBatch = if (maxCraftable > 0) {
                minOf(maxCraftable, effectiveCraftBatchLimit)
            } else {
                1
            }
            val durationResolution = durationService.resolveCraftFromRecipe(
                state = state,
                discipline = discipline,
                recipe = recipe,
                maxCraftable = maxCraftable,
                requestedTimes = requestedBatchSize,
                skillLevelOverride = skillLevel
            )
            batchSize = durationResolution?.times?.coerceAtLeast(1)
                ?: requestedBatchSize?.coerceAtLeast(1)?.coerceAtMost(maxSelectableBatch)
                ?: maxSelectableBatch
            batchSeconds = durationResolution?.durationSeconds
                ?: engine.skillSystem.actionDurationSeconds(
                    baseSeconds = recipe.baseDurationSeconds.coerceAtLeast(1.0) * batchSize.toDouble(),
                    skillLevel = skillLevel
                )
            perActionSeconds = (batchSeconds / batchSize.toDouble()).coerceAtLeast(0.5)
        } else {
            maxSelectableBatch = 1
            batchSize = 1
            batchSeconds = 0.0
            perActionSeconds = 0.0
        }
        return ProductionRecipeView(
            id = recipe.id,
            name = recipe.name,
            outputLabel = "${itemName(recipe.outputItemId)} x${recipe.outputQty}",
            discipline = recipe.discipline,
            unlocked = unlocked,
            unlockReason = if (unlocked) null else unlockMessage(recipe.minSkillLevel, skill),
            available = unlocked && maxCraftable > 0,
            maxCraftable = maxCraftable,
            maxSelectableBatch = maxSelectableBatch,
            batchSize = batchSize,
            estimatedPerActionSeconds = perActionSeconds,
            estimatedBatchSeconds = batchSeconds,
            blockedReasons = blockedReasons,
            ingredientLines = ingredientLineList
        )
    }

    private fun skillSignature(
        player: PlayerState,
        entries: List<OrderedRecipeEntry>,
        skillLevelCache: MutableMap<SkillType, Int>
    ): Int {
        var signature = 17
        entries.asSequence()
            .map { it.skillType }
            .distinct()
            .sortedBy { it.name }
            .forEach { skill ->
                val level = skillLevelCache.getOrPut(skill) { engine.skillSystem.snapshot(player, skill).level }
                signature = (signature * 31) + skill.name.hashCode()
                signature = (signature * 31) + level
            }
        return signature
    }

    private fun recipeOrder(discipline: CraftDiscipline): RecipeOrderCacheEntry {
        val revision = engine.craftingService.recipeCatalogRevision()
        val cached = recipeOrderCache[discipline]
        if (cached != null && cached.catalogRevision == revision) {
            return cached
        }
        val entries = engine.craftingService.enabledRecipeCatalog(discipline)
            .asSequence()
            .sortedWith(compareBy<CraftRecipeDef>({ it.minSkillLevel }, { it.name.lowercase() }, { it.id.lowercase() }))
            .map { recipe -> OrderedRecipeEntry(recipe = recipe, skillType = engine.craftingService.recipeSkill(recipe)) }
            .toList()
        val rebuilt = RecipeOrderCacheEntry(
            catalogRevision = revision,
            entries = entries,
            byId = entries.associateBy { it.recipe.id }
        )
        recipeOrderCache[discipline] = rebuilt
        recipeListCache.remove(discipline)
        return rebuilt
    }

    private fun unlockMessage(minSkillLevel: Int, skill: rpg.model.SkillType): String {
        return "Desbloqueado no nv $minSkillLevel de ${skillLabel(skill)}"
    }

    private fun skillLabel(skill: rpg.model.SkillType): String = when (skill) {
        rpg.model.SkillType.MINING -> "mineracao"
        rpg.model.SkillType.GATHERING -> "coleta"
        rpg.model.SkillType.WOODCUTTING -> "corte de madeira"
        rpg.model.SkillType.FISHING -> "pesca"
        rpg.model.SkillType.HUNTING -> "caca"
        rpg.model.SkillType.BLACKSMITH -> "forja"
        rpg.model.SkillType.ALCHEMIST -> "alquimia"
        rpg.model.SkillType.COOKING -> "culinaria"
        rpg.model.SkillType.ENCHANTING -> "encantamento"
    }
}
