package rpg.application

import rpg.application.actions.GameAction
import rpg.application.creation.CharacterCreationQueryService
import rpg.application.character.CharacterQueryService
import rpg.application.city.CityQueryService
import rpg.application.enchant.EnchantQueryService
import rpg.application.enchant.ExtractionQueryService
import rpg.application.enchant.FusionQueryService
import rpg.application.globalboss.GlobalBossQueryService
import rpg.application.hunting.HuntingQueryService
import rpg.application.inventory.InventoryQueryService
import rpg.application.production.ProductionQueryService
import rpg.application.progression.AchievementQueryService
import rpg.application.progression.QuestQueryService
import rpg.application.session.AutoSavePolicyService
import rpg.application.shop.ShopQueryService
import rpg.combat.CombatTelemetry
import rpg.engine.GameEngine
import rpg.io.DataRepository
import rpg.model.PlayerState

class GameActionHandler(
    repo: DataRepository,
    private val saveGateway: SaveGameGateway = SaveGameGateway()
) {
    private val runtime = GameActionRuntime(repo, saveGateway)
    private val autosavePolicy = AutoSavePolicyService()

    fun engine(): GameEngine = runtime.engine
    fun creationQueryService(): CharacterCreationQueryService = runtime.characterCreationQueryService
    fun characterQueryService(): CharacterQueryService = runtime.characterQueryService
    fun inventoryQueryService(): InventoryQueryService = runtime.inventoryQueryService
    fun questQueryService(): QuestQueryService = runtime.questQueryService
    fun achievementQueryService(): AchievementQueryService = runtime.achievementQueryService
    fun cityQueryService(): CityQueryService = runtime.cityQueryService
    fun productionQueryService(): ProductionQueryService = runtime.productionQueryService
    fun huntingQueryService(): HuntingQueryService = runtime.huntingQueryService
    fun enchantQueryService(): EnchantQueryService = runtime.enchantQueryService
    fun fusionQueryService(): FusionQueryService = runtime.fusionQueryService
    fun extractionQueryService(): ExtractionQueryService = runtime.extractionQueryService
    fun shopQueryService(): ShopQueryService = runtime.shopQueryService
    fun globalBossQueryService(): GlobalBossQueryService = runtime.globalBossQueryService

    fun handle(session: GameSession, action: GameAction): GameActionResult {
        val result = runtime.handle(session, action)
            ?: when (action) {
                GameAction.Attack,
                GameAction.EscapeCombat -> GameActionResult(session)
                else -> GameActionResult(session.copy(messages = listOf("Acao ainda nao suportada no fluxo modular.")))
            }
        val autosavedSession = applyAutosaveIfNeeded(session, action, result.session)
        return if (autosavedSession == result.session) result else result.copy(session = autosavedSession)
    }

    fun applyCombatResult(session: GameSession, result: CombatFlowResult): GameSession {
        val updated = session.copy(
            gameState = runtime.normalizeForCombat(result.gameState),
            navigation = result.navigation,
            pendingEncounter = null,
            messages = result.messages
        )
        return applyAutosaveAfterCombatIfNeeded(session, updated)
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

    fun resolveGlobalBossCombat(
        state: rpg.model.GameState,
        encounter: PendingEncounter,
        combatResult: rpg.combat.CombatResult,
        combatLog: List<String>
    ): CombatFlowResult {
        return runtime.resolveGlobalBossCombat(
            state = state,
            encounter = encounter,
            combatResult = combatResult,
            combatLog = combatLog
        )
    }

    private fun applyAutosaveIfNeeded(
        before: GameSession,
        action: GameAction,
        after: GameSession
    ): GameSession {
        if (!autosavePolicy.shouldAutosave(before, action, after)) return after
        return autosaveSession(after)
    }

    private fun applyAutosaveAfterCombatIfNeeded(
        before: GameSession,
        after: GameSession
    ): GameSession {
        if (!autosavePolicy.shouldAutosaveAfterCombat(before, after)) return after
        return autosaveSession(after)
    }

    private fun autosaveSession(session: GameSession): GameSession {
        val state = session.gameState ?: return session
        val normalized = runtime.normalizeForCombat(state)
        val savedPath = session.currentSavePath?.let { path ->
            saveGateway.save(path, normalized)
        } ?: saveGateway.saveAutosave(normalized)
        return session.copy(
            gameState = normalized,
            currentSavePath = session.currentSavePath ?: savedPath,
            currentSaveName = session.currentSaveName ?: savedPath.fileName.toString()
        )
    }
}
