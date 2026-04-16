package rpg.quest

import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.CraftRecipeDef
import rpg.model.GatherNodeDef
import rpg.model.ItemType
import rpg.model.PlayerState
import rpg.model.QuestObjectiveType
import rpg.model.QuestTargetType
import rpg.model.QuestTemplateDef
import rpg.model.QuestTier

data class QuestGenerationContext(
    val player: PlayerState,
    val unlockedTierIds: Set<String>,
    val accessibleMonsterIds: Set<String>,
    val accessibleMonsterTags: Set<String>,
    val availableItemIds: Set<String>,
    val craftableRecipes: List<CraftRecipeDef>,
    val gatherableNodes: List<GatherNodeDef>,
    val craftingEnabled: Boolean,
    val gatheringEnabled: Boolean,
    val dungeonEnabled: Boolean
)

private data class TargetResolution(
    val targetId: String? = null,
    val targetTag: String? = null,
    val targetName: String = "",
    val difficultyFactor: Double = 1.0
)

class QuestGenerator(
    private val repo: DataRepository,
    private val rng: Random
) {
    fun buildContext(player: PlayerState): QuestGenerationContext {
        val unlockedTiers = repo.mapTiers.values
            .filter { player.level >= it.minLevel }
            .mapTo(mutableSetOf()) { it.id }
        val allowedMonsterIds = repo.mapTiers.values
            .filter { it.id in unlockedTiers }
            .flatMap { it.allowedMonsterTemplates }
            .toMutableSet()
        if (allowedMonsterIds.isEmpty()) {
            allowedMonsterIds.addAll(repo.monsterArchetypes.keys)
        }
        val accessibleArchetypes = allowedMonsterIds.mapNotNull { repo.monsterArchetypes[it] }
        val accessibleTags = mutableSetOf<String>()
        for (archetype in accessibleArchetypes) {
            accessibleTags.addAll(archetype.tags)
            if (archetype.archetype.isNotBlank()) accessibleTags.add(archetype.archetype.lowercase())
            val baseType = archetype.baseType.ifBlank { archetype.id.substringBefore('_') }.lowercase()
            val family = archetype.family.ifBlank { archetype.archetype }.lowercase()
            val monsterType = archetype.monsterTypeId.ifBlank { family }.lowercase()
            accessibleTags.add(baseType)
            if (family.isNotBlank()) accessibleTags.add(family)
            if (monsterType.isNotBlank()) {
                accessibleTags.add(monsterType)
                accessibleTags.add("type:$monsterType")
            }
            accessibleTags.addAll(archetype.questTags.map { it.lowercase() })
        }

        val availableItemIds = mutableSetOf<String>()
        availableItemIds.addAll(repo.items.keys)
        availableItemIds.addAll(repo.itemTemplates.keys)

        val skillLevels = player.skillProgress.mapValues { (_, progress) -> progress.level }
        val craftableRecipes = repo.craftRecipes.values
            .filter { recipe ->
                recipe.enabled &&
                    player.level >= recipe.minPlayerLevel &&
                    skillLevels.getOrDefault(craftSkillTag(recipe).uppercase(), 1) >= recipe.minSkillLevel
            }
        val gatherableNodes = repo.gatherNodes.values
            .filter { node ->
                node.enabled &&
                    player.level >= node.minPlayerLevel &&
                    skillLevels.getOrDefault(gatherSkillTag(node).uppercase(), 1) >= node.minSkillLevel
            }

        return QuestGenerationContext(
            player = player,
            unlockedTierIds = unlockedTiers,
            accessibleMonsterIds = allowedMonsterIds,
            accessibleMonsterTags = accessibleTags,
            availableItemIds = availableItemIds,
            craftableRecipes = craftableRecipes,
            gatherableNodes = gatherableNodes,
            craftingEnabled = repo.craftRecipes.isNotEmpty(),
            gatheringEnabled = repo.gatherNodes.isNotEmpty(),
            dungeonEnabled = repo.mapTiers.isNotEmpty()
        )
    }

    fun generateBatch(
        tier: QuestTier,
        count: Int,
        context: QuestGenerationContext,
        createdAt: Long,
        expiresAt: Long?,
        sourcePool: String,
        avoidTemplateIds: Set<String> = emptySet()
    ): List<QuestInstance> {
        val generated = mutableListOf<QuestInstance>()
        var tries = 0
        val maxTries = max(40, count * 80)
        while (generated.size < count && tries < maxTries) {
            tries++
            val quest = generateSingle(
                tier = tier,
                context = context,
                createdAt = createdAt,
                expiresAt = expiresAt,
                sourcePool = sourcePool,
                avoidTemplateIds = avoidTemplateIds
            ) ?: continue
            if (isDuplicateTarget(generated, quest)) continue
            generated.add(quest)
        }
        return generated
    }

    fun generateSingle(
        tier: QuestTier,
        context: QuestGenerationContext,
        createdAt: Long,
        expiresAt: Long?,
        sourcePool: String,
        avoidTemplateIds: Set<String> = emptySet()
    ): QuestInstance? {
        val pool = repo.questTemplates.values
            .filter { it.enabled }
            .filter { tier in it.supportedTiers }
            .filterNot { it.id in avoidTemplateIds }
            .filter { isTemplateAllowed(it, context) }
        if (pool.isEmpty()) return null

        val chosenTemplate = pickWeighted(pool) ?: return null
        val target = resolveTarget(chosenTemplate, context) ?: return null
        val requiredAmount = computeRequiredAmount(chosenTemplate, tier, context, target)
        val rewards = computeRewards(chosenTemplate, tier, requiredAmount, context)
        val title = renderTitle(chosenTemplate, target, requiredAmount, tier)
        val description = renderDescription(chosenTemplate, target, requiredAmount, tier)
        val hint = renderHint(chosenTemplate, target)
        return QuestInstance(
            instanceId = UUID.randomUUID().toString(),
            templateId = chosenTemplate.id,
            tier = tier,
            objectiveType = chosenTemplate.objectiveType,
            targetType = chosenTemplate.targetType,
            generatedTargetId = target.targetId,
            generatedTargetTag = target.targetTag,
            generatedTargetName = target.targetName,
            title = title,
            description = description,
            hint = hint,
            requiredAmount = requiredAmount,
            currentProgress = 0,
            rewards = rewards,
            createdAt = createdAt,
            expiresAt = expiresAt,
            acceptedAt = if (tier == QuestTier.ACCEPTED) createdAt else null,
            status = QuestStatus.ACTIVE,
            canCancel = tier == QuestTier.ACCEPTED,
            sourcePool = sourcePool,
            consumeTargetOnComplete = chosenTemplate.constraints.consumeTargetOnComplete
        )
    }

    private fun isTemplateAllowed(template: QuestTemplateDef, context: QuestGenerationContext): Boolean {
        val constraints = template.constraints
        val level = context.player.level
        if (level < constraints.minPlayerLevel || level > constraints.maxPlayerLevel) return false
        if (constraints.requiresCrafting && !context.craftingEnabled) return false
        if (constraints.requiresGathering && !context.gatheringEnabled) return false
        if (constraints.requiresDungeon && !context.dungeonEnabled) return false
        if (constraints.requiresUnlockedTierId != null && constraints.requiresUnlockedTierId !in context.unlockedTierIds) return false
        if (constraints.requiresMonsterTag != null && constraints.requiresMonsterTag !in context.accessibleMonsterTags) return false

        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> {
                val targetId = template.targetId
                targetId == null || targetId in context.accessibleMonsterIds
            }
            QuestObjectiveType.KILL_TAG -> {
                val tag = template.targetTag ?: template.targetId
                tag == null ||
                    tag in context.accessibleMonsterTags ||
                    tag in setOf("elite", "boss", "common", "rare", "epic", "legendary")
            }
            QuestObjectiveType.COLLECT_ITEM -> {
                val itemId = template.targetId
                if (itemId == null) {
                    pickCollectItem(context) != null
                } else {
                    itemId in context.availableItemIds
                }
            }
            QuestObjectiveType.CRAFT_ITEM -> {
                if (!context.craftingEnabled) return false
                val targetId = template.targetId
                val targetTag = template.targetTag?.lowercase()
                context.craftableRecipes.any { recipe ->
                    (targetId == null || recipe.outputItemId == targetId) &&
                        (targetTag == null || craftDisciplineTag(recipe) == targetTag)
                }
            }
            QuestObjectiveType.GATHER_RESOURCE -> {
                if (!context.gatheringEnabled) return false
                val targetId = template.targetId
                val targetTag = template.targetTag?.lowercase()
                context.gatherableNodes.any { node ->
                    (targetId == null || node.resourceItemId == targetId || node.id == targetId) &&
                        (targetTag == null || gatherTag(node) == targetTag)
                }
            }
            QuestObjectiveType.REACH_FLOOR,
            QuestObjectiveType.COMPLETE_RUN -> context.dungeonEnabled
        }
    }

    private fun resolveTarget(template: QuestTemplateDef, context: QuestGenerationContext): TargetResolution? {
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> {
                val chosenId = template.targetId
                    ?: context.accessibleMonsterIds.toList().takeIf { it.isNotEmpty() }?.random(rng)
                    ?: return null
                val archetype = repo.monsterArchetypes[chosenId]
                val name = archetype?.displayName?.takeIf { it.isNotBlank() }
                    ?: archetype?.name
                    ?: chosenId
                val diff = 1.0 + ((archetype?.baseXp ?: 20) / 120.0)
                TargetResolution(
                    targetId = chosenId,
                    targetName = name,
                    difficultyFactor = diff.coerceAtMost(3.0)
                )
            }
            QuestObjectiveType.KILL_TAG -> {
                val chosenTag = template.targetTag
                    ?: template.targetId
                    ?: context.accessibleMonsterTags.toList().takeIf { it.isNotEmpty() }?.random(rng)
                    ?: return null
                TargetResolution(
                    targetTag = chosenTag,
                    targetName = chosenTag.replace('_', ' '),
                    difficultyFactor = if (chosenTag == "elite") 1.8 else 1.2
                )
            }
            QuestObjectiveType.COLLECT_ITEM -> {
                val chosenItemId = template.targetId
                    ?: pickCollectItem(context)
                    ?: return null
                val name = itemName(chosenItemId)
                TargetResolution(
                    targetId = chosenItemId,
                    targetName = name,
                    difficultyFactor = itemDifficulty(chosenItemId)
                )
            }
            QuestObjectiveType.CRAFT_ITEM -> {
                val targetTag = template.targetTag?.lowercase()
                val pool = context.craftableRecipes.filter { recipe ->
                    (template.targetId == null || recipe.outputItemId == template.targetId) &&
                        (targetTag == null || craftDisciplineTag(recipe) == targetTag)
                }
                val recipe = pool.randomOrNull(rng) ?: return null
                TargetResolution(
                    targetId = recipe.outputItemId,
                    targetTag = craftDisciplineTag(recipe),
                    targetName = itemName(recipe.outputItemId),
                    difficultyFactor = 1.2 + (recipe.ingredients.size * 0.25)
                )
            }
            QuestObjectiveType.GATHER_RESOURCE -> {
                val targetTag = template.targetTag?.lowercase()
                val pool = context.gatherableNodes.filter { node ->
                    (template.targetId == null || node.id == template.targetId || node.resourceItemId == template.targetId) &&
                        (targetTag == null || gatherTag(node) == targetTag)
                }
                val node = pool.randomOrNull(rng) ?: return null
                TargetResolution(
                    targetId = node.resourceItemId,
                    targetTag = gatherTag(node),
                    targetName = itemName(node.resourceItemId),
                    difficultyFactor = 1.0 + (node.minPlayerLevel / 40.0)
                )
            }
            QuestObjectiveType.REACH_FLOOR -> {
                TargetResolution(
                    targetName = "andar da dungeon",
                    difficultyFactor = 1.0
                )
            }
            QuestObjectiveType.COMPLETE_RUN -> {
                TargetResolution(
                    targetName = "exploracao completa",
                    difficultyFactor = 1.0
                )
            }
        }
    }

    private fun computeRequiredAmount(
        template: QuestTemplateDef,
        tier: QuestTier,
        context: QuestGenerationContext,
        target: TargetResolution
    ): Int {
        val baseRoll = rollRange(template.amountRange)
        val levelFactor = 1.0 + (context.player.level / 35.0)
        val tierFactor = when (tier) {
            QuestTier.ACCEPTED -> 1.0
            QuestTier.DAILY -> 3.0
            QuestTier.WEEKLY -> 10.0
            QuestTier.MONTHLY -> 24.0
        }
        val objectiveFactor = when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> 1.0
            QuestObjectiveType.KILL_TAG -> 1.2
            QuestObjectiveType.COLLECT_ITEM -> 0.9
            QuestObjectiveType.CRAFT_ITEM -> 0.7
            QuestObjectiveType.GATHER_RESOURCE -> 1.1
            QuestObjectiveType.REACH_FLOOR -> 0.2
            QuestObjectiveType.COMPLETE_RUN -> 0.15
        }
        return when (template.objectiveType) {
            QuestObjectiveType.REACH_FLOOR -> {
                val floorBase = context.player.level + (baseRoll * tierFactor).roundToInt()
                max(5, floorBase)
            }
            QuestObjectiveType.COMPLETE_RUN -> {
                val runs = (baseRoll * objectiveFactor * (tierFactor / 3.0)).roundToInt()
                max(1, runs)
            }
            else -> {
                val scaled = baseRoll * tierFactor * levelFactor * target.difficultyFactor * objectiveFactor
                max(1, scaled.roundToInt())
            }
        }
    }

    private fun computeRewards(
        template: QuestTemplateDef,
        tier: QuestTier,
        requiredAmount: Int,
        context: QuestGenerationContext
    ): QuestRewardBundle {
        val profile = template.rewardProfile
        val tierMultiplier = when (tier) {
            QuestTier.ACCEPTED -> 1.0
            QuestTier.DAILY -> 2.5
            QuestTier.WEEKLY -> 6.0
            QuestTier.MONTHLY -> 12.0
        }
        val effortScale = (requiredAmount / 10.0).coerceAtLeast(1.0)
        val levelScale = 1.0 + (context.player.level / 20.0)
        val xp = (profile.xpBase * tierMultiplier * profile.effortMultiplier * effortScale * levelScale)
            .roundToInt()
            .coerceAtLeast(1)
        val gold = (profile.goldBase * tierMultiplier * profile.effortMultiplier * effortScale)
            .roundToInt()
            .coerceAtLeast(1)
        val special = (profile.specialCurrencyBase * tierMultiplier).roundToInt().coerceAtLeast(0)

        val rolledItems = mutableListOf<QuestRewardItem>()
        for (itemDef in profile.itemRewards) {
            if (!context.availableItemIds.contains(itemDef.itemId)) continue
            if (rng.nextDouble(0.0, 100.0) > itemDef.chancePct) continue
            val minQty = itemDef.minQty.coerceAtLeast(1)
            val maxQty = max(minQty, itemDef.maxQty)
            val qty = if (maxQty == minQty) minQty else rng.nextInt(minQty, maxQty + 1)
            rolledItems.add(QuestRewardItem(itemDef.itemId, qty))
        }

        return QuestRewardBundle(
            xp = xp,
            gold = gold,
            specialCurrency = special,
            items = rolledItems
        )
    }

    private fun renderTitle(
        template: QuestTemplateDef,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        if (template.titleTemplate.isNotBlank()) {
            return applyTemplate(template.titleTemplate, target, amount, tier)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER -> "Elimine $amount ${target.targetName}"
            QuestObjectiveType.KILL_TAG -> "Elimine $amount inimigos ${target.targetName}"
            QuestObjectiveType.COLLECT_ITEM -> "Colete $amount ${target.targetName}"
            QuestObjectiveType.CRAFT_ITEM -> "Crie $amount ${target.targetName}"
            QuestObjectiveType.GATHER_RESOURCE -> "Obtenha $amount ${target.targetName}"
            QuestObjectiveType.REACH_FLOOR -> "Alcance o andar $amount"
            QuestObjectiveType.COMPLETE_RUN -> "Conclua $amount exploracoes"
        }
    }

    private fun renderDescription(
        template: QuestTemplateDef,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        if (template.descriptionTemplate.isNotBlank()) {
            return applyTemplate(template.descriptionTemplate, target, amount, tier)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER ->
                "Derrote $amount ${target.targetName} nas areas desbloqueadas da dungeon."
            QuestObjectiveType.KILL_TAG ->
                "Derrote $amount monstros da categoria ${target.targetName}."
            QuestObjectiveType.COLLECT_ITEM ->
                "Colete $amount ${target.targetName}. A entrega sera validada no inventario."
            QuestObjectiveType.CRAFT_ITEM ->
                "Crie $amount ${target.targetName} em uma disciplina de craft valida."
            QuestObjectiveType.GATHER_RESOURCE ->
                "Reuna $amount ${target.targetName} em pontos de coleta disponiveis."
            QuestObjectiveType.REACH_FLOOR ->
                "Alcance pelo menos o andar $amount da dungeon infinita."
            QuestObjectiveType.COMPLETE_RUN ->
                "Finalize $amount exploracoes da dungeon."
        }
    }

    private fun renderHint(template: QuestTemplateDef, target: TargetResolution): String {
        if (template.hintTemplate.isNotBlank()) {
            return applyTemplate(template.hintTemplate, target, 0, QuestTier.ACCEPTED)
        }
        val itemId = target.targetId
        if (itemId != null && template.objectiveType in setOf(
                QuestObjectiveType.COLLECT_ITEM,
                QuestObjectiveType.CRAFT_ITEM,
                QuestObjectiveType.GATHER_RESOURCE
            )
        ) {
            return itemSourceHint(itemId)
        }
        return when (template.objectiveType) {
            QuestObjectiveType.KILL_MONSTER,
            QuestObjectiveType.KILL_TAG -> "Procure inimigos em tiers desbloqueados."
            QuestObjectiveType.REACH_FLOOR -> "Avance de forma consistente; fugir encerra o progresso da run."
            QuestObjectiveType.COMPLETE_RUN -> "Concluir uma run conta ao sair com sucesso."
            else -> ""
        }
    }

    private fun itemSourceHint(itemId: String): String {
        val fromGather = repo.gatherNodes.values.filter { it.resourceItemId == itemId }.map { it.name }
        if (fromGather.isNotEmpty()) {
            return "Pode ser obtido em gathering: ${fromGather.joinToString(", ")}."
        }

        val recipe = repo.craftRecipes.values.firstOrNull { it.outputItemId == itemId }
        if (recipe != null) {
            return "Pode ser criado via ${recipe.discipline.name.lowercase()} na receita ${recipe.name}."
        }

        val tables = repo.dropTables.values.filter { table ->
            table.entries.any { it.itemId == itemId }
        }.map { it.id }
        if (tables.isNotEmpty()) {
            val archetypes = repo.monsterArchetypes.values
                .filter { it.dropTableId in tables }
                .map { it.name }
                .distinct()
                .take(4)
            if (archetypes.isNotEmpty()) {
                return "Pode dropar de monstros como: ${archetypes.joinToString(", ")}."
            }
            return "Pode dropar em tabelas: ${tables.joinToString(", ")}."
        }

        return "Consulte as fontes desbloqueadas no seu progresso atual."
    }

    private fun itemName(itemId: String): String {
        return repo.items[itemId]?.name
            ?: repo.itemTemplates[itemId]?.name
            ?: itemId
    }

    private fun craftDisciplineTag(recipe: rpg.model.CraftRecipeDef): String {
        return craftSkillTag(recipe)
    }

    private fun gatherTag(node: rpg.model.GatherNodeDef): String {
        return gatherSkillTag(node)
    }

    private fun craftSkillTag(recipe: rpg.model.CraftRecipeDef): String {
        return recipe.skillType?.name?.lowercase() ?: when (recipe.discipline) {
            rpg.model.CraftDiscipline.FORGE -> "blacksmith"
            rpg.model.CraftDiscipline.ALCHEMY -> "alchemist"
            rpg.model.CraftDiscipline.COOKING -> "cooking"
        }
    }

    private fun gatherSkillTag(node: rpg.model.GatherNodeDef): String {
        return node.skillType?.name?.lowercase() ?: when (node.type) {
            rpg.model.GatheringType.MINING -> "mining"
            rpg.model.GatheringType.HERBALISM -> "gathering"
            rpg.model.GatheringType.WOODCUTTING -> "woodcutting"
            rpg.model.GatheringType.FISHING -> "fishing"
        }
    }

    private fun itemDifficulty(itemId: String): Double {
        val template = repo.itemTemplates[itemId]
        if (template != null) {
            return 1.0 + (template.dropTier * 0.25)
        }
        val dropCount = repo.dropTables.values.count { table -> table.entries.any { it.itemId == itemId } }
        return if (dropCount <= 0) 1.0 else (1.2 / dropCount).coerceAtLeast(0.8)
    }

    private fun pickCollectItem(context: QuestGenerationContext): String? {
        val fromDrops = repo.dropTables.values
            .flatMap { it.entries.map { entry -> entry.itemId } }
            .filter { itemId ->
                itemId in context.availableItemIds &&
                    (repo.items[itemId]?.type == ItemType.CONSUMABLE ||
                        repo.items[itemId]?.type == ItemType.MATERIAL)
            }
            .distinct()
        val fromGather = context.gatherableNodes.map { it.resourceItemId }
        val pool = (fromDrops + fromGather).distinct()
        return pool.randomOrNull(rng)
    }

    private fun applyTemplate(
        template: String,
        target: TargetResolution,
        amount: Int,
        tier: QuestTier
    ): String {
        return template
            .replace("{target}", target.targetName)
            .replace("{targetId}", target.targetId ?: "")
            .replace("{targetTag}", target.targetTag ?: "")
            .replace("{amount}", amount.toString())
            .replace("{tier}", tier.name.lowercase())
    }

    private fun rollRange(range: rpg.model.IntRangeDef): Int {
        val min = range.min.coerceAtLeast(1)
        val max = max(min, range.max)
        return if (min == max) min else rng.nextInt(min, max + 1)
    }

    private fun pickWeighted(pool: List<QuestTemplateDef>): QuestTemplateDef? {
        if (pool.isEmpty()) return null
        val total = pool.sumOf { it.weight.coerceAtLeast(1) }
        var roll = rng.nextInt(total)
        for (template in pool) {
            roll -= template.weight.coerceAtLeast(1)
            if (roll < 0) return template
        }
        return pool.first()
    }

    private fun isDuplicateTarget(existing: List<QuestInstance>, candidate: QuestInstance): Boolean {
        return existing.any {
            it.objectiveType == candidate.objectiveType &&
                it.generatedTargetId == candidate.generatedTargetId &&
                it.generatedTargetTag == candidate.generatedTargetTag
        }
    }
}
