# Decision: Randomized Item And Drop Model

## Context
The project contains both fixed items and procedural item templates, class-restricted equipment, affixes, rarity tiers, drop tables, and value scaling. The implementation needs to reward replayability without allowing class identity to blur.

## Decision
Use a mixed item model:
- Fixed items for stable rewards or non-procedural content
- Procedural templates for generated equipment
- Affix pools and rarity rules for randomized item instances
- Monster-profile drop tables to control where loot comes from

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- Fixed items are useful for guaranteed rewards and curated progression beats
- Procedural templates add replayable variance to drops
- Affix and rarity rules keep the randomness structured instead of chaotic
- Monster-profile drop tables preserve thematic loot sources
- Tag-based equipment compatibility prevents cross-line class identity mistakes

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- Fully fixed loot only
- Fully procedural loot without template boundaries
- Generic equipment tables with weaker class identity constraints

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Items, Loot, And Economy](../intent/feature-items-loot-economy.md)
- [Feature: Crafting And Gathering](../intent/feature-crafting-gathering.md)
- [Decision: Data-Driven Content Architecture](002-data-driven-content.md)
- [Pattern: Inventory Capacity And Quiver Routing](../knowledge/patterns/inventory-capacity-and-quiver-routing.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
