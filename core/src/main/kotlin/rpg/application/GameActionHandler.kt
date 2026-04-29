package rpg.application

import rpg.application.actions.GameAction
import rpg.application.creation.CharacterCreationQueryService
import rpg.application.character.CharacterQueryService
import rpg.application.city.CityQueryService
import rpg.application.inventory.InventoryQueryService
import rpg.application.production.ProductionQueryService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.application.shop.ShopQueryService
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.PlayerState

class GameActionHandler(
    repo: DataRepository,
    saveGateway: SaveGameGateway = SaveGameGateway()
) {
    private val runtime = GameActionRuntime(repo, saveGateway)

    fun engine(): GameEngine = runtime.engine
    fun creationQueryService(): CharacterCreationQueryService = runtime.characterCreationQueryService
    fun characterQueryService(): CharacterQueryService = runtime.characterQueryService
    fun inventoryQueryService(): InventoryQueryService = runtime.inventoryQueryService
    fun questQueryService(): QuestQueryService = runtime.questQueryService
    fun achievementQueryService(): AchievementQueryService = runtime.achievementQueryService
    fun cityQueryService(): CityQueryService = runtime.cityQueryService
    fun productionQueryService(): ProductionQueryService = runtime.productionQueryService
    fun shopQueryService(): ShopQueryService = runtime.shopQueryService

    fun handle(session: GameSession, action: GameAction): GameActionResult {
        return runtime.handle(session, action)
            ?: when (action) {
                GameAction.Attack,
                GameAction.EscapeCombat -> GameActionResult(session)
                else -> GameActionResult(session.copy(messages = listOf("Acao ainda nao suportada no fluxo modular.")))
            }
    }

    fun applyCombatResult(session: GameSession, result: CombatFlowResult): GameSession {
        return session.copy(
            gameState = runtime.normalizeForCombat(result.gameState),
            navigation = result.navigation,
            pendingEncounter = null,
            messages = result.messages
        )
    }

    fun applyBattleResolvedAchievement(
        player: PlayerState,
        telemetry: CombatTelemetry,
        victory: Boolean,
        escaped: Boolean,
        isBoss: Boolean,
        monsterTypeId: String,
        monsterStars: Int
    ): PlayerState {
        return runtime.applyBattleResolvedAchievement(
            player = player,
            telemetry = telemetry,
            victory = victory,
            escaped = escaped,
            isBoss = isBoss,
            monsterTypeId = monsterTypeId,
            monsterStars = monsterStars
        )
    }

    fun applyGoldEarnedAchievement(player: PlayerState, gold: Long): PlayerState {
        return runtime.applyGoldEarnedAchievement(player, gold)
    }

    fun applyDeathAchievement(player: PlayerState): PlayerState {
        return runtime.applyDeathAchievement(player)
    }
}
