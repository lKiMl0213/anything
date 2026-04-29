package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun ExplorationScreen(
    onDungeon: () -> Unit,
    onOpenMap: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Exploracao") {
        MenuButton("DUNGEON", onDungeon)
        MenuButton("MAPA ABERTO", onOpenMap)
        MenuButton("VOLTAR", onBack)
    }
}
