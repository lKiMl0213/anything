package rpg.quest

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import rpg.model.IntRangeDef
import rpg.model.QuestObjectiveType
import rpg.model.QuestTemplateDef
import rpg.model.QuestTier

internal class QuestRewardCalculator(
    private val rng: Random
) {
    fun computeRequiredAmount(
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

    fun computeRewards(
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

    private fun rollRange(range: IntRangeDef): Int {
        val min = range.min.coerceAtLeast(1)
        val max = max(min, range.max)
        return if (min == max) min else rng.nextInt(min, max + 1)
    }
}
