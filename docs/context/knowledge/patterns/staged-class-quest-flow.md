# Pattern: Staged Class Quest Flow

## Description
Class unlock quests are represented as staged progress objects with path locking, stage snapshots, and synchronized availability rules.

## When to Use
- When adding a new class unlock route
- When extending stage progression or reward timing
- When a system must update progress from combat or collected items

## Pattern
Synchronize quest availability from player state, resolve the active definition, and only allow path choices that belong to the current unlock context.

## Example
```kotlin
fun currentContext(player: PlayerState): ClassQuestContext? {
    val syncedPlayer = synchronize(player)
    val definition = activeDefinition(syncedPlayer) ?: return null
    val progress = progressForDefinition(syncedPlayer, definition)
    if (progress.status == ClassQuestStatus.NOT_AVAILABLE) return null
    if (progress.status == ClassQuestStatus.COMPLETED && progress.chosenPath.isNullOrBlank()) return null
    return ClassQuestContext(definition = definition, progress = progress)
}
```

```kotlin
fun choosePath(
    player: PlayerState,
    itemInstances: Map<String, ItemInstance>,
    pathId: String
): ClassQuestUpdate {
    val syncedPlayer = synchronize(player)
    val context = currentContext(syncedPlayer) ?: return ClassQuestUpdate(...)
    val chosen = pathId.trim().lowercase()
    if (chosen !in context.definition.paths().map { it.lowercase() }.toSet()) {
        return ClassQuestUpdate(...)
    }
    ...
}
```

## Files Using This Pattern
- [ClassQuestService.kt](../../../src/main/kotlin/rpg/classquest/ClassQuestService.kt) - Owns progression, path choice, and staged updates
- [ClassQuestModels.kt](../../../src/main/kotlin/rpg/classquest/ClassQuestModels.kt) - Defines staged reward and progress models
- [ClassQuestMenu.kt](../../../src/main/kotlin/rpg/classquest/ClassQuestMenu.kt) - Renders the active class quest view

## Related
- [Decision: Class Progression Model](../../decisions/004-class-progression-model.md)
- [Feature: Class Unlock Quests](../../intent/feature-class-quests.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
