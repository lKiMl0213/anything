package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun TalentsScreen(onBack: () -> Unit) {
    MenuLayout(title = "Talentos") {
        androidx.compose.material3.Text("Arvore de talentos (placeholder).")
        MenuButton("VOLTAR", onBack)
    }
}
