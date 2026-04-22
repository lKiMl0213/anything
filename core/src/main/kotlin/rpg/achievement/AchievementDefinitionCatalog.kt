package rpg.achievement

import kotlin.math.max

internal object AchievementDefinitionCatalog {
    fun buildDefinitions(): List<AchievementDefinition> {
        val standardScaling = AchievementRewardScaling(goldByTier = listOf(100, 250, 500, 1000, 2500))
        val shortScaling = AchievementRewardScaling(goldByTier = listOf(200, 500, 1200))
        val hiddenScaling = AchievementRewardScaling(goldByTier = listOf(400, 1200, 2600))

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
            id = "death_back_to_life",
            name = "De Volta a Vida!",
            description = "Morra e se levante novamente.",
            category = AchievementCategory.MORTE,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_DEATHS),
            tierTargets = listOf(1, 5, 20, 50, 100)
        )
        add(
            id = "death_stubborn",
            name = "Teimoso",
            description = "Persistencia alem da derrota.",
            category = AchievementCategory.MORTE,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_DEATHS),
            tierTargets = listOf(10, 25, 50, 100, 250)
        )

        add(
            id = "rest_sweet_dreams",
            name = "Bons Sonhos",
            description = "Durma para recuperar todas as forcas.",
            category = AchievementCategory.DESCANSO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_FULL_REST_SLEEPS),
            tierTargets = listOf(1, 10, 25, 50, 100)
        )
        add(
            id = "rest_loyal_customer",
            name = "Cliente Fiel",
            description = "A taverna ja te conhece pelo nome.",
            category = AchievementCategory.DESCANSO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_FULL_REST_SLEEPS),
            tierTargets = listOf(25, 50, 100, 250, 500)
        )

        add(
            id = "gold_first_profit",
            name = "Primeiro Lucro",
            description = "Acumule ouro ao longo da jornada.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_GOLD_EARNED),
            tierTargets = listOf(100, 1000, 5000, 20000, 100000)
        )
        add(
            id = "gold_golden_hand",
            name = "Mao de Ouro",
            description = "Seu fluxo de ouro nao para de crescer.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_GOLD_EARNED),
            tierTargets = listOf(500, 5000, 25000, 100000, 500000)
        )
        add(
            id = "gold_spender",
            name = "Gastador",
            description = "Gaste ouro para acelerar seu progresso.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_GOLD_SPENT),
            tierTargets = listOf(100, 1000, 5000, 20000, 100000)
        )

        add(
            id = "mob_slime_hunter",
            name = "Cacador de Slimes",
            description = "Abata slimes usando o tipo base do monstro.",
            category = AchievementCategory.MOBS,
            trackedStat = AchievementTrackedStat.KillsByBaseType("slime"),
            tierTargets = listOf(10, 50, 100, 250, 1000)
        )
        add(
            id = "mob_wolf_exterminator",
            name = "Exterminador de Lobos",
            description = "Abata lobos usando o tipo base do monstro.",
            category = AchievementCategory.MOBS,
            trackedStat = AchievementTrackedStat.KillsByBaseType("wolf"),
            tierTargets = listOf(10, 50, 100, 250, 1000)
        )
        add(
            id = "mob_elemental_hunter",
            name = "Cacador de Elementais",
            description = "Abata elementais usando o tipo base do monstro.",
            category = AchievementCategory.MOBS,
            trackedStat = AchievementTrackedStat.KillsByBaseType("elemental"),
            tierTargets = listOf(10, 50, 100, 250, 1000)
        )

        for (star in 0..7) {
            add(
                id = "star_${star}_hunter",
                name = "Cacador de ${star}*",
                description = "Derrote monstros com ${star} estrela(s).",
                category = AchievementCategory.ESTRELAS,
                trackedStat = AchievementTrackedStat.KillsByStar(star),
                tierTargets = starTierTargets(star),
                rewardScaling = standardScaling
            )
        }

        add(
            id = "combat_first_victory",
            name = "Primeira Vitoria",
            description = "Venca batalhas em qualquer dungeon.",
            category = AchievementCategory.COMBATE,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_BATTLES_WON),
            tierTargets = listOf(1, 10, 50, 100, 500)
        )
        add(
            id = "combat_boss_slayer",
            name = "Matador de Chefes",
            description = "Derrube chefes cada vez mais poderosos.",
            category = AchievementCategory.COMBATE,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_BOSSES_KILLED),
            tierTargets = listOf(1, 5, 20, 50, 100)
        )
        add(
            id = "combat_precise_strike",
            name = "Golpe Preciso",
            description = "Acerte golpes criticos com frequencia.",
            category = AchievementCategory.COMBATE,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_CRITICAL_HITS),
            tierTargets = listOf(10, 50, 100, 250, 1000)
        )

        add(
            id = "progress_adventurer",
            name = "Aventureiro",
            description = "Conclua quests ao longo da jornada.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_QUESTS_COMPLETED),
            tierTargets = listOf(1, 10, 25, 50, 100)
        )
        add(
            id = "progress_path_choice",
            name = "Escolha de Caminho",
            description = "Desbloqueie caminhos de 2a classe.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_SUBCLASS_UNLOCKS),
            tierTargets = listOf(1, 2, 3),
            rewardScaling = shortScaling
        )
        add(
            id = "progress_supreme_path",
            name = "Caminho Supremo",
            description = "Desbloqueie caminhos de especializacao.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(AchievementStatKeys.TOTAL_SPECIALIZATION_UNLOCKS),
            tierTargets = listOf(1, 2, 3),
            rewardScaling = shortScaling
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

    private fun starTierTargets(star: Int): List<Long> {
        return when (star.coerceIn(0, 7)) {
            0 -> listOf(10, 50, 100, 250, 1000)
            1 -> listOf(10, 45, 90, 220, 900)
            2 -> listOf(8, 35, 75, 180, 700)
            3 -> listOf(6, 25, 55, 140, 500)
            4 -> listOf(4, 15, 35, 90, 300)
            5 -> listOf(3, 10, 24, 60, 180)
            6 -> listOf(2, 6, 14, 35, 90)
            else -> listOf(1, 2, 5, 10, 25)
        }
    }
}
