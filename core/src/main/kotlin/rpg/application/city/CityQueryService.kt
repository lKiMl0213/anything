package rpg.application.city

import rpg.model.GameState

class CityQueryService(
    private val support: CityRulesSupport
) {
    fun tavern(state: GameState): TavernViewData = support.tavernView(state)
}
