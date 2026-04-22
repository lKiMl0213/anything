# Pattern: Combat Snapshot Controller Loop

## Description
Combat exposes a structured snapshot of the current fight and lets a controller react to decision windows instead of directly mutating battle internals from the UI.

## When to Use
- When adding new combat-facing UI behavior
- When extending battle decision handling
- When a system needs read-only combat state before choosing an action

## Pattern
Represent the battle state as a snapshot object, expose a controller interface, and let the combat engine poll actions while advancing the internal simulation.

## Example
```kotlin
data class CombatSnapshot(
    val player: PlayerState,
    val playerStats: ComputedStats,
    val monster: MonsterInstance,
    val monsterStats: ComputedStats,
    val monsterHp: Double,
    val itemInstances: Map<String, ItemInstance>,
    val playerRuntime: CombatRuntimeState,
    val monsterRuntime: CombatRuntimeState,
    val playerFillRate: Double,
    val monsterFillRate: Double,
    val pausedForDecision: Boolean
)

interface PlayerCombatController {
    fun onFrame(snapshot: CombatSnapshot) {}
    fun onDecisionStarted(snapshot: CombatSnapshot) {}
    fun onDecisionEnded() {}
    fun pollAction(snapshot: CombatSnapshot): CombatAction?
}
```

```kotlin
controller.onFrame(snapshot(paused = false))
while (playerActor.currentHp > 0.0 && monsterActor.currentHp > 0.0) {
    val pausedAtLoopStart = playerDecisionOpen && playerActor.runtime.state == CombatState.READY
    if (!pausedAtLoopStart) {
        advanceTick(...)
    }
}
```

## Files Using This Pattern
- [CombatEngine.kt](../../../src/main/kotlin/rpg/combat/CombatEngine.kt) - Defines snapshots, controller hooks, and the battle loop
- [GameCli.kt](../../../src/main/kotlin/rpg/cli/GameCli.kt) - Acts as the player-facing controller during combat

## Related
- [Decision: CLI Orchestrator Architecture](../../decisions/003-cli-orchestrator-architecture.md)
- [Feature: Combat And Status Effects](../../intent/feature-combat-status.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
