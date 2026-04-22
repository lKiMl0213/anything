# Pattern: Engine Service Composition

## Description
The runtime composes many focused engines and services into one orchestration layer instead of centralizing every rule in a single class.

## When to Use
- When adding a new gameplay subsystem that needs to be coordinated from the main runtime
- When a feature should stay independently testable or replaceable
- When the CLI should call a composed facade instead of raw subsystem internals

## Pattern
Keep domain logic in focused classes such as `DropEngine`, `CombatEngine`, `QuestRewardService`, or `GatheringService`, then compose them inside `GameEngine`.

## Example
```kotlin
class GameEngine(private val repo: DataRepository, private val rng: Random = Random.Default) {
    val itemRegistry = ItemRegistry(repo.items, repo.itemTemplates)
    val statsEngine = StatsEngine(repo, itemRegistry)
    val classSystem = ClassSystem(repo)
    val dungeonEngine = DungeonEngine(repo, rng)
    val monsterFactory = MonsterFactory(repo, rng)
    val itemEngine = ItemEngine(itemRegistry, repo.affixes, rng)
    val dropEngine = DropEngine(dropTableRegistry, itemRegistry, itemEngine, rng, balance)
    val combatEngine = CombatEngine(
        statsEngine = statsEngine,
        itemResolver = itemResolver,
        itemRegistry = itemRegistry,
        behaviorEngine = behaviorEngine,
        rng = rng,
        balance = balance,
        biomes = repo.biomes,
        archetypes = repo.monsterArchetypes,
        talentTrees = repo.talentTreesV2.values,
        monsterAffinityService = monsterAffinityService
    )
}
```

## Files Using This Pattern
- [GameEngine.kt](../../../src/main/kotlin/rpg/engine/GameEngine.kt) - Central composition root for gameplay systems
- [GameCli.kt](../../../src/main/kotlin/rpg/cli/GameCli.kt) - Uses the composed engine rather than owning gameplay rules directly
- [ClassQuestService.kt](../../../src/main/kotlin/rpg/classquest/ClassQuestService.kt) - Another focused service integrated into the runtime

## Related
- [Decision: Tech Stack](../../decisions/001-tech-stack.md)
- [Decision: CLI Orchestrator Architecture](../../decisions/003-cli-orchestrator-architecture.md)
- [Decision: Lifetime Achievement Tracking](../../decisions/007-lifetime-achievement-tracking.md)
- [Feature: Dungeon Runs](../../intent/feature-dungeon-runs.md)
- [Feature: Achievement Tracking](../../intent/feature-achievement-tracking.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
