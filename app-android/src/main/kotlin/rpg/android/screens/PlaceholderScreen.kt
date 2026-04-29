package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    backLabel: String = "VOLTAR",
    onBack: () -> Unit
) {
    MenuLayout(title = title) {
        androidx.compose.material3.Text(message)
        MenuButton(backLabel, onBack)
    }
}
