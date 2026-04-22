# Decision: CLI Orchestrator Architecture

## Context
The game needs a playable interface, persistent game state, and coordination across many systems such as combat, progression, quests, items, achievements, and events. The repository structure shows many focused services rather than one monolithic gameplay file.

## Decision
Use a CLI-first architecture where `GameCli` owns user interaction and `GameEngine` composes specialized engines and services for gameplay systems.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- A CLI interface keeps the project lightweight and directly playable from the terminal
- A central orchestrator can compose subsystems without pushing all logic into the menu layer
- Focused services make it easier to extend one gameplay domain without rewriting the whole runtime
- The same `GameState` model can be saved and reloaded cleanly through JSON

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- A monolithic CLI class containing all game rules
- A GUI-first or web-first client
- Tighter coupling between menus and subsystem logic

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Dungeon Runs](../intent/feature-dungeon-runs.md)
- [Feature: Combat And Status Effects](../intent/feature-combat-status.md)
- [Feature: Achievement Tracking](../intent/feature-achievement-tracking.md)
- [Pattern: Engine Service Composition](../knowledge/patterns/engine-service-composition.md)
- [Pattern: Combat Snapshot Controller Loop](../knowledge/patterns/combat-snapshot-controller-loop.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
