package rpg.android.screens

import androidx.compose.runtime.Composable
import rpg.android.components.MenuButton
import rpg.android.components.MenuLayout

@Composable
fun HubScreen(
    onExploration: () -> Unit,
    onCharacter: () -> Unit,
    onProduction: () -> Unit,
    onProgression: () -> Unit,
    onCity: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    MenuLayout(title = "Hub Principal") {
        MenuButton("EXPLORAR", onExploration)
        MenuButton("PERSONAGEM", onCharacter)
        MenuButton("PRODUCAO", onProduction)
        MenuButton("PROGRESSAO", onProgression)
        MenuButton("CIDADE", onCity)
        MenuButton("SALVAR", onSave)
        MenuButton("VOLTAR", onBack)
    }
}
