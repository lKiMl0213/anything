package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun QuestsScreen(onBack: () -> Unit) {
    MenuLayout(title = "Quests") {
        androidx.compose.material3.Text("Lista de quests (placeholder).")
        MenuButton("VOLTAR", onBack)
    }
}
