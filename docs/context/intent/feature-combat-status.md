# Feature: Combat And Status Effects

## What
Combat allows the player to attack, cast skills, use items, and react to enemy behavior while both sides exchange damage and status effects. It supports moment-to-moment tactical play inside battles.

## Why
This feature provides the core challenge of the RPG. It turns character builds, equipment choices, and class abilities into meaningful decisions during encounters instead of only passive stat growth.

## Acceptance Criteria
- [ ] The player can perform basic attacks, skills, item usage, and escape attempts
- [ ] Combat supports skill timing, resource costs, and cooldown behavior
- [ ] Status effects can influence combat outcomes over time
- [ ] Enemy behavior can alter the pace or pressure of a fight
- [ ] Combat results feed telemetry and reward systems after resolution

## Related
- [Project Intent](project-intent.md)
- [Decision: CLI Orchestrator Architecture](../decisions/003-cli-orchestrator-architecture.md)
- [Pattern: Combat Snapshot Controller Loop](../knowledge/patterns/combat-snapshot-controller-loop.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Active (already implemented)
