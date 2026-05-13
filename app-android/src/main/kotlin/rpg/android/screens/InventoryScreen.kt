package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun InventoryScreen(
    items: List<String>,
    onBack: () -> Unit
) {
    MenuLayout(title = "Inventário") {
        if (items.isEmpty()) {
            androidx.compose.material3.Text("Inventário vazio.")
        } else {
            items.forEach { item ->
                androidx.compose.material3.Text("- $item")
            }
        }
        MenuButton("VOLTAR", onBack)
    }
}

