package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    MenuLayout(title = "Conquistas") {
        androidx.compose.material3.Text("Conquistas (placeholder).")
        MenuButton("VOLTAR", onBack)
    }
}
