# Decision: Selective Context Memory

## Context
The repository now has Context Mesh documentation, but most day-to-day work in this project still depends on reading Kotlin and JSON source files directly. Loading the whole `context/` tree on every task wastes tokens, slows down narrow debugging/refactor work, and risks treating documentation as more authoritative than the code.

At the same time, some information is worth preserving across sessions:
- durable technical choices
- reusable implementation patterns
- important milestone summaries
- short partial history needed after context compaction or handoff

## Decision
Use `context/` as **selective memory**, not as mandatory preload.

Rules:
- Load only the smallest relevant subset of `context/`
- Prefer source code and data files first for local/narrow tasks
- Before compacting the IDE/context window, persist only durable and high-signal information into the most specific file under `context/`
- Use `context/evolution/working-memory.md` only for concise partial history that does not fit better elsewhere

## Rationale
This keeps the benefits of Context Mesh without paying unnecessary token cost.

Rationale inferred from the current codebase and workflow:
- the codebase is already structured enough that many answers are cheaper to recover from source
- only a subset of work is cross-session or architectural
- concise checkpointing preserves continuity without turning `context/` into a second copy of the code

## Alternatives Considered
- Bulk-load the entire `context/` tree on every task
  - Rejected because it wastes tokens and adds low-signal context for narrow tasks
- Avoid Context Mesh entirely
  - Rejected because durable decisions, patterns, and handoff notes still provide real value
- Keep all handoff knowledge only in transient chat history
  - Rejected because it is lost on compaction/resume

## Outcomes
Outcomes to be documented as project evolves.

## Related
- [Project Intent](../intent/project-intent.md)
- [Pattern: Selective Context Checkpointing](../knowledge/patterns/selective-context-checkpointing.md)

## Status
- **Created**: 2026-04-18 (Phase: Learn)
- **Status**: Accepted
- **Note**: Added after observing that Context Mesh is useful in this repository only when used as selective memory.
