# Decision: Data-Driven Content Architecture

## Context
The game contains many gameplay domains that change frequently: classes, subclasses, specializations, talent trees, monsters, maps, biomes, drops, quests, shops, crafting recipes, and item templates. Hardcoding all of this in Kotlin would make content growth expensive.

## Decision
Store gameplay content as JSON definitions under `data/` and load them into typed registries at startup.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- Content can be expanded without rewriting core systems
- The engine can validate and reuse the same model structure across modules
- Designers or maintainers can change data tables, trees, and templates without touching the main runtime flow
- Multiple subsystems can share the same content-loading approach

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- Hardcoded gameplay content in Kotlin classes
- Mixed code-plus-data definitions with more system-specific formats
- Database-backed content storage

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Dungeon Runs](../intent/feature-dungeon-runs.md)
- [Feature: Class Progression](../intent/feature-class-progression.md)
- [Feature: Items, Loot, And Economy](../intent/feature-items-loot-economy.md)
- [Feature: Crafting And Gathering](../intent/feature-crafting-gathering.md)
- [Feature: Procedural Quest Board](../intent/feature-procedural-quest-board.md)
- [Decision: Class Progression Model](004-class-progression-model.md)
- [Decision: Randomized Item And Drop Model](005-randomized-item-and-drop-model.md)
- [Decision: Procedural Quest Generation](006-procedural-quest-generation.md)
- [Pattern: Data Repository Registry Loading](../knowledge/patterns/data-repository-registry-loading.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
