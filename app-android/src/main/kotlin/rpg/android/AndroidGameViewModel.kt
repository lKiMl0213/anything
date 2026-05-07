package rpg.android

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Files
import rpg.android.combat.AndroidCombatFlowController
import rpg.android.combat.AndroidCombatOutcomeResolver
import rpg.android.state.AndroidUiState
import rpg.android.state.PatchNotesUiModel
import rpg.android.state.PopupDetailUiModel
import rpg.android.state.SaveSlotUi
import rpg.android.state.StartPageUiModel
import rpg.android.state.TimedActionUiState
import rpg.android.ui.scale.GameUiScale
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.actions.GameAction
import rpg.application.support.OutOfCombatTimeService
import rpg.navigation.NavigationState
import rpg.presentation.model.MenuScreenViewModel

class AndroidGameViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<AndroidUiState>(AndroidUiState.Loading)
    val uiState: StateFlow<AndroidUiState> = _uiState.asStateFlow()

    private val _timedActionState = MutableStateFlow<TimedActionUiState?>(null)
    val timedActionState: StateFlow<TimedActionUiState?> = _timedActionState.asStateFlow()

    private val _popupDetail = MutableStateFlow<PopupDetailUiModel?>(null)
    val popupDetail: StateFlow<PopupDetailUiModel?> = _popupDetail.asStateFlow()
    private val _patchNotesPopup = MutableStateFlow<PatchNotesUiModel?>(null)
    val patchNotesPopup: StateFlow<PatchNotesUiModel?> = _patchNotesPopup.asStateFlow()
    private val _progressAlert = MutableStateFlow(false)
    val progressAlert: StateFlow<Boolean> = _progressAlert.asStateFlow()
    private val preferences = application.getSharedPreferences("android_ui", Context.MODE_PRIVATE)
    private val _darkTheme = MutableStateFlow(preferences.getBoolean("dark_theme", true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()
    private val _uiScale = MutableStateFlow(
        GameUiScale.fromStorageKey(preferences.getString("ui_scale", GameUiScale.default.storageKey))
    )
    val uiScale: StateFlow<GameUiScale> = _uiScale.asStateFlow()

    private var runtime: RuntimeDeps? = null
    private var session = GameSession()
    private var startMessage: String? = null
    private var startPageSaves: List<SaveSlotUi> = emptyList()
    private var raceClassSelection: RaceClassSelectionDraft? = null
    private var creationAttributeDraft: MutableMap<String, Int>? = null
    private var characterAttributePending: MutableMap<String, Int> = mutableMapOf()
    private var sellQuantityState: SellQuantityState? = null
    private var attributeContext: AttributeContext? = null

    private var combatController: AndroidCombatFlowController? = null
    private var combatUiJob: Job? = null
    private var combatRunJob: Job? = null
    private var timedActionJob: Job? = null
    private var autosaveJob: Job? = null
    private var passiveTickJob: Job? = null
    private var lastElapsedRealtimeMs: Long = 0L
    private var lastPassiveAutosaveMs: Long = 0L

    init {
        viewModelScope.launch {
            bootstrapRuntime()
        }
    }

    fun startNewGame() = applyAction(GameAction.StartNewGame)

    fun loadGame() {
        openLoadList()
    }

    fun openLoadList() {
        val deps = runtime ?: return
        startPageSaves = querySaveSlots(deps)
        if (startPageSaves.isEmpty()) {
            startMessage = "Nenhum save encontrado."
        } else {
            startMessage = null
        }
        publishFromSession()
    }

    fun loadSelectedSave(fileName: String) {
        val deps = runtime ?: return
        val selected = deps.saveGateway.listSaves()
            .firstOrNull { it.fileName.toString().equals(fileName, ignoreCase = true) }
        if (selected == null) {
            startMessage = "Save nao encontrado: $fileName"
            startPageSaves = querySaveSlots(deps)
            publishFromSession()
            return
        }
        applyAction(GameAction.LoadSave(selected))
    }

    fun onMenuAction(action: GameAction) = applyAction(action)

    fun onCreationNameChanged(name: String) = applyAction(GameAction.SetCharacterCreationName(name))

    fun openRaceClassSelection() {
        val deps = runtime ?: return
        val draft = session.creationDraft ?: deps.creationQueryService.initialDraft()
        raceClassSelection = RaceClassSelectionDraft(draft.raceId, draft.classId)
        publishFromSession()
    }

    fun onRaceSelected(raceId: String) {
        raceClassSelection = raceClassSelection?.copy(raceId = raceId)
        publishFromSession()
    }

    fun onClassSelected(classId: String) {
        raceClassSelection = raceClassSelection?.copy(classId = classId)
        publishFromSession()
    }

    fun confirmRaceClassSelection() {
        val draft = raceClassSelection ?: return
        draft.raceId?.let {
            applyAction(GameAction.SelectCharacterCreationRace(it), publishNow = false)
            applyAction(GameAction.ConfirmCharacterCreationRace, publishNow = false)
        }
        draft.classId?.let {
            applyAction(GameAction.SelectCharacterCreationClass(it), publishNow = false)
            applyAction(GameAction.ConfirmCharacterCreationClass, publishNow = false)
        }
        raceClassSelection = null
        publishFromSession()
    }

    fun cancelRaceClassSelection() {
        raceClassSelection = null
        publishFromSession()
    }

    fun openCreationAttributeDistribution() {
        val deps = runtime ?: return
        val draft = session.creationDraft ?: deps.creationQueryService.initialDraft()
        creationAttributeDraft = deps.creationQueryService.attributeRows(draft)
            .associate { it.code to it.allocated }
            .toMutableMap()
        attributeContext = AttributeContext.CREATION
        publishFromSession()
    }

    fun openCharacterAttributes() {
        applyAction(GameAction.OpenAttributes)
        val deps = runtime ?: return
        val state = session.gameState ?: return
        characterAttributePending = deps.characterQueryService.attributeRows(state)
            .associate { it.code to 0 }
            .toMutableMap()
        attributeContext = AttributeContext.CHARACTER
        publishFromSession()
    }

    fun onAttributeIncrease(code: String) {
        when (attributeContext) {
            AttributeContext.CREATION -> adjustCreationAttribute(code, +1)
            AttributeContext.CHARACTER -> adjustCharacterAttribute(code, +1)
            null -> Unit
        }
    }

    fun onAttributeDecrease(code: String) {
        when (attributeContext) {
            AttributeContext.CREATION -> adjustCreationAttribute(code, -1)
            AttributeContext.CHARACTER -> adjustCharacterAttribute(code, -1)
            null -> Unit
        }
    }

    fun confirmAttributeDistribution() {
        when (attributeContext) {
            AttributeContext.CREATION -> {
                val pending = creationAttributeDraft ?: return
                pending.forEach { (code, allocated) ->
                    applyAction(GameAction.SetCharacterCreationAttribute(code, allocated), publishNow = false)
                }
                session = session.copy(
                    navigation = NavigationState.CharacterCreation,
                    selectedAttributeCode = null
                )
                creationAttributeDraft = null
                attributeContext = null
                publishFromSession()
            }

            AttributeContext.CHARACTER -> applyCharacterAttributes()
            null -> Unit
        }
    }

    fun cancelAttributeDistribution() {
        if (attributeContext == AttributeContext.CHARACTER && session.navigation == NavigationState.Attributes) {
            applyAction(GameAction.Back)
        }
        creationAttributeDraft = null
        characterAttributePending.clear()
        attributeContext = null
        publishFromSession()
    }

    fun confirmCreation() = applyAction(GameAction.ConfirmCharacterCreation)

    fun cancelCreation() = applyAction(GameAction.Back)

    fun openCharacter() = applyAction(GameAction.OpenCharacterMenu)
    fun openProduction() = applyAction(GameAction.OpenProductionMenu)
    fun openExplore() = applyAction(GameAction.OpenExploration)
    fun openHub() {
        if (session.gameState == null) return
        if (session.navigation == NavigationState.Hub) {
            publishFromSession()
            return
        }
        var guard = 0
        while (session.navigation != NavigationState.Hub && guard < 12) {
            val before = session.navigation
            applyAction(GameAction.Back, publishNow = false)
            guard += 1
            if (session.navigation == before) break
        }
        publishFromSession()
    }
    fun openCity() = applyAction(GameAction.OpenCityMenu)
    fun openProgression() = applyAction(GameAction.OpenProgressionMenu)
    fun openTalents() = applyAction(GameAction.OpenTalents)
    fun openGlobalBoss() = applyAction(GameAction.OpenGlobalBossMenu)

    fun onCharacterSlotTapped(slotKey: String) = applyAction(GameAction.InspectEquippedSlot(slotKey))
    fun onCharacterInventoryItemTapped(itemId: String) {
        sellQuantityState = null
        applyAction(GameAction.InspectInventoryItem(itemId))
    }

    fun clearPopup() {
        if (session.navigation == NavigationState.InventoryItemDetail || session.navigation == NavigationState.EquippedItemDetail) {
            sellQuantityState = null
            applyAction(GameAction.Back)
        } else {
            sellQuantityState = null
            _popupDetail.value = null
        }
    }

    fun dismissPatchNotesPopup() {
        val currentPopup = _patchNotesPopup.value
        if (currentPopup?.markSeenOnDismiss == true) {
            preferences.edit().putString(PREF_LAST_SEEN_PATCH_VERSION, currentPopup.versionLabel).apply()
        }
        _patchNotesPopup.value = null
    }

    fun openPatchNotesFromSettings() {
        val deps = runtime ?: return
        val current = deps.patchNotesService.currentEntry() ?: return
        _patchNotesPopup.value = PatchNotesUiModel(
            title = "Notas da atualizacao",
            versionLabel = current.version,
            dateLabel = current.date.takeIf { it.isNotBlank() },
            novidades = current.novidades,
            melhorias = current.melhorias,
            correcoes = current.correcoes,
            markSeenOnDismiss = true
        )
    }

    fun onCombatAttack() = combatController?.submitAttack()
    fun onCombatEscape() = combatController?.submitEscape()
    fun onCombatUseItem(itemId: String) = combatController?.submitUseItem(itemId)
    fun clearHubInfo() {
        session = session.copy(messages = emptyList())
        publishFromSession()
    }
    fun toggleTheme() {
        val next = !_darkTheme.value
        _darkTheme.value = next
        preferences.edit().putBoolean("dark_theme", next).apply()
    }

    fun setUiScale(scale: GameUiScale) {
        if (_uiScale.value == scale) return
        _uiScale.value = scale
        preferences.edit().putString("ui_scale", scale.storageKey).apply()
    }

    fun cancelTimedAction() {
        if (timedActionJob?.isActive != true) {
            _timedActionState.value = null
            return
        }
        timedActionJob?.cancel()
        timedActionJob = null
        _timedActionState.value = null
        session = session.copy(messages = listOf("Acao cancelada."))
        publishFromSession()
    }

    fun onAppBackgrounded() {
        requestAutosave(immediate = true)
    }

    private suspend fun bootstrapRuntime() {
        try {
            val deps = createAndroidRuntimeDeps(getApplication())
            runtime = deps
            startPassiveTicking()
            startPageSaves = querySaveSlots(deps)
            _uiState.value = AndroidUiState.StartPage(
                StartPageUiModel(
                    canLoad = startPageSaves.isNotEmpty(),
                    saves = startPageSaves
                )
            )
            _progressAlert.value = false
        } catch (error: Exception) {
            _uiState.value = AndroidUiState.Error("Falha ao iniciar no Android: ${error.message ?: "erro desconhecido"}")
            _progressAlert.value = false
        }
    }

    private fun applyAction(action: GameAction, publishNow: Boolean = true) {
        val deps = runtime ?: return
        val beforeState = session.gameState
        val result = deps.actionHandler.handle(session, action)
        session = result.session
        handleEffect(result.effect)
        if (beforeState != session.gameState) {
            requestAutosave()
        }
        if (publishNow) {
            publishFromSession()
        }
    }

    private fun handleEffect(effect: GameEffect) {
        when (effect) {
            GameEffect.None -> Unit
            is GameEffect.LaunchCombat -> startCombat(effect)
            is GameEffect.LaunchProductionTimedAction -> startTimedAction(effect)
        }
    }

    private fun startTimedAction(effect: GameEffect.LaunchProductionTimedAction) {
        timedActionJob?.cancel()
        requestAutosave(immediate = true)
        timedActionJob = viewModelScope.launch {
            val totalMs = (effect.view.durationSeconds.coerceAtLeast(0.5) * 1000.0).toLong()
            val stepMs = 120L
            val totalSteps = (totalMs / stepMs).coerceAtLeast(1L).toInt()
            for (step in 0..totalSteps) {
                val progress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                val remainingMs = (totalMs - step * stepMs).coerceAtLeast(0L)
                _timedActionState.value = TimedActionUiState(
                    title = effect.view.categoryLabel,
                    detail = "${effect.view.actionLabel} | ${effect.view.skillLabel} ${effect.view.skillLevel}",
                    remainingSeconds = ((remainingMs + 999L) / 1000L).toInt(),
                    progress = progress
                )
                if (step < totalSteps) delay(stepMs)
            }
            _timedActionState.value = null
            applyAction(effect.completionAction)
            requestAutosave(immediate = true)
        }
    }

    private fun startCombat(effect: GameEffect.LaunchCombat) {
        val deps = runtime ?: return
        combatUiJob?.cancel(); combatRunJob?.cancel()
        val controller = AndroidCombatFlowController(
            engine = deps.actionHandler.engine(),
            outcomeResolver = AndroidCombatOutcomeResolver(
                engine = deps.actionHandler.engine(),
                applyBattleResolvedAchievement = deps.actionHandler::applyBattleResolvedAchievement,
                applyGoldEarnedAchievement = deps.actionHandler::applyGoldEarnedAchievement,
                applyDeathAchievement = deps.actionHandler::applyDeathAchievement
            ),
            resolveGlobalBossCombat = deps.actionHandler::resolveGlobalBossCombat
        )
        combatController = controller
        combatUiJob = viewModelScope.launch {
            controller.uiState.collectLatest { _uiState.value = AndroidUiState.Combat(it) }
        }
        combatRunJob = viewModelScope.launch(Dispatchers.Default) {
            val state = session.gameState ?: return@launch
            val outcome = controller.run(state, effect.encounter)
            withContext(Dispatchers.Main) {
                combatUiJob?.cancel(); combatController = null
                session = deps.actionHandler.applyCombatResult(session, outcome)
                requestAutosave(immediate = true)
                publishFromSession()
            }
        }
    }

    private fun publishFromSession() {
        val deps = runtime ?: return
        val state = session.gameState
        _progressAlert.value = if (state == null) {
            false
        } else {
            deps.actionHandler.questQueryService().hasQuestAlert(state) ||
                deps.actionHandler.achievementQueryService().hasClaimableRewards(state)
        }
        _popupDetail.value = buildPopupDetail()
        refreshPatchNotesPopup(state)

        if (raceClassSelection != null) {
            _uiState.value = AndroidUiState.RaceClass(
                AndroidUiModelBuilders.buildRaceClassUi(
                    deps = deps,
                    draft = raceClassSelection
                )
            )
            return
        }
        if (attributeContext == AttributeContext.CREATION && creationAttributeDraft != null) {
            _uiState.value = AndroidUiState.AttributeDistribution(
                AndroidUiModelBuilders.buildCreationAttributeUi(
                    session = session,
                    deps = deps,
                    creationAttributeDraft = creationAttributeDraft.orEmpty()
                )
            )
            return
        }
        if (attributeContext == AttributeContext.CHARACTER && state != null && session.navigation == NavigationState.Attributes) {
            _uiState.value = AndroidUiState.AttributeDistribution(
                AndroidUiModelBuilders.buildCharacterAttributeUi(
                    session = session,
                    state = state,
                    deps = deps,
                    characterAttributePending = characterAttributePending
                )
            )
            return
        }

        when (session.navigation) {
            NavigationState.MainMenu -> _uiState.value = AndroidUiState.StartPage(
                StartPageUiModel(
                    canLoad = startPageSaves.isNotEmpty(),
                    message = startMessage ?: session.messages.firstOrNull(),
                    saves = startPageSaves
                )
            )

            NavigationState.CharacterCreation -> _uiState.value = AndroidUiState.NewGame(
                AndroidUiModelBuilders.buildNewGameUi(session = session, deps = deps)
            )

            NavigationState.Hub -> _uiState.value = AndroidUiState.MainHub(
                AndroidUiModelBuilders.buildHubUi(
                    state = state,
                    messages = session.messages,
                    deps = deps
                )
            )

            NavigationState.CharacterMenu,
            NavigationState.Inventory,
            NavigationState.Equipped,
            NavigationState.InventoryItemDetail,
            NavigationState.EquippedItemDetail,
            NavigationState.Quiver -> if (state != null) {
                _uiState.value = AndroidUiState.Character(
                    AndroidUiModelBuilders.buildCharacterUi(
                        session = session,
                        state = state,
                        deps = deps
                    )
                )
            }

            else -> {
                val view = deps.presenter.present(session)
                _uiState.value = if (view is MenuScreenViewModel) {
                    AndroidUiState.GenericMenu(
                        viewModel = view,
                        section = sectionForNavigation(session.navigation),
                        actionPreviews = buildMenuActionPreviews(
                            session = session,
                            viewModel = view,
                            deps = deps
                        ),
                        talentTreeGraph = if (state != null && session.navigation == NavigationState.TalentTreeDetail) {
                            buildTalentTreeGraphUi(session = session, state = state, deps = deps)
                        } else {
                            null
                        }
                    )
                } else {
                    AndroidUiState.Error("Tela ainda nao suportada no Android.")
                }
            }
        }
    }

    private fun buildPopupDetail(): PopupDetailUiModel? {
        val deps = runtime ?: return null
        if (session.navigation == NavigationState.InventoryItemDetail) {
            val state = session.gameState
            val itemId = session.selectedInventoryItemId
            if (state != null && itemId != null) {
                val detail = deps.inventoryQueryService.inventoryItemDetail(state, itemId)
                if (detail != null) {
                    val current = sellQuantityState
                    sellQuantityState = if (current == null || current.itemId != itemId) {
                        SellQuantityState(
                            itemId = itemId,
                            quantity = 1,
                            maxQuantity = detail.quantity.coerceAtLeast(1),
                            unitValue = detail.saleValue
                        )
                    } else {
                        current.copy(
                            maxQuantity = detail.quantity.coerceAtLeast(1),
                            unitValue = detail.saleValue,
                            quantity = current.quantity.coerceIn(1, detail.quantity.coerceAtLeast(1))
                        )
                    }
                }
            }
        } else {
            sellQuantityState = null
        }
        return buildAndroidPopupDetail(
            session = session,
            deps = deps,
            onUseItem = { itemId -> applyAction(GameAction.UseInventoryItem(itemId)) },
            onEquipItem = { itemId -> applyAction(GameAction.EquipInventoryItem(itemId)) },
            onSell = { itemId, quantity -> sellInventoryQuantity(itemId, quantity) },
            onUnequip = { slot -> applyAction(GameAction.UnequipSlot(slot)) },
            sellQuantityState = sellQuantityState,
            onDecreaseSellQuantity = ::decreaseSellQuantity,
            onIncreaseSellQuantity = ::increaseSellQuantity
        )
    }

    private fun refreshPatchNotesPopup(state: rpg.model.GameState?) {
        if (state == null) {
            _patchNotesPopup.value = null
            return
        }
        if (session.navigation != NavigationState.Hub) return
        if (_patchNotesPopup.value != null) return
        val deps = runtime ?: return
        val lastSeenVersion = preferences.getString(PREF_LAST_SEEN_PATCH_VERSION, null)
        val pending = deps.patchNotesService.nextEntryToShow(lastSeenVersion) ?: return
        _patchNotesPopup.value = PatchNotesUiModel(
            title = "Notas da atualizacao",
            versionLabel = pending.version,
            dateLabel = pending.date.takeIf { it.isNotBlank() },
            novidades = pending.novidades,
            melhorias = pending.melhorias,
            correcoes = pending.correcoes,
            markSeenOnDismiss = true
        )
    }

    private fun decreaseSellQuantity() {
        val current = sellQuantityState ?: return
        if (current.quantity <= 1) return
        sellQuantityState = current.copy(quantity = current.quantity - 1)
        publishFromSession()
    }

    private fun increaseSellQuantity() {
        val current = sellQuantityState ?: return
        if (current.quantity >= current.maxQuantity) return
        sellQuantityState = current.copy(quantity = current.quantity + 1)
        publishFromSession()
    }

    private fun sellInventoryQuantity(itemId: String, quantity: Int) {
        val stateBefore = session.gameState ?: return
        val beforeGold = stateBefore.player.gold
        val safeQty = quantity.coerceAtLeast(1)
        val stackIds = runtime
            ?.inventoryQueryService
            ?.inventoryStacks(stateBefore, session.inventoryFilter)
            ?.firstOrNull { it.sampleItemId == itemId }
            ?.itemIds
            .orEmpty()
            .take(safeQty)
        stackIds.forEach { stackItemId ->
            applyAction(GameAction.SellInventoryItem(stackItemId), publishNow = false)
        }
        val afterGold = session.gameState?.player?.gold ?: beforeGold
        val gained = (afterGold - beforeGold).coerceAtLeast(0)
        if (gained > 0) {
            session = session.copy(messages = listOf("Venda concluida: +$gained ouro."))
        }
        sellQuantityState = null
        publishFromSession()
    }

    private fun adjustCreationAttribute(code: String, delta: Int) {
        val deps = runtime ?: return
        val draft = session.creationDraft ?: deps.creationQueryService.initialDraft()
        val map = creationAttributeDraft ?: return
        val current = map[code] ?: 0
        if (delta < 0 && current <= 0) return
        val remaining = (draft.totalPoints - map.values.sum()).coerceAtLeast(0)
        if (delta > 0 && remaining <= 0) return
        map[code] = (current + delta).coerceAtLeast(0)
        publishFromSession()
    }

    private fun adjustCharacterAttribute(code: String, delta: Int) {
        val state = session.gameState ?: return
        val current = characterAttributePending[code] ?: 0
        if (delta < 0 && current <= 0) return
        val remaining = (state.player.unspentAttrPoints - characterAttributePending.values.sum()).coerceAtLeast(0)
        if (delta > 0 && remaining <= 0) return
        characterAttributePending[code] = (current + delta).coerceAtLeast(0)
        publishFromSession()
    }

    private fun applyCharacterAttributes() {
        val deps = runtime ?: return
        val state = session.gameState ?: return
        if (characterAttributePending.values.sum() <= 0) return
        val rows = deps.characterQueryService.attributeRows(state)
        val targets = rows.associate { row -> row.code to (row.baseValue + (characterAttributePending[row.code] ?: 0)) }
        val mutation = deps.characterCommandBridge.applyAttributes(state, targets)
        session = session.copy(gameState = mutation.state, messages = mutation.messages)
        characterAttributePending.clear()
        attributeContext = null
        applyAction(GameAction.Back, publishNow = false)
        requestAutosave(immediate = true)
        publishFromSession()
    }

    private fun requestAutosave(immediate: Boolean = false) {
        val deps = runtime ?: return
        val state = session.gameState ?: return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            if (!immediate) delay(300)
            val currentPath = resolveAutosavePath(state.player.name, deps)
            val savedPath = withContext(Dispatchers.IO) {
                deps.saveGateway.save(currentPath, state)
            }
            session = session.copy(
                currentSavePath = savedPath,
                currentSaveName = savedPath.fileName.toString()
            )
        }
    }

    private fun startPassiveTicking() {
        if (passiveTickJob?.isActive == true) return
        lastElapsedRealtimeMs = SystemClock.elapsedRealtime()
        passiveTickJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                val now = SystemClock.elapsedRealtime()
                val deltaMs = (now - lastElapsedRealtimeMs).coerceAtLeast(0L)
                lastElapsedRealtimeMs = now
                applyPassiveOutOfCombatProgress(deltaMs = deltaMs, nowMs = now)
            }
        }
    }

    private fun applyPassiveOutOfCombatProgress(deltaMs: Long, nowMs: Long) {
        if (deltaMs <= 0L) return
        if (timedActionJob?.isActive == true) return
        if (session.navigation == NavigationState.Combat) return
        val deps = runtime ?: return
        val state = session.gameState ?: return
        val minutes = deltaMs.toDouble() / 60_000.0
        if (minutes <= 0.0) return

        val advance = OutOfCombatTimeService(deps.actionHandler.engine())
            .advance(state.player, state.itemInstances, minutes)
        if (advance.player == state.player && advance.messages.isEmpty()) return

        val mergedMessages = if (advance.messages.isEmpty()) {
            session.messages
        } else {
            (session.messages + advance.messages).takeLast(12)
        }
        session = session.copy(
            gameState = state.copy(player = advance.player),
            messages = mergedMessages
        )
        publishFromSession()

        val hpChanged = advance.player.currentHp != state.player.currentHp || advance.player.currentMp != state.player.currentMp
        if (hpChanged && (nowMs - lastPassiveAutosaveMs) >= 30_000L) {
            lastPassiveAutosaveMs = nowMs
            requestAutosave()
        }
    }

    override fun onCleared() {
        super.onCleared()
        passiveTickJob?.cancel()
    }

    private fun resolveAutosavePath(characterName: String, deps: RuntimeDeps): Path {
        val current = session.currentSavePath
        if (current != null && !isLegacyAutosaveFile(current.fileName.toString())) {
            return current
        }
        val base = sanitizeSaveName(characterName)
        val preferredFileName = "$base.json"
        val existing = deps.saveGateway.listSaves()
        val selected = existing.firstOrNull { path ->
            val file = path.fileName.toString()
            if (file.equals(preferredFileName, ignoreCase = true)) {
                true
            } else {
                val stem = file.removeSuffix(".json").lowercase()
                stem == base || stem.startsWith("${base}_")
            }
        } ?: deps.saveGateway.resolveSaveFile(preferredFileName)

        if (current != null && isLegacyAutosaveFile(current.fileName.toString()) && current != selected) {
            runCatching { Files.deleteIfExists(current) }
        }
        session = session.copy(
            currentSavePath = selected,
            currentSaveName = selected.fileName.toString()
        )
        return selected
    }

    private fun sanitizeSaveName(name: String): String {
        val normalized = name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return normalized.ifBlank { "personagem" }
    }

    private fun isLegacyAutosaveFile(fileName: String): Boolean {
        val normalized = fileName.lowercase()
        return normalized == "autosave.json" || normalized.startsWith("autosave_")
    }

    private fun querySaveSlots(deps: RuntimeDeps): List<SaveSlotUi> {
        return deps.saveGateway.listSaves()
            .filterNot { isLegacyAutosaveFile(it.fileName.toString()) }
            .map { path ->
            val state = runCatching { deps.saveGateway.load(path) }.getOrNull()
            val characterName = state?.player?.name?.takeIf { it.isNotBlank() } ?: path.fileName.toString().removeSuffix(".json")
            SaveSlotUi(
                fileName = path.fileName.toString(),
                characterName = characterName
            )
        }.sortedBy { it.characterName.lowercase() }
    }

    companion object {
        private const val PREF_LAST_SEEN_PATCH_VERSION = "last_seen_patch_version"

        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AndroidGameViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return AndroidGameViewModel(application) as T
                    }
                    error("ViewModel nao suportado: ${modelClass.name}")
                }
            }
        }
    }
}
