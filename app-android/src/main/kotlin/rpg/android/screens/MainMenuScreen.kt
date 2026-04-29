package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    onLoad: () -> Unit,
    onExit: () -> Unit
) {
    MenuLayout(title = "Main Menu") {
        MenuButton("NOVO JOGO", onNewGame)
        MenuButton("CARREGAR", onLoad)
        MenuButton("SAIR", onExit)
    }
}
