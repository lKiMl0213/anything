# Pattern: Procedural Quest Context Filtering

## Description
Procedural quest generation first builds a context from what the player can actually access, then filters quest templates against that context before generating a quest instance.

## When to Use
- When adding a new procedural quest type
- When extending board generation with new player capabilities
- When a generator must avoid creating impossible objectives

## Pattern
Build a capability snapshot from player progression and loaded content, then validate each template against level, system availability, and reachable targets before generation.

## Example
```kotlin
fun buildContext(player: PlayerState): QuestGenerationContext {
    val unlockedTiers = repo.mapTiers.values
        .filter { player.level >= it.minLevel }
        .mapTo(mutableSetOf()) { it.id }
    val allowedMonsterIds = repo.mapTiers.values
        .filter { it.id in unlockedTiers }
        .flatMap { it.allowedMonsterTemplates }
        .toMutableSet()
    ...
    return QuestGenerationContext(
        player = player,
        unlockedTierIds = unlockedTiers,
        accessibleMonsterIds = allowedMonsterIds,
        accessibleMonsterTags = accessibleTags,
        availableItemIds = availableItemIds,
        craftableRecipes = craftableRecipes,
        gatherableNodes = gatherableNodes,
        craftingEnabled = repo.craftRecipes.isNotEmpty(),
        gatheringEnabled = repo.gatherNodes.isNotEmpty(),
        dungeonEnabled = repo.mapTiers.isNotEmpty()
    )
}
```

```kotlin
private fun isTemplateAllowed(template: QuestTemplateDef, context: QuestGenerationContext): Boolean {
    val constraints = template.constraints
    val level = context.player.level
    if (level < constraints.minPlayerLevel || level > constraints.maxPlayerLevel) return false
    if (constraints.requiresCrafting && !context.craftingEnabled) return false
    if (constraints.requiresGathering && !context.gatheringEnabled) return false
    ...
}
```

## Files Using This Pattern
- [QuestGenerator.kt](../../../src/main/kotlin/rpg/quest/QuestGenerator.kt) - Builds the generation context and filters templates
- [QuestBoardEngine.kt](../../../src/main/kotlin/rpg/quest/QuestBoardEngine.kt) - Uses generated instances in the board flow
- [data/quest_templates/read.me](../../../data/quest_templates/read.me) - Documents the template format consumed by the generator

## Related
- [Decision: Procedural Quest Generation](../../decisions/006-procedural-quest-generation.md)
- [Feature: Procedural Quest Board](../../intent/feature-procedural-quest-board.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
