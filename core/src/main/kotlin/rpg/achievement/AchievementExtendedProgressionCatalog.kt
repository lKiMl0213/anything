package rpg.achievement

import kotlin.math.max

internal object AchievementExtendedProgressionCatalog {
    fun build(
        standardScaling: AchievementRewardScaling,
        hiddenScaling: AchievementRewardScaling
    ): List<AchievementDefinition> {
        val definitions = mutableListOf<AchievementDefinition>()

        fun add(
            id: String,
            name: String,
            description: String,
            category: AchievementCategory,
            trackedStat: AchievementTrackedStat,
            tierTargets: List<Long>,
            rewardScaling: AchievementRewardScaling = standardScaling,
            isHidden: Boolean = false
        ) {
            val normalizedTargets = tierTargets
                .map { max(1L, it) }
                .distinct()
                .sorted()
            if (normalizedTargets.isEmpty()) return
            definitions += AchievementDefinition(
                id = id,
                name = name,
                description = description,
                category = category,
                trackedStat = trackedStat,
                tierTargets = normalizedTargets,
                rewardScaling = rewardScaling,
                isHidden = isHidden,
                capped = true
            )
        }

        add(
            id = "enchant_apprentice",
            name = "Aprendiz Arcano",
            description = "Realize tentativas de encantamento.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Enchant.NAMESPACE,
                AchievementCounterKeys.Enchant.ATTEMPTS
            ),
            tierTargets = listOf(5, 25, 75, 180, 400)
        )
        add(
            id = "enchant_master",
            name = "Mestre do Encantamento",
            description = "Conquiste sucessos no encantamento de itens.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Enchant.NAMESPACE,
                AchievementCounterKeys.Enchant.SUCCESSES
            ),
            tierTargets = listOf(3, 15, 40, 100, 220)
        )
        add(
            id = "fusion_alchemist",
            name = "Artesao da Fusao",
            description = "Funda equipamentos e pedras de encantamento.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Fusion.NAMESPACE,
                AchievementCounterKeys.Fusion.TOTAL
            ),
            tierTargets = listOf(3, 12, 30, 70, 140)
        )
        add(
            id = "extraction_scholar",
            name = "Erudito da Extracao",
            description = "Extraia encantamentos para criar pedras.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Extraction.NAMESPACE,
                AchievementCounterKeys.Extraction.STONES_CREATED
            ),
            tierTargets = listOf(2, 8, 20, 45, 90)
        )
        add(
            id = "hunter_instinct",
            name = "Instinto de Cacador",
            description = "Obtenha recursos em atividades de caca.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Hunting.NAMESPACE,
                AchievementCounterKeys.Hunting.TOTAL_UNITS
            ),
            tierTargets = listOf(30, 120, 300, 650, 1400)
        )
        add(
            id = "hunter_trophy",
            name = "Colecionador de Trofeus",
            description = "Encontre drops raros durante a caca.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Hunting.NAMESPACE,
                AchievementCounterKeys.Hunting.RARE_DROPS
            ),
            tierTargets = listOf(2, 10, 24, 50, 100)
        )
        add(
            id = "chef_on_fire",
            name = "Chef em Chamas",
            description = "Cozinhe receitas para fortalecer seu personagem.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Cooking.NAMESPACE,
                AchievementCounterKeys.Cooking.RECIPES_DONE
            ),
            tierTargets = listOf(5, 20, 60, 140, 320)
        )
        add(
            id = "chef_buff_master",
            name = "Sabor da Vitoria",
            description = "Ative buffs de culinaria durante sua jornada.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.Cooking.NAMESPACE,
                AchievementCounterKeys.Cooking.BUFFS_USED
            ),
            tierTargets = listOf(3, 15, 40, 90, 180)
        )
        add(
            id = "enchant_resource_collector",
            name = "Coletor de Runas",
            description = "Adquira pedras, runas e pergaminhos de encantamento.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.CustomCounter(
                AchievementCounterKeys.EnchantResources.NAMESPACE,
                AchievementCounterKeys.EnchantResources.ACQUIRED
            ),
            tierTargets = listOf(10, 50, 130, 300, 650)
        )
        add(
            id = "hidden_class_reset",
            name = "Renascimento da Classe",
            description = "Sobreviva aos resets de caminho e retorne mais forte.",
            category = AchievementCategory.OCULTA,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_CLASS_RESET_TRIGGERS),
            tierTargets = listOf(1, 2, 3),
            rewardScaling = hiddenScaling,
            isHidden = true
        )

        return definitions
    }
}
