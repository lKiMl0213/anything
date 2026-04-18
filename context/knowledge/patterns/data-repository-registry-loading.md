# Pattern: Data Repository Registry Loading

## Description
The project loads gameplay definitions from JSON folders into typed in-memory registries during startup. This keeps runtime systems decoupled from file parsing details.

## When to Use
- When adding a new content family under `data/`
- When a subsystem needs strongly typed access to JSON-defined records
- When startup-time loading is preferable to ad hoc file reads during gameplay

## Pattern
Define a serializable model, load all JSON files from a directory through `DataRepository`, and expose the result as a keyed map for the rest of the runtime.

## Example
```kotlin
class DataRepository(private val root: Path) {
    val classes: Map<String, ClassDef> = loadDir<ClassDef>("classes").associateBy { it.id }
    val itemTemplates: Map<String, ItemTemplateDef> = loadDir<ItemTemplateDef>("item_templates").associateBy { it.id }
    val dropTables: Map<String, DropTableDef> = loadDir<DropTableDef>("drop_tables").associateBy { it.id }

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
}
```

## Files Using This Pattern
- [DataRepository.kt](../../../src/main/kotlin/rpg/io/DataRepository.kt) - Loads content families from `data/`
- [JsonStore.kt](../../../src/main/kotlin/rpg/io/JsonStore.kt) - Handles JSON serialization and deserialization
- [ItemRegistry.kt](../../../src/main/kotlin/rpg/registry/ItemRegistry.kt) - Wraps loaded item and template maps in registry accessors

## Related
- [Decision: Tech Stack](../../decisions/001-tech-stack.md)
- [Decision: Data-Driven Content Architecture](../../decisions/002-data-driven-content.md)
- [Feature: Class Progression](../../intent/feature-class-progression.md)
- [Feature: Crafting And Gathering](../../intent/feature-crafting-gathering.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
