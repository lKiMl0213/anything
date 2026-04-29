package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun ProgressionScreen(
    onQuests: () -> Unit,
    onAchievements: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Progressao") {
        MenuButton("QUESTS", onQuests)
        MenuButton("CONQUISTAS", onAchievements)
        MenuButton("VOLTAR", onBack)
    }
}
