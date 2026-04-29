package rpg.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import rpg.model.ClassDef
import rpg.model.GameBalanceDef
import rpg.model.ItemDef
import rpg.model.ItemTemplateDef
import rpg.model.MapDef
import rpg.model.MapTierDef
import rpg.model.MonsterArchetypeDef
import rpg.model.MonsterTypeDefinition
import rpg.model.RaceDef
import rpg.model.SubclassDef
import rpg.model.SpecializationDef
import rpg.model.TalentTree
import rpg.model.DropTableDef
import rpg.model.BiomeDef
import rpg.model.AffixDef
import rpg.model.MonsterBehaviorDef
import rpg.model.TextPoolDef
import rpg.model.CharacterDef
import rpg.model.PermanentUpgradeDef
import rpg.model.CraftRecipeDef
import rpg.model.GatherNodeDef
import rpg.model.QuestTemplateDef
import rpg.model.ShopEntryDef
import rpg.model.CashPackDef
import rpg.model.SkillDef
import rpg.model.DungeonEventDef
import rpg.monster.MonsterModifier

class DataRepository(private val root: Path) {
    val classes: Map<String, ClassDef> = loadDir<ClassDef>("classes").associateBy { it.id }
    val subclasses: Map<String, SubclassDef> = loadDir<SubclassDef>("subclasses").associateBy { it.id }
    val specializations: Map<String, SpecializationDef> = loadDir<SpecializationDef>("specializations").associateBy { it.id }
    val races: Map<String, RaceDef> = loadDir<RaceDef>("races").associateBy { it.id }
    val monsterTypes: Map<String, MonsterTypeDefinition> = loadDir<MonsterTypeDefinition>("monster_types").associateBy { it.id }
    val monsterArchetypes: Map<String, MonsterArchetypeDef> = loadDir<MonsterArchetypeDef>("monster_archetypes").associateBy { it.id }
    val monsterModifiers: Map<String, MonsterModifier> = loadDir<MonsterModifier>("monster_modifiers").associateBy { it.id }
    val monsterBehaviors: Map<String, MonsterBehaviorDef> = loadDir<MonsterBehaviorDef>("monster_behaviors").associateBy { it.tag }
    val items: Map<String, ItemDef> = loadDir<ItemDef>("items").associateBy { it.id }
    val itemTemplates: Map<String, ItemTemplateDef> = loadDir<ItemTemplateDef>("item_templates").associateBy { it.id }
    val permanentUpgrades: Map<String, PermanentUpgradeDef> = loadDir<PermanentUpgradeDef>("upgrades").associateBy { it.id.lowercase() }
    val affixes: Map<String, AffixDef> = loadDir<AffixDef>("affixes").associateBy { it.id }
    val dropTables: Map<String, DropTableDef> = loadDir<DropTableDef>("drop_tables").associateBy { it.id }
    val questTemplates: Map<String, QuestTemplateDef> = loadDir<QuestTemplateDef>("quest_templates").associateBy { it.id }
    val craftRecipes: Map<String, CraftRecipeDef> = loadDir<CraftRecipeDef>("crafting").associateBy { it.id }
    val gatherNodes: Map<String, GatherNodeDef> = loadDir<GatherNodeDef>("gathering").associateBy { it.id }
    val skills: Map<String, SkillDef> = loadDir<SkillDef>("skills").associateBy { it.id.name }
    val shopEntries: Map<String, ShopEntryDef> = loadDir<ShopEntryDef>("shop").associateBy { it.id }
    val cashPacks: Map<String, CashPackDef> = loadDir<CashPackDef>("cash_packs").associateBy { it.id }
    val dungeonEvents: DungeonEventDef = loadFile("events/dungeon_events.json") ?: DungeonEventDef()
    val maps: Map<String, MapDef> = loadDir<MapDef>("maps").associateBy { it.id }
    val mapTiers: Map<String, MapTierDef> = loadDir<MapTierDef>("map_tiers").associateBy { it.id }
    val biomes: Map<String, BiomeDef> = loadDir<BiomeDef>("biomes").associateBy { it.id }
    val talentTreesV2: Map<String, TalentTree> = loadDir<TalentTree>("talent_trees").associateBy { it.id }
    val textPools: Map<String, TextPoolDef> = loadDir<TextPoolDef>("text_pools").associateBy { it.id }
    val character: CharacterDef = loadFile("character/character_base.json") ?: CharacterDef()
    val balance: GameBalanceDef = loadFile("balance.json") ?: GameBalanceDef()

    fun mapById(id: String): MapDef = maps[id] ?: error("Mapa nao encontrado: $id")
    fun roomById(map: MapDef, id: String) = map.rooms.firstOrNull { it.id == id }
        ?: error("Sala nao encontrada: $id")

    private inline fun <reified T> loadDir(dirName: String): List<T> {
        val dir = root.resolve(dirName)
        if (!Files.exists(dir)) {
            return emptyList()
        }

        Files.walk(dir).use { stream ->
            val results = mutableListOf<T>()
            stream
                .filter { it.isRegularFile() }
                .filter { it.name.lowercase().endsWith(".json") }
                .forEach { results.add(JsonStore.load<T>(it)) }
            return results
        }
    }

    private inline fun <reified T> loadFile(fileName: String): T? {
        val file = root.resolve(fileName)
        if (!Files.exists(file) || !file.isRegularFile()) {
            return null
        }
        return JsonStore.load(file)
    }
}
