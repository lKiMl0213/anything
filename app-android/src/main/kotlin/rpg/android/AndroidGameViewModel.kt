package rpg.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rpg.android.application.CharacterCommandBridge
import rpg.android.combat.AndroidCombatFlowController
import rpg.android.platform.AndroidDataBootstrap
import rpg.android.state.AndroidUiState
import rpg.android.state.AndroidStateBuilders
import rpg.application.GameActionHandler
import rpg.application.GameEffect
import rpg.application.GameSession
import rpg.application.SaveGameGateway
import rpg.application.actions.GameAction
import rpg.application.character.CharacterCommandService
import rpg.application.character.CharacterQueryService
import rpg.application.character.CharacterRulesSupport
import rpg.io.DataRepository
import rpg.navigation.NavigationState
import rpg.presentation.GamePresenter
import rpg.presentation.model.MenuScreenViewModel

class AndroidGameViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<AndroidUiState>(AndroidUiState.Loading)
    val uiState: StateFlow<AndroidUiState> = _uiState.asStateFlow()

    private var runtime: RuntimeDeps? = null
    private var session = GameSession()
    private var attributePending: Map<String, Int> = emptyMap()

    private var combatController: AndroidCombatFlowController? = null
    private var combatUiJob: Job? = null
    private var combatRunJob: Job? = null

    init {
        viewModelScope.launch {
            bootstrapRuntime()
        }
    }

    fun onMenuAction(action: GameAction) {
        val deps = runtime ?: return
        attributePending = emptyMap()
        val result = deps.actionHandler.handle(session, action)
        session = result.session
        handleEffect(result.effect)
    }

    fun onAttributesIncrease(code: String) {
        val state = session.gameState ?: return
        if (state.player.unspentAttrPoints - attributePending.values.sum() <= 0) return
        val next = attributePending.toMutableMap()
        next[code] = (next[code] ?: 0) + 1
        attributePending = next
        publishAttributesState()
    }

    fun onAttributesDecrease(code: String) {
        val current = attributePending[code] ?: 0
        if (current <= 0) return
        val next = attributePending.toMutableMap()
        next[code] = current - 1
        attributePending = next
        publishAttributesState()
    }

    fun onAttributesApply() {
        val deps = runtime ?: return
        val state = session.gameState ?: return
        if (attributePending.values.sum() <= 0) return
        val rows = deps.characterQueryService.attributeRows(state)
        val targets = rows.associate { row ->
            val delta = attributePending[row.code] ?: 0
            row.code to (row.baseValue + delta)
        }
        val mutation = deps.characterCommandBridge.applyAttributes(state, targets)
        session = session.copy(
            gameState = mutation.state,
            navigation = NavigationState.Attributes,
            messages = mutation.messages
        )
        attributePending = emptyMap()
        publishAttributesState()
    }

    fun onCombatAttack() {
        combatController?.submitAttack()
    }

    fun onCombatEscape() {
        combatController?.submitEscape()
    }

    fun onCombatUseItem(itemId: String) {
        combatController?.submitUseItem(itemId)
    }

    private suspend fun bootstrapRuntime() {
        try {
            val deps = withContext(Dispatchers.IO) {
                val paths = AndroidDataBootstrap.prepare(getApplication())
                val repo = DataRepository(paths.dataRoot)
                val actionHandler = GameActionHandler(
                    repo = repo,
                    saveGateway = SaveGameGateway(paths.savesRoot)
                )
                val presenter = GamePresenter(
                    engine = actionHandler.engine(),
                    creationQueryService = actionHandler.creationQueryService(),
                    inventoryQueryService = actionHandler.inventoryQueryService(),
                    characterQueryService = actionHandler.characterQueryService(),
                    questQueryService = actionHandler.questQueryService(),
                    achievementQueryService = actionHandler.achievementQueryService(),
                    cityQueryService = actionHandler.cityQueryService(),
                    productionQueryService = actionHandler.productionQueryService(),
                    shopQueryService = actionHandler.shopQueryService()
                )
                val characterSupport = CharacterRulesSupport(repo, actionHandler.engine())
                RuntimeDeps(
                    actionHandler = actionHandler,
                    presenter = presenter,
                    characterQueryService = actionHandler.characterQueryService(),
                    characterCommandBridge = CharacterCommandBridge(
                        CharacterCommandService(characterSupport)
                    )
                )
            }
            runtime = deps
            publishFromSession()
        } catch (error: Exception) {
            _uiState.value = AndroidUiState.Error(
                message = "Falha ao iniciar o jogo no Android: ${error.message ?: "erro desconhecido"}"
            )
        }
    }

    private fun handleEffect(effect: GameEffect) {
        when (effect) {
            GameEffect.None -> publishFromSession()
            is GameEffect.LaunchCombat -> startCombat(effect)
        }
    }

    private fun startCombat(effect: GameEffect.LaunchCombat) {
        val deps = runtime ?: return
        combatUiJob?.cancel()
        combatRunJob?.cancel()

        val controller = AndroidCombatFlowController(deps.actionHandler.engine())
        combatController = controller
        combatUiJob = viewModelScope.launch {
            controller.uiState.collectLatest { combatState ->
                _uiState.value = AndroidUiState.Combat(combatState)
            }
        }

        combatRunJob = viewModelScope.launch(Dispatchers.Default) {
            val state = session.gameState ?: return@launch
            val outcome = controller.run(state, effect.encounter)
            withContext(Dispatchers.Main) {
                combatUiJob?.cancel()
                combatController = null
                session = deps.actionHandler.applyCombatResult(session, outcome)
                publishFromSession()
            }
        }
    }

    private fun publishFromSession() {
        if (session.navigation == NavigationState.Attributes && session.gameState != null) {
            publishAttributesState()
            return
        }

        val deps = runtime ?: return
        val view = deps.presenter.present(session)
        _uiState.value = when (view) {
            is MenuScreenViewModel -> AndroidUiState.Menu(view)
            else -> AndroidUiState.Error("Tela ainda nao suportada no Android.")
        }
    }

    private fun publishAttributesState() {
        val deps = runtime ?: return
        val state = session.gameState ?: return
        val rows = deps.characterQueryService.attributeRows(state)
        attributePending = AndroidStateBuilders.ensurePending(rows, attributePending)
        _uiState.value = AndroidUiState.Attributes(
            AndroidStateBuilders.buildAttributesUiState(
                rows = rows,
                pending = attributePending,
                unspentPoints = state.player.unspentAttrPoints,
                messages = session.messages
            )
        )
    }

    private data class RuntimeDeps(
        val actionHandler: GameActionHandler,
        val presenter: GamePresenter,
        val characterQueryService: CharacterQueryService,
        val characterCommandBridge: CharacterCommandBridge
    )

    companion object {
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
