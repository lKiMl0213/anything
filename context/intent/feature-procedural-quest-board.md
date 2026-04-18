# Feature: Procedural Quest Board

## What
The procedural quest board generates repeatable quests based on the player’s current access to monsters, items, professions, and dungeon content. It provides rotating objectives without requiring every quest to be hand-authored.

## Why
This feature increases content longevity and reduces repetition. It keeps the game loop populated with relevant goals that scale with player progression and available systems.

## Acceptance Criteria
- [ ] The game can generate quest batches from reusable templates
- [ ] Generated quests stay within the player’s reachable content and level range
- [ ] Quest objectives can span combat, collection, crafting, gathering, and dungeon progress
- [ ] Quest rewards scale with effort and tier
- [ ] Generated quests can be accepted, progressed, and completed through normal play

## Related
- [Project Intent](project-intent.md)
- [Decision: Data-Driven Content Architecture](../decisions/002-data-driven-content.md)
- [Decision: Procedural Quest Generation](../decisions/006-procedural-quest-generation.md)
- [Pattern: Procedural Quest Context Filtering](../knowledge/patterns/procedural-quest-context-filtering.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Active (already implemented)
