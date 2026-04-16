package rpg.achievement

import kotlin.math.max
import rpg.model.PlayerState

class AchievementService {
    private val definitions: List<AchievementDefinition> = buildDefinitions()
    private val definitionsById: Map<String, AchievementDefinition> = definitions.associateBy { it.id }

    fun allDefinitions(): List<AchievementDefinition> = definitions

    fun synchronize(
        player: PlayerState,
        emitNotifications: Boolean = false
    ): AchievementSyncResult {
        val basePlayer = ensureKnownAchievements(player)
        val progressMap = basePlayer.achievementProgressById.toMutableMap()
        val unlocked = mutableListOf<AchievementTierUnlockedNotification>()

        for (definition in definitions) {
            val current = progressMap[definition.id] ?: AchievementProgress(id = definition.id)
            val normalizedCurrent = normalizeProgress(current, definition.id, definition.tierTargets.size)
            val value = trackedValue(basePlayer.lifetimeStats, definition.trackedStat).coerceAtLeast(0L)

            var next = normalizedCurrent.copy(currentValue = value)
            if (next.currentTierIndex >= definition.tierTargets.size) {
                next = next.copy(
                    rewardAvailable = false,
                    maxTierReached = true,
                    capped = definition.capped
                )
            } else {
                val target = definition.tierTargets[next.currentTierIndex]
                var rewardReady = next.rewardAvailable
                if (!rewardReady && value >= target) {
                    rewardReady = true
                    if (emitNotifications) {
                        unlocked += buildTierUnlockedNotification(definition, next.currentTierIndex)
                    }
                }
                if (rewardReady && value < target) {
                    rewardReady = false
                }
                next = next.copy(
                    rewardAvailable = rewardReady,
                    maxTierReached = false,
                    capped = false
                )
            }
            progressMap[definition.id] = next
        }

        return AchievementSyncResult(
            player = basePlayer.copy(achievementProgressById = progressMap.toMap()),
            unlockedTiers = unlocked
        )
    }

    fun hasClaimableRewards(player: PlayerState): Boolean {
        val synced = synchronize(player, emitNotifications = false).player
        return definitions.any { definition ->
            synced.achievementProgressById[definition.id]?.rewardAvailable == true
        }
    }

    fun buildAchievementList(player: PlayerState): AchievementListResult {
        val sync = synchronize(player, emitNotifications = false)
        val views = definitions.mapNotNull { definition ->
            val progress = sync.player.achievementProgressById[definition.id] ?: return@mapNotNull null
            toView(definition, progress)
        }
        return AchievementListResult(sync.player, views)
    }

    fun claimReward(player: PlayerState, achievementId: String): AchievementClaimResult {
        val syncBefore = synchronize(player, emitNotifications = false)
        val syncedPlayer = syncBefore.player
        val definition = definitionsById[achievementId]
            ?: return AchievementClaimResult(
                success = false,
                message = "Conquista nao encontrada.",
                player = syncedPlayer
            )

        val currentProgress = syncedPlayer.achievementProgressById[achievementId]
            ?: AchievementProgress(id = achievementId)
        if (currentProgress.maxTierReached || currentProgress.currentTierIndex >= definition.tierTargets.size) {
            return AchievementClaimResult(
                success = false,
                message = "Conquista ja atingiu o cap MAX.",
                player = syncedPlayer
            )
        }
        if (!currentProgress.rewardAvailable) {
            return AchievementClaimResult(
                success = false,
                message = "Nenhuma recompensa disponivel para resgate.",
                player = syncedPlayer
            )
        }

        val rewardGold = definition.rewardScaling.rewardForTier(currentProgress.currentTierIndex).coerceAtLeast(0)
        val updatedStats = syncedPlayer.lifetimeStats.copy(
            totalGoldEarned = syncedPlayer.lifetimeStats.totalGoldEarned + rewardGold
        )

        val nextTierIndex = currentProgress.currentTierIndex + 1
        val reachedMax = nextTierIndex >= definition.tierTargets.size
        val advancedProgress = currentProgress.copy(
            currentTierIndex = nextTierIndex,
            timesCompleted = currentProgress.timesCompleted + 1,
            rewardAvailable = false,
            maxTierReached = reachedMax,
            capped = reachedMax && definition.capped
        )

        val playerAfterClaim = syncedPlayer.copy(
            gold = syncedPlayer.gold + rewardGold,
            lifetimeStats = updatedStats,
            achievementProgressById = syncedPlayer.achievementProgressById + (achievementId to advancedProgress)
        )
        val syncAfter = synchronize(playerAfterClaim, emitNotifications = true)
        return AchievementClaimResult(
            success = true,
            message = "Recompensa resgatada: +$rewardGold ouro.",
            player = syncAfter.player,
            rewardGold = rewardGold,
            unlockedTiers = syncAfter.unlockedTiers
        )
    }

    private fun toView(
        definition: AchievementDefinition,
        progress: AchievementProgress
    ): AchievementView {
        val tierIndex = progress.currentTierIndex
        val nextTarget = if (tierIndex in definition.tierTargets.indices) {
            definition.tierTargets[tierIndex]
        } else {
            null
        }
        val nextReward = if (tierIndex in definition.tierTargets.indices) {
            definition.rewardScaling.rewardForTier(tierIndex).coerceAtLeast(0)
        } else {
            null
        }
        val revealed = !definition.isHidden || progress.timesCompleted > 0 || progress.rewardAvailable
        val displayName = if (revealed) definition.name else "[???]"
        val displayDescription = if (revealed) definition.description else "[???]"
        val status = when {
            progress.maxTierReached || progress.currentTierIndex >= definition.tierTargets.size -> AchievementStatus.MAX
            progress.rewardAvailable -> AchievementStatus.TIER_COMPLETED
            else -> AchievementStatus.IN_PROGRESS
        }
        return AchievementView(
            id = definition.id,
            category = definition.category,
            displayName = displayName,
            displayDescription = displayDescription,
            currentValue = progress.currentValue,
            currentTierTarget = nextTarget,
            timesCompleted = progress.timesCompleted,
            nextRewardGold = nextReward,
            rewardAvailable = progress.rewardAvailable,
            status = status,
            currentTierIndex = progress.currentTierIndex,
            isHidden = definition.isHidden,
            maxTierReached = progress.maxTierReached,
            capped = progress.capped
        )
    }

    private fun normalizeProgress(
        progress: AchievementProgress,
        id: String,
        tierCount: Int
    ): AchievementProgress {
        val clampedTier = progress.currentTierIndex.coerceIn(0, max(0, tierCount))
        val maxReached = progress.maxTierReached || clampedTier >= tierCount
        return progress.copy(
            id = id,
            currentTierIndex = clampedTier,
            currentValue = progress.currentValue.coerceAtLeast(0L),
            timesCompleted = progress.timesCompleted.coerceAtLeast(0),
            rewardAvailable = if (clampedTier >= tierCount) false else progress.rewardAvailable,
            maxTierReached = maxReached,
            capped = if (clampedTier >= tierCount) true else progress.capped
        )
    }

    private fun ensureKnownAchievements(player: PlayerState): PlayerState {
        val progress = player.achievementProgressById.toMutableMap()
        var changed = false
        for (definition in definitions) {
            if (!progress.containsKey(definition.id)) {
                progress[definition.id] = AchievementProgress(id = definition.id)
                changed = true
            }
        }
        return if (changed) {
            player.copy(achievementProgressById = progress.toMap())
        } else {
            player
        }
    }

    private fun trackedValue(
        stats: PlayerLifetimeStats,
        trackedStat: AchievementTrackedStat
    ): Long {
        return when (trackedStat) {
            is AchievementTrackedStat.LifetimeKey -> when (trackedStat.key) {
                StatKeys.TOTAL_GOLD_EARNED -> stats.totalGoldEarned
                StatKeys.TOTAL_GOLD_SPENT -> stats.totalGoldSpent
                StatKeys.TOTAL_DEATHS -> stats.totalDeaths
                StatKeys.TOTAL_FULL_REST_SLEEPS -> stats.totalFullRestSleeps
                StatKeys.TOTAL_BATTLES_WON -> stats.totalBattlesWon
                StatKeys.TOTAL_BATTLES_LOST -> stats.totalBattlesLost
                StatKeys.TOTAL_BOSSES_KILLED -> stats.totalBossesKilled
                StatKeys.TOTAL_CRITICAL_HITS -> stats.totalCriticalHits
                StatKeys.TOTAL_QUESTS_COMPLETED -> stats.totalQuestsCompleted
                StatKeys.TOTAL_SUBCLASS_UNLOCKS -> stats.totalSubclassUnlocks
                StatKeys.TOTAL_SPECIALIZATION_UNLOCKS -> stats.totalSpecializationUnlocks
                StatKeys.TOTAL_CLASS_RESET_TRIGGERS -> stats.totalClassResetTriggers
                StatKeys.TOTAL_MONSTERS_KILLED -> stats.totalMonstersKilled
                else -> 0L
            }
            is AchievementTrackedStat.KillsByBaseType -> {
                stats.killsByBaseType[normalizeBaseType(trackedStat.baseType)] ?: 0L
            }
            is AchievementTrackedStat.KillsByStar -> {
                stats.killsByStar[trackedStat.star.coerceIn(0, 7)] ?: 0L
            }
            is AchievementTrackedStat.CustomCounter -> {
                val key = customCounterKey(trackedStat.namespace, trackedStat.key)
                stats.customCounters[key] ?: 0L
            }
        }
    }

    private fun buildTierUnlockedNotification(
        definition: AchievementDefinition,
        tierIndex: Int
    ): AchievementTierUnlockedNotification {
        val reward = definition.rewardScaling.rewardForTier(tierIndex).coerceAtLeast(0)
        return AchievementTierUnlockedNotification(
            achievementId = definition.id,
            displayName = definition.name,
            displayDescription = definition.description,
            rewardGold = reward
        )
    }

    private fun buildDefinitions(): List<AchievementDefinition> {
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
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_DEATHS),
            tierTargets = listOf(1, 5, 20, 50, 100)
        )
        add(
            id = "death_stubborn",
            name = "Teimoso",
            description = "Persistencia alem da derrota.",
            category = AchievementCategory.MORTE,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_DEATHS),
            tierTargets = listOf(10, 25, 50, 100, 250)
        )

        add(
            id = "rest_sweet_dreams",
            name = "Bons Sonhos",
            description = "Durma para recuperar todas as forcas.",
            category = AchievementCategory.DESCANSO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_FULL_REST_SLEEPS),
            tierTargets = listOf(1, 10, 25, 50, 100)
        )
        add(
            id = "rest_loyal_customer",
            name = "Cliente Fiel",
            description = "A taverna ja te conhece pelo nome.",
            category = AchievementCategory.DESCANSO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_FULL_REST_SLEEPS),
            tierTargets = listOf(25, 50, 100, 250, 500)
        )

        add(
            id = "gold_first_profit",
            name = "Primeiro Lucro",
            description = "Acumule ouro ao longo da jornada.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_GOLD_EARNED),
            tierTargets = listOf(100, 1000, 5000, 20000, 100000)
        )
        add(
            id = "gold_golden_hand",
            name = "Mao de Ouro",
            description = "Seu fluxo de ouro nao para de crescer.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_GOLD_EARNED),
            tierTargets = listOf(500, 5000, 25000, 100000, 500000)
        )
        add(
            id = "gold_spender",
            name = "Gastador",
            description = "Gaste ouro para acelerar seu progresso.",
            category = AchievementCategory.OURO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_GOLD_SPENT),
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
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_BATTLES_WON),
            tierTargets = listOf(1, 10, 50, 100, 500)
        )
        add(
            id = "combat_boss_slayer",
            name = "Matador de Chefes",
            description = "Derrube chefes cada vez mais poderosos.",
            category = AchievementCategory.COMBATE,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_BOSSES_KILLED),
            tierTargets = listOf(1, 5, 20, 50, 100)
        )
        add(
            id = "combat_precise_strike",
            name = "Golpe Preciso",
            description = "Acerte golpes criticos com frequencia.",
            category = AchievementCategory.COMBATE,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_CRITICAL_HITS),
            tierTargets = listOf(10, 50, 100, 250, 1000)
        )

        add(
            id = "progress_adventurer",
            name = "Aventureiro",
            description = "Conclua quests ao longo da jornada.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_QUESTS_COMPLETED),
            tierTargets = listOf(1, 10, 25, 50, 100)
        )
        add(
            id = "progress_path_choice",
            name = "Escolha de Caminho",
            description = "Desbloqueie caminhos de subclasse.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_SUBCLASS_UNLOCKS),
            tierTargets = listOf(1, 2, 3),
            rewardScaling = shortScaling
        )
        add(
            id = "progress_supreme_path",
            name = "Caminho Supremo",
            description = "Desbloqueie caminhos de especializacao.",
            category = AchievementCategory.PROGRESSAO,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_SPECIALIZATION_UNLOCKS),
            tierTargets = listOf(1, 2, 3),
            rewardScaling = shortScaling
        )
        add(
            id = "hidden_class_reset",
            name = "Renascimento da Classe",
            description = "Sobreviva aos resets de caminho e retorne mais forte.",
            category = AchievementCategory.OCULTA,
            trackedStat = AchievementTrackedStat.LifetimeKey(StatKeys.TOTAL_CLASS_RESET_TRIGGERS),
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

    private fun normalizeBaseType(baseType: String): String {
        return baseType.trim().lowercase().ifBlank { "unknown" }
    }

    private fun customCounterKey(namespace: String, key: String): String {
        val left = namespace.trim().lowercase().ifBlank { "global" }
        val right = key.trim().lowercase().ifBlank { "counter" }
        return "$left:$right"
    }

    object StatKeys {
        const val TOTAL_GOLD_EARNED = "totalGoldEarned"
        const val TOTAL_GOLD_SPENT = "totalGoldSpent"
        const val TOTAL_DEATHS = "totalDeaths"
        const val TOTAL_FULL_REST_SLEEPS = "totalFullRestSleeps"
        const val TOTAL_BATTLES_WON = "totalBattlesWon"
        const val TOTAL_BATTLES_LOST = "totalBattlesLost"
        const val TOTAL_BOSSES_KILLED = "totalBossesKilled"
        const val TOTAL_CRITICAL_HITS = "totalCriticalHits"
        const val TOTAL_QUESTS_COMPLETED = "totalQuestsCompleted"
        const val TOTAL_SUBCLASS_UNLOCKS = "totalSubclassUnlocks"
        const val TOTAL_SPECIALIZATION_UNLOCKS = "totalSpecializationUnlocks"
        const val TOTAL_CLASS_RESET_TRIGGERS = "totalClassResetTriggers"
        const val TOTAL_MONSTERS_KILLED = "totalMonstersKilled"
    }
}

