package rpg.classquest

internal class ClassQuestDungeonCatalog(
    private val pathNameResolver: (ClassQuestUnlockType, String) -> String
) {
    fun dungeonDefinition(
        unlockType: ClassQuestUnlockType,
        pathId: String,
        classId: String
    ): ClassQuestDungeonDefinition? {
        val path = pathId.lowercase()
        val baseClass = classId.lowercase()
        val canonicalPath = ClassQuestCatalogSupport.canonicalDungeonPathId(unlockType, baseClass, path) ?: return null
        val definition = when (baseClass) {
            "archer" -> ClassQuestDungeonCatalogArcher.definitionFor(canonicalPath, pathNameResolver)
            "mage" -> ClassQuestDungeonCatalogMage.definitionFor(canonicalPath, pathNameResolver)
            "swordman" -> ClassQuestDungeonCatalogSwordman.definitionFor(canonicalPath, pathNameResolver)
            else -> null
        } ?: return null
        return definition.copy(
            unlockType = unlockType,
            classId = baseClass,
            pathId = path,
            pathName = pathNameResolver(unlockType, path)
        )
    }
}

internal fun dungeonMonster(
    id: String,
    name: String,
    baseArchetypeId: String,
    baseType: String = "",
    family: String = "",
    lootProfileId: String = "",
    tags: Set<String> = emptySet()
): ClassQuestDungeonMonster {
    val inferredBaseType = baseType.trim().ifBlank {
        name.substringBefore(' ').ifBlank { baseArchetypeId.substringBefore('_') }
    }
    val normalizedBaseType = inferredBaseType.trim().lowercase().ifBlank { "monster" }
    val normalizedFamily = family.trim().lowercase().ifBlank {
        ClassQuestCatalogSupport.defaultFamilyForBaseType(normalizedBaseType)
    }
    val normalizedLootProfile = lootProfileId.trim().lowercase().ifBlank {
        ClassQuestCatalogSupport.defaultLootProfileFor(normalizedBaseType, normalizedFamily)
    }
    return ClassQuestDungeonMonster(
        monsterId = id.lowercase(),
        displayName = name,
        baseArchetypeId = baseArchetypeId.lowercase(),
        baseType = normalizedBaseType,
        family = normalizedFamily,
        lootProfileId = normalizedLootProfile,
        identityTags = tags.map { it.lowercase() }.toSet()
    )
}
