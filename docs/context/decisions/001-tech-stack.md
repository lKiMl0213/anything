# Decision: Tech Stack

## Context
The repository is a terminal RPG application with heavy local data loading, typed game models, and portable distribution needs. The build files and source layout show a JVM application rather than a browser or server-first project.

## Decision
Use a Kotlin/JVM application built with Gradle, targeting Java 21, and use `kotlinx.serialization` for JSON model serialization.

## Rationale
Rationale not explicitly documented in the repository, inferred from implementation:
- Kotlin data classes fit the large number of structured gameplay definitions
- JVM packaging supports local execution and Windows-portable distribution
- Gradle provides application packaging and toolchain management
- `kotlinx.serialization` keeps JSON loading strongly typed without adding a heavier persistence stack

## Alternatives Considered
Alternatives not documented in existing codebase. Likely alternatives would have included:
- A scripting-language implementation with looser typing
- A GUI or web-first front end instead of a CLI application
- Manual JSON parsing or another serialization library

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Feature: Dungeon Runs](../intent/feature-dungeon-runs.md)
- [Feature: Combat And Status Effects](../intent/feature-combat-status.md)
- [Feature: Class Progression](../intent/feature-class-progression.md)
- [Feature: Items, Loot, And Economy](../intent/feature-items-loot-economy.md)
- [Pattern: Data Repository Registry Loading](../knowledge/patterns/data-repository-registry-loading.md)
- [Pattern: Engine Service Composition](../knowledge/patterns/engine-service-composition.md)

## Status
- **Created**: 2026-04-18 (Phase: Intent)
- **Status**: Accepted
- **Note**: Documented from existing implementation
