# Feature: Dungeon Runs

## What
Dungeon runs let the player choose a tier, enter a run, advance through rooms, face escalating danger, and periodically encounter bosses. The run acts as the main repeatable gameplay loop.

## Why
This feature gives the game a structured progression loop with risk, reward, and replay value. It creates a consistent way for players to earn experience, loot, and progression while testing their current build.

## Acceptance Criteria
- [ ] The player can start a run from an available dungeon tier
- [ ] A run advances through room types such as monster, rest, event, and boss encounters
- [ ] Difficulty and depth increase over the course of the run
- [ ] Boss encounters appear at progression milestones
- [ ] Victory rewards feed back into character progression and resource gain

## Related
- [Project Intent](project-intent.md)
- [Decision: Data-Driven Content Architecture](../decisions/002-data-driven-content.md)
- [Decision: CLI Orchestrator Architecture](../decisions/003-cli-orchestrator-architecture.md)
- [Pattern: Engine Service Composition](../knowledge/patterns/engine-service-composition.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Active (already implemented)
