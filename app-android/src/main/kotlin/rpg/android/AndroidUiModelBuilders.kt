package rpg.android

import rpg.android.state.AttributeAllocationUiState
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
import rpg.model.GameState
import rpg.model.SkillType
import kotlin.math.ceil

internal object AndroidUiModelBuilders {
    fun fallbackNewGameUi(state: CharacterCreationUiState): NewGameUiModel {
        return NewGameUiModel(
            name = state.name,
            selectedRaceName = state.races.firstOrNull { it.id == state.selectedRaceId }?.label ?: "-",
            selectedClassName = state.classes.firstOrNull { it.id == state.selectedClassId }?.label ?: "-",
            pointsRemaining = state.pointsRemaining,
            attributes = state.attributes.map { NewGameAttributeUi(it.code, it.label, it.finalValue) },
            canConfirm = state.canConfirm,
            message = state.message
        )
    }

    fun fallbackCharacterAttributeUi(state: AttributeAllocationUiState): AttributeDistributionUiModel {
        return AttributeDistributionUiModel(
            title = "Distribuicao de Atributos",
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
            classSummaryLines = deps.creationQueryService.classSummaryLines(selected.classId)
        )
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
            title = "Distribuicao de Atributos",
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
            title = "Distribuicao de Atributos",
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
            raceClassLabel = "-",
            levelXpLabel = "-",
            currencyLabel = "-",
            inventoryCapacityLabel = "-",
            hpCurrent = 0.0,
            hpMax = 1.0,
            mpCurrent = 0.0,
            mpMax = 1.0,
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
            Triple(SkillType.MINING, "Mineracao", "⛏"),
            Triple(SkillType.GATHERING, "Coleta", "🌿"),
            Triple(SkillType.WOODCUTTING, "Lenhador", "🪓"),
            Triple(SkillType.HUNTING, "Caca", "🏹"),
            Triple(SkillType.ALCHEMIST, "Alquimia", "🧪"),
            Triple(SkillType.COOKING, "Culinaria", "🍳"),
            Triple(SkillType.ENCHANTING, "Encantamento", "✨")
        ).map { (type, label, symbol) ->
            val snapshot = deps.actionHandler.engine().skillSystem.snapshot(gameState.player, type)
            HubSkillUi(
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

        return MainHubUiModel(
            name = gameState.player.name,
            raceClassLabel = "$race | ${clazz?.name ?: gameState.player.classId}",
            levelXpLabel = "Nivel ${gameState.player.level} | XP ${gameState.player.xp}",
            currencyLabel = "Ouro ${gameState.player.gold} | Cash ${gameState.player.premiumCash}",
            inventoryCapacityLabel = deps.inventoryQueryService.inventoryCapacityLabel(gameState),
            hpCurrent = gameState.player.currentHp,
            hpMax = stats.derived.hpMax,
            mpCurrent = gameState.player.currentMp,
            mpMax = stats.derived.mpMax,
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

    fun buildCharacterUi(
        session: GameSession,
        state: GameState,
        deps: RuntimeDeps
    ): CharacterUiModel {
        val slots = deps.inventoryQueryService.equippedSlots(state)
        val mainSlots = slots.filterNot { it.slotKey.startsWith("ACCESSORY") }
        val accessorySlots = slots.filter { it.slotKey.startsWith("ACCESSORY") }
        return CharacterUiModel(
            equippedSlots = mainSlots,
            accessorySlots = accessorySlots,
            inventoryStacks = deps.inventoryQueryService.inventoryStacks(state, session.inventoryFilter),
            inventoryCapacityLabel = deps.inventoryQueryService.inventoryCapacityLabel(state),
            canOpenAttributes = true,
            canOpenTalents = true
        )
    }
}
