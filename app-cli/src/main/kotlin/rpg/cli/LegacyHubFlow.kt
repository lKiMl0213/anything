package rpg.cli

import rpg.model.GameState
import rpg.model.PlayerState
import rpg.quest.QuestBoardState

internal class LegacyHubFlow(
    private val readMenuChoice: (prompt: String, min: Int, max: Int) -> Int?,
    private val synchronizeClock: (GameState) -> GameState,
    private val normalizeLoadedState: (GameState) -> GameState,
    private val synchronizeClassQuest: (GameState) -> GameState,
    private val synchronizeAchievements: (GameState) -> GameState,
    private val synchronizeQuestBoard: (GameState) -> GameState,
    private val checkSubclassUnlock: (GameState) -> GameState,
    private val checkSpecializationUnlock: (GameState) -> GameState,
    private val showClock: (GameState) -> Unit,
    private val showStatus: (PlayerState, Map<String, rpg.model.ItemInstance>) -> Unit,
    private val showDebuff: (PlayerState) -> Unit,
    private val menuAlert: (Boolean) -> String,
    private val labelWithAlert: (String, String) -> String,
    private val hasUnspentAttributePoints: (PlayerState) -> Boolean,
    private val hasTalentPointsAvailable: (PlayerState) -> Boolean,
    private val hasReadyToClaim: (QuestBoardState) -> Boolean,
    private val hasAchievementRewardReady: (PlayerState) -> Boolean,
    private val openExploreMenu: (GameState) -> GameState,
    private val openProductionMenu: (GameState) -> GameState,
    private val openCityMenu: (GameState) -> GameState,
    private val saveGame: (GameState) -> Unit,
    private val openEquipped: (GameState) -> GameState,
    private val openInventory: (GameState) -> GameState,
    private val allocateUnspentPoints: (PlayerState, Map<String, rpg.model.ItemInstance>) -> PlayerState,
    private val openTalents: (GameState) -> GameState,
    private val openQuestBoard: (GameState) -> GameState,
    private val openAchievementMenu: (GameState) -> GameState
) {
    fun hubLoop(initialState: GameState): GameState {
        var state = synchronizeClock(normalizeLoadedState(initialState))
        while (true) {
            state = synchronizeClock(state)
            state = normalizeLoadedState(state)
            state = synchronizeClassQuest(state)
            state = synchronizeAchievements(state)
            println("\n=== Menu Principal ===")
            state = checkSubclassUnlock(state)
            state = checkSpecializationUnlock(state)
            state = synchronizeAchievements(state)
            state = synchronizeQuestBoard(state)
            showClock(state)
            showStatus(state.player, state.itemInstances)
            showDebuff(state.player)

            val characterAlert = menuAlert(
                hasUnspentAttributePoints(state.player) || hasTalentPointsAvailable(state.player)
            )
            val progressionAlert = menuAlert(
                hasReadyToClaim(state.questBoard) || hasAchievementRewardReady(state.player)
            )

            println("\n1. Explorar")
            println(labelWithAlert("2. Personagem", characterAlert))
            println("3. Producao")
            println(labelWithAlert("4. Progressao", progressionAlert))
            println("5. Cidade")
            println("6. Salvar")
            println("x. Sair para o menu")

            when (readMenuChoice("Escolha: ", 1, 6)) {
                1 -> state = openExploreMenu(state)
                2 -> state = openCharacterMenu(state)
                3 -> state = openProductionMenu(state)
                4 -> state = openProgressionMenu(state)
                5 -> state = openCityMenu(state)
                6 -> saveGame(state)
                null -> return state
            }
        }
    }

    fun openCharacterMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            val attributeAlert = menuAlert(hasUnspentAttributePoints(updated.player))
            val talentAlert = menuAlert(hasTalentPointsAvailable(updated.player))
            println("\n=== Personagem ===")
            println("1. Equipados")
            println("2. Inventario")
            println(labelWithAlert("3. Atributos", attributeAlert))
            println(labelWithAlert("4. Talentos", talentAlert))
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 4)) {
                1 -> updated = openEquipped(updated)
                2 -> updated = openInventory(updated)
                3 -> updated = updated.copy(
                    player = allocateUnspentPoints(updated.player, updated.itemInstances)
                )
                4 -> updated = openTalents(updated)
                null -> return updated
            }
        }
    }

    fun openProgressionMenu(state: GameState): GameState {
        var updated = state
        while (true) {
            updated = synchronizeClock(updated)
            updated = synchronizeClassQuest(updated)
            updated = synchronizeAchievements(updated)
            updated = synchronizeQuestBoard(updated)

            val questsAlert = if (hasReadyToClaim(updated.questBoard)) menuAlert(true) else ""
            val achievementsAlert = if (hasAchievementRewardReady(updated.player)) menuAlert(true) else ""

            println("\n=== Progressao ===")
            println(labelWithAlert("1. Quests", questsAlert))
            println(labelWithAlert("2. Conquistas", achievementsAlert))
            println("x. Voltar")

            when (readMenuChoice("Escolha: ", 1, 2)) {
                1 -> updated = openQuestBoard(updated)
                2 -> updated = openAchievementMenu(updated)
                null -> return updated
            }
        }
    }
}

