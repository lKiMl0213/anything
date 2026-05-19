package rpg.android

import rpg.android.state.AttributeAllocationUiState
import rpg.android.state.BackpackTierUi
import rpg.android.state.CharacterUpgradeUi
import rpg.android.state.AttributeDistributionRowUi
import rpg.android.state.AttributeDistributionUiModel
import rpg.android.state.CharacterCreationUiState
import rpg.android.state.CharacterUiModel
import rpg.android.state.HubSkillUi
import rpg.android.state.MainHubUiModel
import rpg.android.state.MainSection
import rpg.android.state.NewGameAttributeUi
import rpg.android.state.NewGameUiModel
import rpg.android.state.RaceClassUiModel
import rpg.android.state.SelectOption
import rpg.application.GameSession
import rpg.engine.Progression
import rpg.model.GameState
import rpg.model.SkillType
import rpg.model.ShopCurrency
import rpg.application.shop.UpgradeMenuCategory
import rpg.premium.PremiumSupport
import kotlin.math.ceil

internal object AndroidUiModelBuilders {
    fun fallbackNewGameUi(state: CharacterCreationUiState): NewGameUiModel {
        return NewGameUiModel(
            name = state.name,
            selectedRaceName = state.races.firstOrNull { it.id == state.selectedRaceId }?.label ?: "-",
            selectedClassName = state.classes.firstOrNull { it.id == state.selectedClassId }?.label ?: "-",
            pointsRemaining = state.pointsRemaining,
            attributes = state.attributes.map { NewGameAttributeUi(it.code, it.label, it.finalValue) },
            attributeDetailByCode = state.attributes.associate { it.code to listOf(it.label) },
            canConfirm = state.canConfirm,
            message = state.message
        )
    }

    fun fallbackCharacterAttributeUi(state: AttributeAllocationUiState): AttributeDistributionUiModel {
        return AttributeDistributionUiModel(
            title = "Distribuição de Atributos",
            pointsRemaining = state.pointsRemaining,
            rows = state.rows.map { AttributeDistributionRowUi(it.code, it.label, it.previewFinal, it.pending) },
            detailByCode = state.rows.associate { it.code to listOf(it.label) },
            canConfirm = state.canApply,
            messages = state.messages
        )
    }

    fun buildNewGameUi(
        session: GameSession,
        deps: RuntimeDeps
    ): NewGameUiModel {
        val draft = session.creationDraft ?: deps.creationQueryService.initialDraft()
        val rows = deps.creationQueryService.attributeRows(draft)
        return NewGameUiModel(
            name = draft.name,
            selectedRaceName = deps.creationQueryService.raceById(draft.raceId)?.name ?: "-",
            selectedClassName = deps.creationQueryService.classById(draft.classId)?.name ?: "-",
            pointsRemaining = draft.remainingPoints,
            attributes = rows.map { NewGameAttributeUi(it.code, it.label, it.finalValue) },
            attributeDetailByCode = rows.associate { row ->
                val detail = deps.creationQueryService.attributeDetail(row.code)
                row.code to (detail?.directEffects.orEmpty() + detail?.gameplayImpact.orEmpty())
            },
            canConfirm = draft.name.isNotBlank() && draft.raceId != null && draft.classId != null,
            message = session.messages.firstOrNull()
        )
    }

    fun buildRaceClassUi(
        deps: RuntimeDeps,
        draft: RaceClassSelectionDraft?
    ): RaceClassUiModel {
        val selected = draft ?: RaceClassSelectionDraft(null, null)
        return RaceClassUiModel(
            selectedRaceId = selected.raceId,
            selectedClassId = selected.classId,
            raceOptions = deps.creationQueryService.availableRaces().map { SelectOption(it.id, it.name) },
            classOptions = deps.creationQueryService.availableClasses().map { SelectOption(it.id, it.name) },
            raceSummaryLines = deps.creationQueryService.raceSummaryLines(selected.raceId),
            classSummaryLines = deps.creationQueryService.classSummaryLines(selected.classId),
            spriteAssetPath = characterCreationSpritePath(
                raceId = selected.raceId,
                classId = selected.classId
            )
        )
    }

    private fun characterCreationSpritePath(
        raceId: String?,
        classId: String?
    ): String {
        val race = raceId?.takeIf { it.isNotBlank() } ?: return ""
        return if (classId.isNullOrBlank()) {
            "character_sprites/$race/base/${race}base.png"
        } else {
            "character_sprites/$race/$classId/$race$classId.png"
        }
    }

    fun buildCreationAttributeUi(
        session: GameSession,
        deps: RuntimeDeps,
        creationAttributeDraft: Map<String, Int>
    ): AttributeDistributionUiModel {
        val draft = session.creationDraft ?: deps.creationQueryService.initialDraft()
        val spent = creationAttributeDraft.values.sum()
        val remaining = (draft.totalPoints - spent).coerceAtLeast(0)
        val rows = deps.creationQueryService.attributeRows(draft).map {
            AttributeDistributionRowUi(
                it.code,
                it.label,
                it.raceBonus + it.classBonus + (creationAttributeDraft[it.code] ?: 0),
                creationAttributeDraft[it.code] ?: 0
            )
        }
        val details = rows.associate { row ->
            val detail = deps.creationQueryService.attributeDetail(row.code)
            row.code to (detail?.directEffects.orEmpty() + detail?.gameplayImpact.orEmpty())
        }
        return AttributeDistributionUiModel(
            title = "Distribuição de Atributos",
            pointsRemaining = remaining,
            rows = rows,
            detailByCode = details,
            canConfirm = true,
            messages = session.messages
        )
    }

    fun buildCharacterAttributeUi(
        session: GameSession,
        state: GameState,
        deps: RuntimeDeps,
        characterAttributePending: Map<String, Int>
    ): AttributeDistributionUiModel {
        val rows = deps.characterQueryService.attributeRows(state)
        val pendingSpent = characterAttributePending.values.sum()
        val remaining = (state.player.unspentAttrPoints - pendingSpent).coerceAtLeast(0)
        val uiRows = rows.map {
            val pending = characterAttributePending[it.code] ?: 0
            AttributeDistributionRowUi(it.code, it.label, it.finalValue + pending, pending)
        }
        val details = rows.associate { row ->
            row.code to deps.characterQueryService.attributeDetail(state, row.code)?.detailLines.orEmpty()
        }
        return AttributeDistributionUiModel(
            title = "Distribuição de Atributos",
            pointsRemaining = remaining,
            rows = uiRows,
            detailByCode = details,
            canConfirm = pendingSpent > 0,
            messages = session.messages
        )
    }

    fun buildHubUi(
        state: GameState?,
        messages: List<String>,
        deps: RuntimeDeps
    ): MainHubUiModel {
        val gameState = state ?: return MainHubUiModel(
            name = "-",
            premiumStatusLabel = "Premium: desativado",
            raceClassLabel = "-",
            levelXpLabel = "-",
            playerLevel = 1,
            playerXp = 0,
            playerXpMax = 1,
            currencyLabel = "-",
            inventoryCapacityLabel = "-",
            hpCurrent = 0.0,
            hpMax = 1.0,
            mpCurrent = 0.0,
            mpMax = 1.0,
            activeEffectName = null,
            activeEffectRemainingSeconds = 0,
            deathDebuffStacks = 0,
            deathDebuffMinutes = 0.0,
            hpRegenPerMinute = 0.0,
            mpRegenPerMinute = 0.0,
            hpEtaSeconds = 0,
            mpEtaSeconds = 0,
            skills = emptyList(),
            infoLines = messages
        )
        val stats = deps.actionHandler.engine().computePlayerStats(gameState.player, gameState.itemInstances)
        val clazz = deps.creationQueryService.classById(gameState.player.classId)
        val race = deps.creationQueryService.raceById(gameState.player.raceId)?.name ?: gameState.player.raceId
        val skills = listOf(
            Triple(SkillType.BLACKSMITH, "Forja", "🔨"),
            Triple(SkillType.FISHING, "Pesca", "🎣"),
            Triple(SkillType.MINING, "Mineração", "⛏"),
            Triple(SkillType.GATHERING, "Coleta", "🌿"),
            Triple(SkillType.WOODCUTTING, "Lenhador", "🪓"),
            Triple(SkillType.HUNTING, "Caça", "🏹"),
            Triple(SkillType.ALCHEMIST, "Alquimia", "🧪"),
            Triple(SkillType.COOKING, "Culinária", "🍳"),
            Triple(SkillType.ENCHANTING, "Encantamento", "✨")
        ).map { (type, label, symbol) ->
            val snapshot = deps.actionHandler.engine().skillSystem.snapshot(gameState.player, type)
            HubSkillUi(
                skillType = type,
                symbol = symbol,
                label = label,
                level = snapshot.level,
                currentXp = snapshot.currentXp,
                requiredXp = snapshot.requiredXp
            )
        }
        val hpGap = (stats.derived.hpMax - gameState.player.currentHp).coerceAtLeast(0.0)
        val mpGap = (stats.derived.mpMax - gameState.player.currentMp).coerceAtLeast(0.0)
        val hpRegen = stats.derived.hpRegen.coerceAtLeast(0.0)
        val mpRegen = stats.derived.mpRegen.coerceAtLeast(0.0)
        val hpEta = if (hpRegen <= 0.0 || hpGap <= 0.0) 0 else ceil((hpGap / hpRegen) * 60.0).toInt()
        val mpEta = if (mpRegen <= 0.0 || mpGap <= 0.0) 0 else ceil((mpGap / mpRegen) * 60.0).toInt()
        val (activeEffectName, activeEffectRemainingSeconds) = buildActiveEffect(gameState)
        val premiumStatusLabel = buildPremiumStatusLabel(gameState)

        return MainHubUiModel(
            name = gameState.player.name,
            premiumStatusLabel = premiumStatusLabel,
            raceClassLabel = "$race | ${clazz?.name ?: gameState.player.classId}",
            levelXpLabel = "Nível ${gameState.player.level} | XP ${gameState.player.xp}",
            playerLevel = gameState.player.level,
            playerXp = gameState.player.xp,
            playerXpMax = Progression.xpForNext(gameState.player.level),
            currencyLabel = "Ouro ${gameState.player.gold} | Cash ${gameState.player.premiumCash}",
            inventoryCapacityLabel = deps.inventoryQueryService.inventoryCapacityLabel(gameState),
            hpCurrent = gameState.player.currentHp,
            hpMax = stats.derived.hpMax,
            mpCurrent = gameState.player.currentMp,
            mpMax = stats.derived.mpMax,
            activeEffectName = activeEffectName,
            activeEffectRemainingSeconds = activeEffectRemainingSeconds,
            deathDebuffStacks = gameState.player.deathDebuffStacks,
            deathDebuffMinutes = gameState.player.deathDebuffMinutes,
            hpRegenPerMinute = hpRegen,
            mpRegenPerMinute = mpRegen,
            hpEtaSeconds = hpEta,
            mpEtaSeconds = mpEta,
            skills = skills,
            infoLines = messages
        )
    }

    private fun buildPremiumStatusLabel(gameState: GameState): String {
        val player = gameState.player
        if (player.premiumPermanent) {
            return "Premium: Ativo (Permanente)"
        }
        if (!PremiumSupport.isPremiumActive(player)) {
            return "Premium: desativado"
        }
        val remainingMs = (player.premiumExpiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        return "Premium: Ativo (${formatPremiumRemaining(remainingMs)})"
    }

    private fun formatPremiumRemaining(remainingMs: Long): String {
        val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(0L)
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> "${days}d ${hours}h"
            hours > 0L -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun buildActiveEffect(gameState: GameState): Pair<String?, Int> {
        val foodBuffName = gameState.player.foodBuffName.takeIf {
            gameState.player.foodBuffRemainingMinutes > 0.0 && it.isNotBlank()
        }
        if (foodBuffName != null) {
            return foodBuffName to (gameState.player.foodBuffRemainingMinutes * 60.0).toInt().coerceAtLeast(0)
        }

        val roomEffect = if (gameState.player.roomEffectRooms > 0 && gameState.player.roomEffectMultiplier != 1.0) {
            val percent = ((gameState.player.roomEffectMultiplier - 1.0) * 100.0).toInt()
            "Buff de sala +${percent}% por ${gameState.player.roomEffectRooms} salas"
        } else {
            null
        }
        if (roomEffect != null) {
            return roomEffect to 0
        }

        val runEffect = if (gameState.player.runAttrMultiplier != 1.0) {
            val percent = ((gameState.player.runAttrMultiplier - 1.0) * 100.0).toInt()
            "Buff de corrida +${percent}%"
        } else {
            null
        }
        if (runEffect != null) {
            return runEffect to 0
        }

        return null to 0
    }

    fun buildCharacterUi(
        session: GameSession,
        state: GameState,
        deps: RuntimeDeps
    ): CharacterUiModel {
        val slots = deps.inventoryQueryService.equippedSlots(state)
        val mainSlots = slots.filterNot { it.slotKey.startsWith("ACCESSORY") }
        val accessorySlots = slots.filter { it.slotKey.startsWith("ACCESSORY") }
        val backpackTiers = deps.inventoryQueryService.backpackTierViews(state)
            .map { BackpackTierUi(tier = it.tier, equipped = it.equipped) }
        val shopQuery = deps.actionHandler.shopQueryService()
        val acquiredUpgrades = UpgradeMenuCategory.entries
            .flatMap { category ->
                shopQuery.upgrades(
                    player = state.player,
                    currency = ShopCurrency.GOLD,
                    category = category
                )
            }
            .filter { it.level > 0 }
            .sortedBy { it.name }
            .map { upgrade ->
                val upgradeAction = if (!upgrade.atMaxLevel) {
                    upgrade.costs.firstOrNull()?.id?.let { costId ->
                        rpg.application.actions.GameAction.BuyUpgrade(
                            upgradeId = upgrade.id,
                            costId = costId,
                            currency = ShopCurrency.GOLD
                        )
                    }
                } else {
                    null
                }
                CharacterUpgradeUi(
                    id = upgrade.id,
                    name = upgrade.name,
                    level = upgrade.level,
                    maxLevel = upgrade.maxLevel,
                    effectLabel = upgrade.currentLabel,
                    summary = upgrade.description.ifBlank { "Sem descrição." },
                    upgradeAction = upgradeAction
                )
            }
        return CharacterUiModel(
            equippedSlots = mainSlots,
            accessorySlots = accessorySlots,
            spriteAssetPath = characterCreationSpritePath(
                raceId = state.player.raceId,
                classId = state.player.classId
            ),
            backpackTiers = backpackTiers,
            inventoryStacks = deps.inventoryQueryService.inventoryStacks(state, session.inventoryFilter),
            inventoryCapacityLabel = deps.inventoryQueryService.inventoryCapacityLabel(state),
            inventorySortMode = session.inventoryFilter.sortMode,
            acquiredUpgrades = acquiredUpgrades,
            canOpenAttributes = true,
            canOpenTalents = true,
            canOpenUpgrades = true
        )
    }
}
