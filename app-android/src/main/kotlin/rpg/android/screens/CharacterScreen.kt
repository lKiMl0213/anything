package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun CharacterScreen(
    onEquipments: () -> Unit,
    onInventory: () -> Unit,
    onAttributes: () -> Unit,
    onTalents: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Personagem") {
        MenuButton("EQUIPAMENTOS", onEquipments)
        MenuButton("INVENTARIO", onInventory)
        MenuButton("ATRIBUTOS", onAttributes)
        MenuButton("TALENTOS", onTalents)
        MenuButton("VOLTAR", onBack)
    }
}
