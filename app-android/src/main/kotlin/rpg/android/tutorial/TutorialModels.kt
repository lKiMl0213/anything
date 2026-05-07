package rpg.android.tutorial

import rpg.android.state.AndroidUiState
import rpg.android.state.MainSection
import rpg.model.GameState
import rpg.navigation.NavigationState

data class TutorialState(
    val completed: Boolean,
    val step: TutorialStep
)

enum class TutorialStep(val persistedValue: Int) {
    HUB_OVERVIEW(1),
    EXPLORE_BUTTON(2),
    EXPLORATION_AREAS(3),
    OPEN_CHARACTER(4),
    CHARACTER_ATTR_TALENTS(5),
    CHARACTER_EQUIPMENT_INVENTORY(6),
    OPEN_PRODUCTION(7),
    PRODUCTION_INFO(8),
    OPEN_CITY(9),
    OPEN_PROGRESSION(10),
    OPEN_SETTINGS(11),
    FINAL(12),
    CITY_INFO(13),
    PROGRESSION_INFO(14);

    companion object {
        fun fromPersisted(value: Int): TutorialStep {
            return entries.firstOrNull { it.persistedValue == value } ?: HUB_OVERVIEW
        }
    }
}

enum class TutorialTarget {
    HUB_INFO_PANEL,
    HUB_EXPLORE_BUTTON,
    EXPLORATION_AREAS_PANEL,
    BACK_BUTTON,
    BOTTOM_NAV_CHARACTER,
    CHARACTER_ACTION_PANEL,
    CHARACTER_EQUIPMENT_PANEL,
    BOTTOM_NAV_PRODUCTION,
    PRODUCTION_PANEL,
    CITY_PANEL,
    BOTTOM_NAV_CITY,
    PROGRESSION_PANEL,
    BOTTOM_NAV_PROGRESSION,
    BOTTOM_NAV_EXPLORE,
    SETTINGS_BUTTON,
    NONE
}

enum class TutorialAction {
    OPEN_EXPLORE,
    OPEN_CHARACTER,
    OPEN_PRODUCTION,
    OPEN_CITY,
    OPEN_PROGRESSION,
    BACK_FROM_EXPLORATION_AREAS,
    OPEN_HUB,
    OPEN_SETTINGS
}

data class TutorialOverlayState(
    val step: TutorialStep,
    val title: String,
    val message: String,
    val target: TutorialTarget,
    val requiresUserAction: Boolean,
    val primaryButtonLabel: String? = null
)

data class TutorialActionDecision(
    val allowed: Boolean,
    val nextStep: TutorialStep? = null
)

internal data class TutorialScreenContext(
    val navigation: NavigationState,
    val isHub: Boolean,
    val isCharacter: Boolean,
    val isProduction: Boolean,
    val isExplorationAreas: Boolean,
    val isCity: Boolean,
    val isProgression: Boolean
) {
    companion object {
        fun fromUiState(
            navigation: NavigationState,
            uiState: AndroidUiState
        ): TutorialScreenContext {
            val generic = uiState as? AndroidUiState.GenericMenu
            val isCharacterScreen = uiState is AndroidUiState.Character ||
                (generic?.section == MainSection.CHARACTER)
            val isProductionScreen = generic?.section == MainSection.PRODUCTION
            val isExplorationAreasScreen = generic?.section == MainSection.EXPLORATION &&
                generic.viewModel.title.contains("areas de exploracao", ignoreCase = true)
            val isCityScreen = generic?.section == MainSection.CITY
            val isProgressionScreen = generic?.section == MainSection.PROGRESSION
            return TutorialScreenContext(
                navigation = navigation,
                isHub = uiState is AndroidUiState.MainHub,
                isCharacter = isCharacterScreen,
                isProduction = isProductionScreen,
                isExplorationAreas = isExplorationAreasScreen,
                isCity = isCityScreen,
                isProgression = isProgressionScreen
            )
        }
    }
}

internal fun GameState.toTutorialState(): TutorialState {
    return TutorialState(
        completed = tutorialCompleted,
        step = TutorialStep.fromPersisted(tutorialStep)
    )
}
