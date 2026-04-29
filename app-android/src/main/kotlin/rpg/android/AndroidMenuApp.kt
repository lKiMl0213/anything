package rpg.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import rpg.android.navigation.AppScreen
import rpg.android.screens.AchievementsScreen
import rpg.android.screens.AttributesScreen
import rpg.android.screens.CharacterCreationScreen
import rpg.android.screens.CharacterScreen
import rpg.android.screens.CityScreen
import rpg.android.screens.ExplorationScreen
import rpg.android.screens.HubScreen
import rpg.android.screens.InventoryScreen
import rpg.android.screens.MainMenuScreen
import rpg.android.screens.PlaceholderScreen
import rpg.android.screens.ProductionScreen
import rpg.android.screens.ProgressionScreen
import rpg.android.screens.QuestsScreen
import rpg.android.screens.TalentsScreen

private val defaultCreationAttributes = mapOf(
    "STR" to 5,
    "AGI" to 5,
    "DEX" to 5,
    "VIT" to 5,
    "INT" to 5,
    "SPR" to 5,
    "LUK" to 5
)

@Composable
fun AndroidMenuApp(
    onBatchApplyAttributes: (Map<String, Int>) -> Unit = {},
    onSaveGame: () -> Unit = {}
) {
    var screen by remember { mutableStateOf(AppScreen.MAIN_MENU) }
    var currentAttributes by remember { mutableStateOf(defaultCreationAttributes) }
    var availableAttributePoints by remember { mutableStateOf(5) }
    var inventoryItems by remember {
        mutableStateOf(
            listOf(
                "Pocao de Vida",
                "Pocao de Mana",
                "Flecha de Madeira x10"
            )
        )
    }

    when (screen) {
        AppScreen.MAIN_MENU -> MainMenuScreen(
            onNewGame = { screen = AppScreen.CHARACTER_CREATION },
            onLoad = { screen = AppScreen.HUB },
            onExit = { screen = AppScreen.EXIT }
        )

        AppScreen.CHARACTER_CREATION -> CharacterCreationScreen(
            initialAttributes = defaultCreationAttributes,
            initialPool = 35,
            onConfirm = { finalAttributes ->
                onBatchApplyAttributes(finalAttributes)
                currentAttributes = finalAttributes
                availableAttributePoints = 0
                screen = AppScreen.HUB
            },
            onBack = { screen = AppScreen.MAIN_MENU }
        )

        AppScreen.HUB -> HubScreen(
            onExploration = { screen = AppScreen.EXPLORATION },
            onCharacter = { screen = AppScreen.CHARACTER },
            onProduction = { screen = AppScreen.PRODUCTION },
            onProgression = { screen = AppScreen.PROGRESSION },
            onCity = { screen = AppScreen.CITY },
            onSave = onSaveGame,
            onBack = { screen = AppScreen.MAIN_MENU }
        )

        AppScreen.EXPLORATION -> ExplorationScreen(
            onDungeon = { screen = AppScreen.DUNGEON },
            onOpenMap = { screen = AppScreen.MAP_OPEN },
            onBack = { screen = AppScreen.HUB }
        )

        AppScreen.CHARACTER -> CharacterScreen(
            onEquipments = { screen = AppScreen.EQUIPMENTS },
            onInventory = { screen = AppScreen.INVENTORY },
            onAttributes = { screen = AppScreen.ATTRIBUTES },
            onTalents = { screen = AppScreen.TALENTS },
            onBack = { screen = AppScreen.HUB }
        )

        AppScreen.PRODUCTION -> ProductionScreen(
            onCraft = { screen = AppScreen.CRAFT },
            onGathering = { screen = AppScreen.GATHERING },
            onBack = { screen = AppScreen.HUB }
        )

        AppScreen.PROGRESSION -> ProgressionScreen(
            onQuests = { screen = AppScreen.QUESTS },
            onAchievements = { screen = AppScreen.ACHIEVEMENTS },
            onBack = { screen = AppScreen.HUB }
        )

        AppScreen.CITY -> CityScreen(
            onTavern = { screen = AppScreen.TAVERN },
            onShop = { screen = AppScreen.SHOP },
            onBack = { screen = AppScreen.HUB }
        )

        AppScreen.INVENTORY -> InventoryScreen(
            items = inventoryItems,
            onBack = { screen = AppScreen.CHARACTER }
        )

        AppScreen.ATTRIBUTES -> AttributesScreen(
            currentAttributes = currentAttributes,
            availablePoints = availableAttributePoints,
            onApply = { finalAttributes, spent ->
                if (spent > 0) {
                    onBatchApplyAttributes(finalAttributes)
                    currentAttributes = finalAttributes
                    availableAttributePoints = (availableAttributePoints - spent).coerceAtLeast(0)
                }
            },
            onBack = { screen = AppScreen.CHARACTER }
        )

        AppScreen.TALENTS -> TalentsScreen(
            onBack = { screen = AppScreen.CHARACTER }
        )

        AppScreen.QUESTS -> QuestsScreen(
            onBack = { screen = AppScreen.PROGRESSION }
        )

        AppScreen.ACHIEVEMENTS -> AchievementsScreen(
            onBack = { screen = AppScreen.PROGRESSION }
        )

        AppScreen.EQUIPMENTS -> PlaceholderScreen(
            title = "Equipamentos",
            message = "Tela de equipamentos (placeholder).",
            onBack = { screen = AppScreen.CHARACTER }
        )

        AppScreen.CRAFT -> PlaceholderScreen(
            title = "Craft",
            message = "Tela de craft (placeholder).",
            onBack = { screen = AppScreen.PRODUCTION }
        )

        AppScreen.GATHERING -> PlaceholderScreen(
            title = "Coleta",
            message = "Tela de coleta (placeholder).",
            onBack = { screen = AppScreen.PRODUCTION }
        )

        AppScreen.TAVERN -> PlaceholderScreen(
            title = "Taverna",
            message = "Tela da taverna (placeholder).",
            onBack = { screen = AppScreen.CITY }
        )

        AppScreen.SHOP -> PlaceholderScreen(
            title = "Loja",
            message = "Tela da loja (placeholder).",
            onBack = { screen = AppScreen.CITY }
        )

        AppScreen.MAP_OPEN -> PlaceholderScreen(
            title = "Mapa Aberto",
            message = "Mapa aberto em desenvolvimento.",
            onBack = { screen = AppScreen.EXPLORATION }
        )

        AppScreen.DUNGEON -> PlaceholderScreen(
            title = "Dungeon",
            message = "Entrada de dungeon (sem combate nesta fase).",
            onBack = { screen = AppScreen.EXPLORATION }
        )

        AppScreen.EXIT -> PlaceholderScreen(
            title = "Sessao Encerrada",
            message = "Aplicativo encerrado.",
            backLabel = "VOLTAR AO MENU",
            onBack = { screen = AppScreen.MAIN_MENU }
        )
    }
}
