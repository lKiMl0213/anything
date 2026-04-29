package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun InventoryScreen(
    items: List<String>,
    onBack: () -> Unit
) {
    MenuLayout(title = "Inventario") {
        if (items.isEmpty()) {
            androidx.compose.material3.Text("Inventario vazio.")
        } else {
            items.forEach { item ->
                androidx.compose.material3.Text("- $item")
            }
        }
        MenuButton("VOLTAR", onBack)
    }
}
