package rpg.android

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rpg.android.screens.AppMenuScreen
import rpg.android.screens.AttributesTouchScreen
import rpg.android.screens.CombatTouchScreen
import rpg.android.state.AndroidUiState
import rpg.application.actions.GameAction

@Composable
fun AndroidGameApp() {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: AndroidGameViewModel = viewModel(
        factory = AndroidGameViewModel.factory(app)
    )
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        AndroidUiState.Loading -> SimpleTextScreen("Carregando dados do jogo...")
        is AndroidUiState.Error -> SimpleTextScreen(state.message)
        is AndroidUiState.Menu -> AppMenuScreen(
            model = state.viewModel,
            onSelectAction = viewModel::onMenuAction
        )
        is AndroidUiState.Attributes -> AttributesTouchScreen(
            state = state.state,
            onIncrease = viewModel::onAttributesIncrease,
            onDecrease = viewModel::onAttributesDecrease,
            onApply = viewModel::onAttributesApply,
            onBack = { viewModel.onMenuAction(GameAction.Back) }
        )
        is AndroidUiState.Combat -> CombatTouchScreen(
            state = state.state,
            onAttack = viewModel::onCombatAttack,
            onEscape = viewModel::onCombatEscape,
            onUseItem = viewModel::onCombatUseItem
        )
    }
}

@Composable
private fun SimpleTextScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(message)
    }
}
