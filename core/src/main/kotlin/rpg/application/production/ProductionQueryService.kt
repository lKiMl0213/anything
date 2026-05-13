package rpg.application.production

import rpg.engine.GameEngine
import rpg.model.CraftDiscipline
import rpg.model.CraftRecipeDef
import rpg.model.GameState
import rpg.model.GatheringType
import rpg.model.PlayerState
import rpg.model.SkillType
import rpg.premium.PremiumSupport

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
    private val gatherOrderCache = mutableMapOf<GatheringType, GatherNodeOrderCacheEntry>()
    private val gatherListCache = mutableMapOf<GatheringType, GatherNodeListCacheEntry>()

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
        val orderCache = gatherOrder(type)
        val player = state.player
        val skillLevelCache = mutableMapOf<SkillType, Int>()
        val cacheKey = GatherNodeListCacheKey(
            catalogRevision = orderCache.catalogRevision,
            skillSignature = gatherSkillSignature(player, orderCache.entries, skillLevelCache),
            durationSignature = gatherDurationSignature(player, type)
        )
        val cached = gatherListCache[type]
        if (cached != null && cached.key == cacheKey) {
            return cached.views
        }

        val views = orderCache.entries.map { entry ->
            val node = entry.node
            val skill = entry.skillType
            val skillLevel = skillLevelCache.getOrPut(skill) {
                engine.skillSystem.snapshot(player, skill).level
            }
            val unlocked = skillLevel >= node.minSkillLevel
            val duration = durationService.resolveGatherFromNode(
                state = state,
                type = type,
                node = node,
                skillLevelOverride = skillLevel
            ).durationSeconds
            ProductionGatherNodeView(
                id = node.id,
                name = node.name,
                type = node.type,
                resourceLabel = itemName(node.resourceItemId),
                skillType = skill,
                skillLevel = skillLevel,
                minSkillLevel = node.minSkillLevel,
                unlocked = unlocked,
                unlockReason = if (unlocked) null else unlockMessage(node.minSkillLevel, skill),
                durationSeconds = duration,
                available = unlocked
            )
        }
        gatherListCache[type] = GatherNodeListCacheEntry(cacheKey, views)
        return views
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

    private fun gatherOrder(type: GatheringType): GatherNodeOrderCacheEntry {
        val revision = engine.gatheringService.nodeCatalogRevision()
        val cached = gatherOrderCache[type]
        if (cached != null && cached.catalogRevision == revision) {
            return cached
        }
        val entries = engine.gatheringService.enabledNodeCatalog(type)
            .asSequence()
            .sortedWith(compareBy({ it.minSkillLevel }, { it.name.lowercase() }, { it.id.lowercase() }))
            .map { node -> OrderedGatherNodeEntry(node = node, skillType = engine.gatheringService.nodeSkill(node)) }
            .toList()
        val rebuilt = GatherNodeOrderCacheEntry(
            catalogRevision = revision,
            entries = entries,
            byId = entries.associateBy { it.node.id }
        )
        gatherOrderCache[type] = rebuilt
        gatherListCache.remove(type)
        return rebuilt
    }

    private fun gatherSkillSignature(
        player: PlayerState,
        entries: List<OrderedGatherNodeEntry>,
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

    private fun gatherDurationSignature(player: PlayerState, type: GatheringType): Int {
        val taskId = when (type) {
            GatheringType.HERBALISM -> "herbalism"
            GatheringType.MINING -> "mining"
            GatheringType.WOODCUTTING -> "woodcutting"
            GatheringType.FISHING -> "fishing"
        }
        val activeTask = player.foodBuffTaskId?.trim()?.lowercase()
        val buffActiveForTask = player.foodBuffRemainingMinutes > 0.0 && activeTask == taskId
        var signature = 17
        signature = (signature * 31) + if (PremiumSupport.isPremiumActive(player)) 1 else 0
        signature = (signature * 31) + if (buffActiveForTask) 1 else 0
        if (buffActiveForTask) {
            signature = (signature * 31) + (player.foodBuffTaskEfficiencyPct * 100.0).toInt()
            signature = (signature * 31) + player.foodBuffRemainingMinutes.toInt()
        }
        return signature
    }

    private fun unlockMessage(minSkillLevel: Int, skill: rpg.model.SkillType): String {
        return "Desbloqueado no nv $minSkillLevel de ${skillLabel(skill)}"
    }

    private fun skillLabel(skill: rpg.model.SkillType): String = when (skill) {
        rpg.model.SkillType.MINING -> "mineração"
        rpg.model.SkillType.GATHERING -> "coleta"
        rpg.model.SkillType.WOODCUTTING -> "corte de madeira"
        rpg.model.SkillType.FISHING -> "pesca"
        rpg.model.SkillType.HUNTING -> "caça"
        rpg.model.SkillType.BLACKSMITH -> "forja"
        rpg.model.SkillType.ALCHEMIST -> "alquimia"
        rpg.model.SkillType.COOKING -> "culinária"
        rpg.model.SkillType.ENCHANTING -> "encantamento"
    }
}

