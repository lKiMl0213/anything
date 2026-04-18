# Changelog

## 2026-04-18 - Data Layout Cleanup

### Updated Systems
- Reorganized classes, subclasses, specializations, talent trees, quest templates, drop tables, shop entries, items, and item templates into nested domain-based hierarchies.
- Class-line content now mirrors `base -> second class -> specialization` in the filesystem for easier navigation.
- Gold and cash shop entries are now grouped by responsibility instead of staying flat.
- Fixed class reward weapons and quivers were moved under class-line item folders.

### Maintenance Notes
- `DataRepository` already loads JSON recursively, so future folder cleanups should preserve ids and hierarchy instead of keeping flat directories.
- `.gitignore` now excludes local notes plus temporary context scratch files.

## 2026-04-18 - Itemization Pass

### Updated Systems
- Randomized class armor templates for subclass and specialization sets
- Context-aware equipment drops using biome, tier band, and monster tag weighting
- Class quest rewards now generate rolled gear from templates instead of only fixed armor pieces
- Inventory filters by item type and minimum rarity
- Direct comparison against equipped gear inside inventory item details

### Balance Notes
- Rarity promotion chances on dropped template gear were reduced to keep epic/legendary results meaningful
- New class armor templates were split by line fantasy with curated affix pools and loot tags

## 2026-04-18 - Context Policy Refresh

### Updated Systems
- Context Mesh changed to selective memory instead of mandatory preload
- Added explicit pre-compaction checkpoint rules
- Added concise working memory for partial history that matters later
- Removed legacy static subclass/specialization armor item files after template migration

## Current State - Context Mesh Added

### Existing Features (documented)
- Dungeon runs with boss cycles and room progression
- Combat with skills, status effects, and combat telemetry
- Three-stage class progression with talents
- Class unlock quests with path-based rewards
- Inventory, equipment, shops, and rarity-based loot
- Crafting and gathering profession loops
- Procedural quest generation from reusable templates
- Achievement tracking with lifetime counters

### Tech Stack (documented)
- Kotlin JVM application
- Gradle build and application packaging
- Java 21 toolchain
- kotlinx.serialization JSON

### Patterns Identified
- Data repository registry loading
- Engine service composition
- Combat snapshot controller loop
- Inventory capacity and quiver routing
- Staged class quest flow
- Procedural quest context filtering

---
*Context Mesh added: 2026-04-18*
*This changelog documents the state when Context Mesh was added.*
*Future changes will be tracked below.*
