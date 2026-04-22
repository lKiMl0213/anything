# Decision: Class Progression Model

## Context
The project supports multiple character lines, build choices, unlock milestones, and class-themed progression rewards. The implementation shows a strict hierarchy between base class, second class, and specialization, plus data-defined talent trees and unlock quests.

## Decision
Use a three-stage class model:
- Base class
- Second class
- Specialization

Keep the base talent tree tied to the base class, gate later stages through class quests, and define talent progression through data-driven talent trees with prerequisites.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- The hierarchy keeps class identity readable and prevents invalid progression branches
- Unlock quests turn progression milestones into explicit gameplay moments
- Data-defined requirements make talent and class rules easier to expand than hardcoded branch logic
- Keeping the base tree attached to the original class preserves lineage identity after evolution

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- A flat class list with no lineage
- Hardcoded unlock trees in menu logic
- Separate unrelated talent systems for each stage instead of lineage-based progression

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Class Progression](../intent/feature-class-progression.md)
- [Feature: Class Unlock Quests](../intent/feature-class-quests.md)
- [Decision: Data-Driven Content Architecture](002-data-driven-content.md)
- [Pattern: Staged Class Quest Flow](../knowledge/patterns/staged-class-quest-flow.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
