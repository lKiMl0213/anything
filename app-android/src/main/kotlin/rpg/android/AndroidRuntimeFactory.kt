package rpg.android

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rpg.android.application.CharacterCommandBridge
import rpg.android.platform.AndroidDataBootstrap
import rpg.application.GameActionHandler
import rpg.application.SaveGameGateway
import rpg.application.character.CharacterCommandService
import rpg.application.character.CharacterQueryService
import rpg.application.character.CharacterRulesSupport
import rpg.application.creation.CharacterCreationQueryService
import rpg.application.inventory.InventoryQueryService
import rpg.io.DataRepository
import rpg.presentation.GamePresenter

internal data class RaceClassSelectionDraft(val raceId: String?, val classId: String?)
internal enum class AttributeContext { CREATION, CHARACTER }

internal data class RuntimeDeps(
    val actionHandler: GameActionHandler,
    val presenter: GamePresenter,
    val creationQueryService: CharacterCreationQueryService,
    val characterQueryService: CharacterQueryService,
    val inventoryQueryService: InventoryQueryService,
    val characterCommandBridge: CharacterCommandBridge,
    val saveGateway: SaveGameGateway
)

internal suspend fun createAndroidRuntimeDeps(application: Application): RuntimeDeps {
    return withContext(Dispatchers.IO) {
        val paths = AndroidDataBootstrap.prepare(application)
        val repo = DataRepository(paths.dataRoot)
        val saveGateway = SaveGameGateway(paths.savesRoot)
        val actionHandler = GameActionHandler(repo = repo, saveGateway = saveGateway)
        val presenter = GamePresenter(
            engine = actionHandler.engine(),
            creationQueryService = actionHandler.creationQueryService(),
            inventoryQueryService = actionHandler.inventoryQueryService(),
            characterQueryService = actionHandler.characterQueryService(),
            questQueryService = actionHandler.questQueryService(),
            achievementQueryService = actionHandler.achievementQueryService(),
            cityQueryService = actionHandler.cityQueryService(),
            productionQueryService = actionHandler.productionQueryService(),
            huntingQueryService = actionHandler.huntingQueryService(),
            enchantQueryService = actionHandler.enchantQueryService(),
            fusionQueryService = actionHandler.fusionQueryService(),
            extractionQueryService = actionHandler.extractionQueryService(),
            shopQueryService = actionHandler.shopQueryService(),
            globalBossQueryService = actionHandler.globalBossQueryService()
        )
        val characterSupport = CharacterRulesSupport(repo, actionHandler.engine())
        RuntimeDeps(
            actionHandler = actionHandler,
            presenter = presenter,
            creationQueryService = actionHandler.creationQueryService(),
            characterQueryService = actionHandler.characterQueryService(),
            inventoryQueryService = actionHandler.inventoryQueryService(),
            characterCommandBridge = CharacterCommandBridge(CharacterCommandService(characterSupport)),
            saveGateway = saveGateway
        )
    }
}
