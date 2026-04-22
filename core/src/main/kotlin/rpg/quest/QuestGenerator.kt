package rpg.quest

import java.util.UUID
import kotlin.math.max
import kotlin.random.Random
import rpg.io.DataRepository
import rpg.model.QuestTemplateDef
import rpg.model.QuestTier

class QuestGenerator(
    repo: DataRepository,
    private val rng: Random
) {
    private val catalogSupport = QuestGenerationCatalogSupport(repo, rng)
    private val contextBuilder = QuestGenerationContextBuilder(repo, catalogSupport)
    private val templateEligibility = QuestTemplateEligibility(catalogSupport)
    private val targetResolver = QuestTargetResolver(repo, rng, catalogSupport)
    private val rewardCalculator = QuestRewardCalculator(rng)
    private val textGenerator = QuestTextGenerator(catalogSupport)
    private val questTemplates = repo.questTemplates

    fun buildContext(player: rpg.model.PlayerState): QuestGenerationContext {
        return contextBuilder.buildContext(player)
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
        val pool = questTemplates.values
            .filter { it.enabled }
            .filter { tier in it.supportedTiers }
            .filterNot { it.id in avoidTemplateIds }
            .filter { templateEligibility.isTemplateAllowed(it, context) }
        if (pool.isEmpty()) return null

        val chosenTemplate = pickWeighted(pool) ?: return null
        val target = targetResolver.resolveTarget(chosenTemplate, context) ?: return null
        val requiredAmount = rewardCalculator.computeRequiredAmount(chosenTemplate, tier, context, target)
        val rewards = rewardCalculator.computeRewards(chosenTemplate, tier, requiredAmount, context)
        val title = textGenerator.renderTitle(chosenTemplate, target, requiredAmount, tier)
        val description = textGenerator.renderDescription(chosenTemplate, target, requiredAmount, tier)
        val hint = textGenerator.renderHint(chosenTemplate, target)
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
