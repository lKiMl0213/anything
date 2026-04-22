# Working Memory

Use this file only for concise, durable partial history that matters after context compaction or handoff and does not fit better in a feature, decision, pattern, or changelog entry.

## Current High-Signal Notes
- Class armor progression now uses randomized templates in `data/item_templates/*_template.json`.
- Static armor files previously stored in `data/items/a_*`, `data/items/m_*`, and `data/items/s_*` were removed and should not be reintroduced for subclass/specialization armor rewards or drops.
- Class quest rewards and contextual loot tables should target armor templates, not fixed armor item ids.
- Context Mesh in this repository should be loaded selectively; code and data remain the primary source of truth for narrow tasks.
- JSON content registries under `data/` are loaded recursively, so future folder cleanups should preserve ids and hierarchy instead of keeping flat directories.
- Class-line filesystem organization now mirrors `base -> second class -> specialization` for classes, specializations, talent trees, class rewards, and class-oriented item templates.
- UI strategy for the next major frontend step is documented in `Relatório.md`: prefer Android-first with Jetpack Compose, keep gameplay semi-ATB, extract reusable core away from `GameCli`, and only evaluate Web after the Android UI is stable.
- `GameCli` is now only an entrypoint; the old full CLI lives in `LegacyGameCli`, and the new modular vertical slice lives across `application`, `navigation`, `presentation`, `cli/input`, and `cli/renderer`.
- The migrated CLI slice currently covers main menu, continue session, load/save, hub, production/progression/city shell menus, exploration, dungeon tier selection, combat attack flow, character menu, inventory, equipped slots, item detail actions, quiver management, attributes, and talent inspection/rank-up; unsupported paths still hand off to targeted legacy submenus to preserve functionality.
- Inventory/equipment rules stay in reusable services under `application/inventory`; presentation only consumes prepared view data and the CLI adapters remain the only place for text rendering and raw input.
- Character attribute/talent rules stay in reusable services under `application/character`; presenter/view models only format already-prepared data and mutations return explicit feedback messages.
- Quest, achievement, and tavern rules now live under `application/progression` and `application/city`; progression no longer needs a legacy handoff, while city still keeps a narrow legacy bridge only for non-tavern services.
- `GameActionHandler` is now a coordinator over smaller action dispatchers (`session`, `navigation`, `character`, `inventory`, `progression`, `city`, `exploration`) and should stay that way instead of absorbing domain logic again.
- Legacy-assisted new game now returns a `GameState` back to the modular flow; full legacy hub handoff was removed in favor of narrower bridges for the remaining unmigrated top-level domains.

## Status
- **Created**: 2026-04-18
- **Status**: Active
- **Note**: Keep entries short to avoid token waste.
