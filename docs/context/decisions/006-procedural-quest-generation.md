# Decision: Procedural Quest Generation

## Context
The game supports recurring quests across combat, gathering, crafting, collection, and dungeon progress. A fully handcrafted quest board would scale poorly as content breadth grows.

## Decision
Generate board quests from reusable quest templates filtered against the player’s current accessible content and progression state.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- Templates allow broad quest variety without authoring every instance by hand
- Context filtering reduces the chance of generating impossible or irrelevant quests
- Reward profiles can scale by tier and effort while keeping generation rules reusable
- The board can stay populated even as available systems expand

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- Only fixed handcrafted quest lists
- Separate ad hoc generators for each objective type
- Unfiltered random generation that ignores player access

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Procedural Quest Board](../intent/feature-procedural-quest-board.md)
- [Decision: Data-Driven Content Architecture](002-data-driven-content.md)
- [Pattern: Procedural Quest Context Filtering](../knowledge/patterns/procedural-quest-context-filtering.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
