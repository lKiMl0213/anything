package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun CityScreen(
    onTavern: () -> Unit,
    onShop: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Cidade") {
        MenuButton("TAVERNA", onTavern)
        MenuButton("LOJA", onShop)
        MenuButton("VOLTAR", onBack)
    }
}
