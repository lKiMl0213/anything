# Feature: Achievement Tracking

## What
Achievement tracking records long-term milestones such as combat outcomes, gold flow, unlocks, and other lifetime actions, then exposes progress and rewards through an achievement view.

## Why
This feature adds meta-progression and recognition for sustained play. It helps players see long-term accomplishments even when individual runs or sessions are short.

## Acceptance Criteria
- [ ] Lifetime actions can increase tracked achievement progress
- [ ] Achievement progress can be synchronized with the current player state
- [ ] Tier unlocks can be surfaced back to the player
- [ ] Achievement progress survives normal save/load usage

## Related
- [Project Intent](project-intent.md)
- [Decision: CLI Orchestrator Architecture](../decisions/003-cli-orchestrator-architecture.md)
- [Decision: Lifetime Achievement Tracking](../decisions/007-lifetime-achievement-tracking.md)
- [Pattern: Engine Service Composition](../knowledge/patterns/engine-service-composition.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Active (already implemented)
