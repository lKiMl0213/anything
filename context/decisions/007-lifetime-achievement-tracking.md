# Decision: Lifetime Achievement Tracking

## Context
The game tracks long-term play data beyond a single battle or run. The implementation stores cumulative counters, synchronizes progress, and exposes tier unlocks separately from the core combat and dungeon loop.

## Decision
Track achievements through lifetime counters stored in player state and synchronize them through a dedicated achievement service and tracker.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- Counter-based tracking supports many achievement types without custom state per action
- Synchronization keeps achievement progress aligned with the latest player data
- A dedicated tracker allows gameplay systems to report events without owning reward logic
- Lifetime stats create meta-progression that persists across sessions

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- Single-purpose boolean flags per achievement
- Award logic scattered across every gameplay system
- External analytics-style storage instead of save-bound progression

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Achievement Tracking](../intent/feature-achievement-tracking.md)
- [Decision: CLI Orchestrator Architecture](003-cli-orchestrator-architecture.md)
- [Pattern: Engine Service Composition](../knowledge/patterns/engine-service-composition.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
