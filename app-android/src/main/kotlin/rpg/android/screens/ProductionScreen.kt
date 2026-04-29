package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun ProductionScreen(
    onCraft: () -> Unit,
    onGathering: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Producao") {
        MenuButton("CRAFT", onCraft)
        MenuButton("COLETA", onGathering)
        MenuButton("VOLTAR", onBack)
    }
}
